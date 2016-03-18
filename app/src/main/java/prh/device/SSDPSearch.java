package prh.device;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import prh.artisan.Artisan;
import prh.artisan.EventHandler;
import prh.server.SSDPServer;
import prh.utils.Utils;
import prh.utils.httpUtils;


public class SSDPSearch implements Runnable
    // Implements a runnable that performs an aynchronous SSDP Search
    // for interesting devices and services.
    //
    // Devices are uniquely identified by their TYPE and FRIENDLY_NAME.
    // PRH - The unique ID should really be the device UUID
    //

    //
    // As devices are found, it asks Artisan if it should proceed to get
    // to get the device_description, a costly operation, as Artisan maintains
    // a list of existing devices, and if told to do so, then requests the
    // device description in a separate thread, which then parses the , handles and validates all responses, and
    // eventually creates Devices that create Services and that notify
    // and a factory that creates derived Services
    // and notifies Artisan about them.
{
    private static int dbg_ssdp_search = 0;
    private static int LISTEN_PORT = 8070;
    private static int SEARCH_TIME = 4;

    /*
    private static String SSDP_DEVICE_MEDIA_SERVER = "urn:schemas-upnp-org:device:MediaServer:1";
    private static String SSDP_DEVICE_MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1";
    private static String SSDP_DEVICE_OPEN_HOME = "urn:linn-co-uk:device:Source:1";

    private static String SSDP_SERVICE_CONTENT_DIRECTORY = "urn:schemas-upnp-org:service:ContentDirectory:1";
    private static String SSDP_SERVICE_AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1";
    private static String SSDP_SERVICE_RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1";
    private static String SSDP_SERVICE_OPEN_PRODUCT = "urn:av-openhome-org:service:Product:1";
    private static String SSDP_SERVICE_OPEN_PLAYLIST = "urn:av-openhome-org:service:Playlist:1";
    private static String SSDP_SERVICE_OPEN_INFO = "urn:av-openhome-org:service:Info:1";
    private static String SSDP_SERVICE_OPEN_TIME = "urn:av-openhome-org:service:Time:1";
    private static String SSDP_SERVICE_OPEN_VOLUME = "urn:av-openhome-org:service:Volume:1";
    */


    private DeviceManager device_manager;
    private boolean ssdp_search_finished = false;
    private int num_ssdp_devices_started = 0;
    private int num_new_devices = 0;
    private Artisan artisan;


    //------------------------------------------------------
    // constructor and utilities
    //------------------------------------------------------

    public SSDPSearch(DeviceManager dm, Artisan ma)
    {
        device_manager = dm;
        artisan = ma;
    }

    public boolean finished()
    {
        return ssdp_search_finished;
    }


    private static String ShortType(String what, String st)
        // utility to change an SSDP device or service type
        // i.e. urn:schemas-upnp-org:service:AVTransport:1
        // into one of our more simple names (i.e. AVTransport)
        // Can be called over again with no harm.
    {
        st = st.replaceAll("^.*" + what + ":","");
        st = st.replaceAll(":.*$","");
        return st;
    }


    private static String GetUrn(String st)
        // utility to get the urn from a long device type
        // like urn:schemas-upnp-org:device:MediaServer:1
    {
        st = st.replaceAll("^.*urn:","");
        st = st.replaceAll(":device.*$","");
        return st;
    }


    //------------------------------------------------------
    // run() - do the SSDPSearch
    //------------------------------------------------------

    public void run()
    {
        Utils.log(dbg_ssdp_search,0,"SSDPSearch.run() started");

        // create a DeviceListener on LISTEN_PORT

        SSDPSearchListener listener = new SSDPSearchListener(device_manager);
        Thread listener_thread = new Thread(listener);
        listener_thread.start();
        Utils.log(dbg_ssdp_search + 1,1,"SSDPSearch started listener_thread");

        // send out the M_SEARCH message and return to caller.
        // everything else happens in the listener

        try
        {
            String urn = "ssdp:all";  // concession for re-usability

            MulticastSocket sock = new MulticastSocket(null);
            Utils.log(dbg_ssdp_search + 1,1,"SSDPSearch socket created for server_ip(" + Utils.server_ip + ")");
            sock.bind(new InetSocketAddress(Utils.server_ip,LISTEN_PORT));
            Utils.log(dbg_ssdp_search + 1,1,"SSDPSearch socket bound");
            sock.setTimeToLive(SEARCH_TIME);

            String text = "";
            text += "M-SEARCH * HTTP/1.1\r\n";
            text += "HOST: 239.255.255.250:1900\r\n";
            text += "MAN: \"ssdp:discover\"\r\n";
            text += "MX: 3\r\n";
            text += "ST: " + urn + "\r\n";
            text += "\r\n";

            DatagramPacket packet = new DatagramPacket(
                text.getBytes(),
                text.getBytes().length,
                SSDPServer.mMulticastGroupAddress);

            Utils.log(dbg_ssdp_search + 1,1,"SSDPSearch sending packet");
            sock.send(packet);
            Utils.log(dbg_ssdp_search + 1,1,"SSDPSearch packet sent");

            sock.disconnect();
            sock.close();
        }
        catch (Exception e)
        {
            Utils.error("Could not send M_SEARCH message: " + e.toString());
        }

        Utils.log(dbg_ssdp_search + 1,0,"SSDPSearch.run() finished");
    }


    //-----------------------------------------------------------
    // SearchListener
    //-----------------------------------------------------------

    private class SSDPSearchListener implements Runnable
    {
        private DeviceManager device_manager;
        SSDPSearchListener(DeviceManager dm)
        {
            device_manager = dm;
        }

        public void run()
        {
            Utils.log(dbg_ssdp_search+1,0,"SSDPSearchListener::run()");
            MulticastSocket sock = null;

            try
            {
                sock = new MulticastSocket(null);
                Utils.log(dbg_ssdp_search+1,1,"SSDPSearchListener socket created ServerIP=" + (Utils.server_ip == null ? "null" : Utils.server_ip));
                sock.bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"),LISTEN_PORT));
                Utils.log(dbg_ssdp_search+1,1,"SSDPSearchListener socket bound");
                sock.setTimeToLive(SEARCH_TIME);
                sock.setSoTimeout(SEARCH_TIME * 1000);
                Utils.log(dbg_ssdp_search+1,1,"SSDPSearchListener joining group");
                sock.joinGroup(
                    SSDPServer.mMulticastGroupAddress,
                    SSDPServer.mNetIf);
                Utils.log(dbg_ssdp_search+1,1,"SSDPSearchListener group joined");
            }
            catch (Exception e)
            {
                Utils.error("Exception creating listener socket: " + e.toString());
                if (sock != null) sock.close();
                sock = null;
            }


            if (sock != null)
            {
                byte[] buf = new byte[ 2048 ];
                DatagramPacket reply = new DatagramPacket( buf, buf.length );
                boolean running = true;

                while (running)    // until SocketTimeoutException
                {
                    try
                    {
                        Utils.log(1,1,"SSDPSearchListener waiting for reply ...");
                        sock.receive(reply);
                        Utils.log(dbg_ssdp_search+1,1,"REPLY from " + reply.getAddress() + ":" + reply.getPort());
                        String data = new String( reply.getData(), reply.getOffset(), reply.getLength() );
                        Utils.log(dbg_ssdp_search+3,2,"reply data=" + data);

                        // get the device type
                        // and if its a supported type
                        // fire off a deviceDescriptionGetter

                        String location = null;
                        String device_usn = null;
                        // String device_st = null;

                        boolean dbg_first_line = true;
                        String lines[] = data.split("\n");
                        for (String line : lines)
                        {
                            line = line.replaceAll("\\s*$","");
                            if (dbg_first_line)
                            {
                                Utils.log(dbg_ssdp_search + 1,2,"first_line=" + line);
                                dbg_first_line = false;
                            }
                            else
                            {
                                int pos = line.indexOf(":");
                                if (pos > 0)
                                {
                                    String lvalue = line.substring(0,pos).toLowerCase();
                                    String rvalue = line.substring(pos + 1);
                                    rvalue = rvalue.replaceAll("^\\s*","");
                                    Utils.log(dbg_ssdp_search + 1,2,"header(" + lvalue + ")='" + rvalue + "'");

                                    if (lvalue.equalsIgnoreCase("location"))
                                        location = rvalue;
                                    if (lvalue.equalsIgnoreCase("usn"))
                                        device_usn = rvalue;
                                    //if (lvalue.equalsIgnoreCase("st"))
                                    //    device_st = rvalue;
                                }
                            }   // not the first line
                        }   // for each line in the reply

                        // if we got a location and, and it's not ourselves
                        // and we got a device type and it's allowed
                        // start the asynchronous XML request for the service description/
                        //
                        // the check for ourselves is important since we fire off an SSDP
                        // search right after constructing the http server, which may
                        // not have had time to start (so I got errors here anyways
                        // cuz the doc could not be loaded).

                        if (location != null && !location.isEmpty() &&
                            // device_st != null && !device_st.isEmpty() &&
                            device_usn != null && !device_usn.isEmpty() &&
                            !location.contains(Utils.server_ip + ":" + Utils.server_port) &&
                            isValidDeviceUSN(device_usn))
                        {
                            SSDPDevice device = new SSDPDevice(
                                device_manager,
                                location,
                                device_usn );

                            // The devices take place on separate threads
                            // Which leads to a race condition tyring to figure out if
                            // the search is completed by counting number started - number_finished

                            if (device.isValid())
                            {
                                num_ssdp_devices_started++;
                                Utils.log(dbg_ssdp_search,0,"creating SSDPDevice(" + device_usn + ") num_ssdp_devices_started="+num_ssdp_devices_started);
                                Thread device_thread = new Thread(device);
                                device_thread.start();
                            }
                        }
                        else
                        {
                            Utils.warning(dbg_ssdp_search+2,1,"Skipping reply from device(" + reply.getAddress() + ":" + reply.getPort() + ") usn=" + device_usn);
                        }


                    }   // try
                    catch( SocketTimeoutException e )
                    {
                        Utils.log(dbg_ssdp_search+1,1,"Socket timed out: " + e.toString());
                        running = false;
                    }
                    catch( IOException e )
                    {
                        Utils.warning(0,1,"IOException: " + e.toString());
                        e.printStackTrace();
                        running = false;
                    }
                    catch( Exception e )
                    {
                        Utils.error("Exception: " + e.toString());
                        e.printStackTrace();
                        running = false;
                    }

                }   // while running
            }   // if sock != null

            // Since the search runs in it's own thread,
            // it's ok to make it synchronous
            // Here we wait for all children to finish

            while (num_ssdp_devices_started > 0)
            {
                Utils.log(dbg_ssdp_search+3,5,"waiting for SSDPSearch to finish");
                Utils.sleep(100);
            }

            // finished!

            if (DeviceManager.USE_DEVICE_CACHE && num_new_devices > 0)
                device_manager.writeCache();
            ssdp_search_finished = true;
            artisan.handleArtisanEvent(EventHandler.EVENT_SSDP_SEARCH_FINISHED,null);
            Utils.log(dbg_ssdp_search + 1,0,"SSDPSearchListener::run() finished");

        }   // run()



        // check if it's a valid, and interesting device_type
        // the single switch of "Source" to "OpenHomeRenderer"


        // validation

        public boolean isValidDeviceUSN(String usn)
            // Checks USN for uuid, urn, and device:
            // Makes sure device_type is one we want
            // Validates the urn versus the expected_urn
        {
            String uuid = Utils.extract_re("uuid:(.*?):",usn);
            String urn = Utils.extract_re("urn:(.*?):",usn);
            String device_type_string = Utils.extract_re("device:(.*?):",usn);

            if (uuid.isEmpty())
                return false;
            if (urn.isEmpty())
                return false;
            if (device_type_string.isEmpty())
                return false;

            String expected_urn = "";
            if (device_type_string.equals(Device.deviceType.MediaRenderer.toString()))
                expected_urn = httpUtils.upnp_urn;
            if (device_type_string.equals(Device.deviceType.MediaServer.toString()))
                expected_urn = httpUtils.upnp_urn;
            if (device_type_string.equals(Device.deviceType.OpenHomeRenderer.toString()))
                expected_urn = httpUtils.open_device_urn;

            if (expected_urn.isEmpty())
                return false;
            if (!urn.equals(expected_urn))
            {
                Utils.warning(0,0,"mismatched urn " + urn + " for " + device_type_string);
                return false;
            }

            return true;
        }


    }   // class SSDPSearchListener



    //-----------------------------------------------------------
    // SSDPDevice
    //-----------------------------------------------------------


    public class SSDPDevice implements Runnable
        // only uses friendlyName to differentiate them
        // if conflicts, really should use the device uuids
    {
        public class ServiceHash extends HashMap<Service.serviceType,SSDPService>{}

        private String location;
        private boolean valid = false;
        private ServiceHash services = new ServiceHash();

        // following parsed from USN
        // actual device type "Source" changed to OpenHomeRenderer
        // friendlyName "WDTVLive = currently in use" changed to "WDTVLive"

        private Device.deviceType device_type = Device.deviceType.DeviceNone;
        private Device.deviceGroup device_group = Device.deviceGroup.DEVICE_GROUP_NONE;
        private String device_uuid = "";
        private String device_urn = "";
        private String device_url = "";

        // following gotten from device description document

        private String friendlyName = "";
        private String icon_path = "";

        // accessors

        public boolean isValid()                    { return valid; }
        public String getFriendlyName()             { return friendlyName; }
        public Device.deviceGroup getDeviceGroup()  { return device_group; }
        public Device.deviceType getDeviceType()    { return device_type; }
        public String getDeviceUUID()               { return device_uuid; }
        public String getDeviceUrn()                { return device_urn; }
        public String getDeviceUrl()                { return device_url; }
        public String getIconPath()                 { return icon_path; }
        public ServiceHash getServices()            { return services; }


        // validation

        private boolean hasRequiredServices()
        {
            if (device_type == Device.deviceType.MediaServer)
                return services.get(Service.serviceType.ContentDirectory) != null;
            if (device_type == Device.deviceType.MediaRenderer)
                return services.get(Service.serviceType.AVTransport) != null;

            // we require them all for openHome

            if (device_type == Device.deviceType.OpenHomeRenderer)
            {
                if (services.get(Service.serviceType.OpenProduct) == null) return false;
                if (services.get(Service.serviceType.OpenPlaylist) == null) return false;
                if (services.get(Service.serviceType.OpenInfo) == null) return false;
                if (services.get(Service.serviceType.OpenVolume) == null) return false;
                if (services.get(Service.serviceType.OpenTime) == null) return false;
                return true;
            }

            return false;
        }


        // constructor
        // usn is of the form:
        // uuid:987f-887d-d98d-d97d97d::urn:schemas-upnp-org:device:MediaServer:1

        SSDPDevice(DeviceManager dm, String loc, String usn )
        {
            device_manager = dm;
            location = loc;
            device_uuid = Utils.extract_re("uuid:(.*?):",usn);
            device_urn = Utils.extract_re("urn:(.*?):",usn);
            String device_type_string = Utils.extract_re("device:(.*?):",usn);
            if (device_type_string.equals("Source"))
                device_type_string = "OpenHomeRenderer";

            // if the uuid already exists in device_manager, skip it

            if (dm.getDevice(device_uuid) != null)
            {
                Utils.log(dbg_ssdp_search,0,"Device Already Exists " + device_type_string + "::" + device_uuid);
                return;
            }

              // the device_type_string now maps to a valid device_type enum

            try
            {
                device_type = Device.deviceType.valueOf(device_type_string);
            }
            catch (Exception e)
            {
                Utils.warning(0,0,"Illegal device_type:" + device_type_string);
                return;
            }

            device_group = Device.groupOf(device_type);
            device_url = "http://" + Utils.ipFromUrl(loc) + ":" + Utils.portFromUrl(loc);
            valid = true;
        }


        // get the device description and parse it for friendlyName, icon, services, etc

        public void run()  { synchronized(device_manager)
            {
                // String url = location + device_url;
                Utils.log(dbg_ssdp_search + 1,0,"SSDPDeviceListener::run(" + device_type + ") at " + location);
                Document doc = Utils.xml_request(location);
                if (doc == null)
                {
                    Utils.error("Could not get device description document for " + device_type + " at " + location);
                    num_ssdp_devices_started--;
                    return;
                }
                Utils.log(dbg_ssdp_search + 1,0,"Got device_description(" + device_type + ") from " + location);

                // get the friendly name

                Element doc_ele = doc.getDocumentElement();
                if (doc_ele == null)
                {
                    Utils.error("Could not get device description document element for " + device_type + " at " + location);
                    num_ssdp_devices_started--;
                    return;
                }

                friendlyName = Utils.getTagValue(doc_ele,"friendlyName");
                if (friendlyName == null || friendlyName.equals(""))
                {
                    Utils.error("No friendlyName found for " + device_type + " at " + location);
                    num_ssdp_devices_started--;
                    return;
                }

                friendlyName = friendlyName.replace("WDTVLive - currently in use","WDTVLive");
                // kludge - we should really be using UUIDs and the most recent name
                Utils.log(dbg_ssdp_search,0,"Got friendlyName(" + friendlyName + ") from " + device_url);

                // get the icon path

                NodeList icons = doc.getElementsByTagName("icon");
                if (icons.getLength() > 0)
                {
                    final Element elem = (Element) icons.item(0);
                    icon_path = Utils.getTagValue(elem,"url");
                    if (icon_path == null) icon_path = "";
                }
                if (icon_path.equals(""))
                    Utils.warning(0,0,"no icon found for " + device_type + " at " + location);


                // loop through the services, creating SSDPServices
                // all services shall be known
                // but don't get documents yet


                NodeList servs = doc.getElementsByTagName("service");
                if (servs.getLength() > 0)
                {
                    for (int i = 0; i < servs.getLength(); i++)
                    {
                        Element serv_ele = (Element) servs.item(i);

                        // GET THE SERVICE TYPE

                        Boolean ok = false;
                        Service.serviceType service_type = Service.serviceType.ServiceNone;
                        String long_service_type_string = Utils.getTagValue(serv_ele,"serviceType");
                        String service_type_string = Utils.extract_re("service:(.*):",long_service_type_string);
                        if (device_type == Device.deviceType.OpenHomeRenderer)
                            service_type_string = "Open" + service_type_string;

                        // if we return before we fire off the document request, then
                        // this device dies on the vine

                        try
                        {
                            service_type = Service.serviceType.valueOf(service_type_string);
                            ok = true;
                        }
                        catch (Exception e)
                        {
                            Utils.warning(0,1,"Skipping invalid service type(" + service_type_string + ")");
                        }

                        if (ok)
                        {
                            SSDPService service = new SSDPService(
                                this,
                                service_type,
                                fix_url(Utils.getTagValue(serv_ele,"SCPDURL")),
                                fix_url(Utils.getTagValue(serv_ele,"controlURL")),
                                fix_url(Utils.getTagValue(serv_ele,"eventSubURL")));

                            if (service.valid())
                                services.put(service.service_type,service);
                        }
                    }
                }

                // if the device is sufficient, proceed to get documents
                // if we can't get a service document for any service,
                // the whole device is considered invalid, and we bail

                if (hasRequiredServices())
                {
                    for (SSDPService service : services.values())
                    {
                        if (!service.loadServiceDoc())
                        {
                            num_ssdp_devices_started--;
                            return;
                        }
                    }
                }
                else
                {
                    Utils.warning(0,1,"Device: " + friendlyName + " does not present a sufficient list of services for " + device_type);
                    num_ssdp_devices_started--;
                    return;
                }

                // Finally, we can create the Device, which will in turn create the
                // derived Services and tell the DeviceManager  the device

                num_new_devices++;
                Utils.log(dbg_ssdp_search,1,"Calling device_manager.createDevice(" + device_type + ") '" + friendlyName + "' at " + device_url);
                device_manager.createDevice(this);
                num_ssdp_devices_started--;
            }   // synchronized
        }   // run()

    }   // class SSDPDevice


    private String fix_url(String st)
        // add a leading slash to a service url in case it doesn't have one
    {
        if (st!=null && !st.equals("") && !st.startsWith("/"))
            st = "/" + st;
        return st;
   }


    //-----------------------------------------------------------
    // SSDPService
    //-----------------------------------------------------------

    public class SSDPService
        // what's available from parsing the device description
    {
        private SSDPDevice device;
        private Service.serviceType service_type;
        private String desc_path;
        private String control_path;
        private String event_path;
        private Document service_doc = null;

        public Service.serviceType getServiceType()  { return service_type; }
        public String getControlPath()  { return control_path; }
        public String getEventPath()    { return event_path; }
        public Document getServiceDoc()   { return service_doc; }


        public SSDPService(
            SSDPDevice d,
            Service.serviceType type,
            String d_path,
            String c_path,
            String e_path )
        {
            Utils.log(dbg_ssdp_search,1,"new SSDPService(" + type + ")");

            device = d;
            service_type = type;
            desc_path = d_path;
            control_path = c_path;
            event_path = e_path;
        }


        public boolean loadServiceDoc()
            // not to be confused with getServiceDescription()
            // which returns the existing doc, this one gets it
            // from the network.
        {
            String url = device.getDeviceUrl() + desc_path;
            Utils.log(dbg_ssdp_search,1,"Getting SSDP_Service(" + service_type + ") from " + url);
            Utils.log(dbg_ssdp_search + 1,2,"desc_url=" + desc_path);
            Utils.log(dbg_ssdp_search + 1,2,"control_url=" + control_path);
            Utils.log(dbg_ssdp_search + 1,2,"event_url=" + event_path);

            service_doc = Utils.xml_request(url);
            if (service_doc == null)
            {
                Utils.error("Could not get service_doc for " + service_type + " from " + url);
                return false;
            }
            return true;
        }


        public boolean valid()
        {
            String msg = null;
            /* if (service_type == null || service_type.equals(""))
                msg = "Missing Service Type";
            else */

            if (desc_path == null || desc_path.equals(""))
                msg = "Missing Description Path";
            else if (control_path == null || control_path.equals(""))
                msg = "Missing Control Path";
            else if (event_path == null || event_path.equals(""))
                msg = "Missing Event Path";
            if (msg != null)
            {
                Utils.error(msg);
                return false;
            }

            // return true if it's an allowed service (that we can create)
            // for the deviceType

            Device.deviceType device_type = device.getDeviceType();

            if (device_type == Device.deviceType.MediaServer)
            {
                if (service_type == Service.serviceType.ContentDirectory) return true;
            }
            else if (device_type == Device.deviceType.MediaRenderer)
            {
                if (service_type == Service.serviceType.AVTransport) return true;
                if (service_type == Service.serviceType.RenderingControl) return true;
            }
            else if (device_type == Device.deviceType.OpenHomeRenderer)
            {
                if (service_type == Service.serviceType.OpenProduct) return true;
                if (service_type == Service.serviceType.OpenPlaylist) return true;
                if (service_type == Service.serviceType.OpenInfo) return true;
                if (service_type == Service.serviceType.OpenVolume) return true;
                if (service_type == Service.serviceType.OpenTime) return true;
            }

            Utils.warning(dbg_ssdp_search+1,1,"Skipping unsupported service:" + device_type + "::" + service_type);
            return false;
        }

    }   // SSDPService


}   // class SSDPSearch



