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
import prh.server.SSDPServer;
import prh.utils.Utils;



public class SSDPSearch implements Runnable
    // Implements a runnable that performs an aynchronous SSDP Search
    // for interesting devices and services.
    //
    // Maintains a cache of previously found devices which Artisan can
    // choose to use, or clear, at startup.
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
    private static int dbg_ssdp_search = 2;
    private static int LISTEN_PORT = 8070;
    private static int SEARCH_TIME = 4;

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


    private DeviceManager device_manager;
    public SSDPSearch(DeviceManager dm)
    {
        device_manager = dm;
    }




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



    private static boolean allowedDevice(String device_type)
    // we only care about these service types
    {
        if (device_type.equals(SSDP_DEVICE_MEDIA_SERVER)) return true;
        if (device_type.equals(SSDP_DEVICE_MEDIA_RENDERER)) return true;
        if (device_type.equals(SSDP_DEVICE_OPEN_HOME)) return true;
        return false;
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
                        String device_type = null;

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
                                    if (lvalue.equalsIgnoreCase("st"))
                                        device_type = rvalue;
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


                        if (location != null && !location.equals("") &&
                            device_type != null && !device_type.equals(""))
                        {
                            if (!location.contains(Utils.server_ip + ":" + Utils.server_port))
                            {
                                if (allowedDevice(device_type))
                                {
                                    SSDPDevice device = new SSDPDevice(
                                        device_manager,
                                        device_type,
                                        location);
                                    Thread device_thread = new Thread(device);
                                    device_thread.start();
                                }
                            }
                        }
                        else
                        {
                            Utils.warning(0,1,"Reply from device(" + reply.getAddress() + ":" + reply.getPort() + ") without location or ST device_type");
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

            Utils.log(dbg_ssdp_search+1,0,"SSDPSearchListener::run() finished");

        }   // run()
    }   // class SSDPSearchListener



    //-----------------------------------------------------------
    // SSDPDevice
    //-----------------------------------------------------------

    private static boolean hasRequiredServices(String device_type, HashMap<String,SSDPService> services)
    {
        if (device_type.equals(SSDP_DEVICE_MEDIA_SERVER))
            return services.get(SSDP_SERVICE_CONTENT_DIRECTORY) != null;
        if (device_type.equals(SSDP_DEVICE_MEDIA_RENDERER))
            return services.get(SSDP_SERVICE_AV_TRANSPORT) != null;

        // we require them all for openHome

        if (device_type.equals(SSDP_DEVICE_OPEN_HOME))
        {
            if (services.get(SSDP_SERVICE_OPEN_PRODUCT) == null) return false;
            if (services.get(SSDP_SERVICE_OPEN_PLAYLIST) == null) return false;
            if (services.get(SSDP_SERVICE_OPEN_INFO) == null) return false;
            if (services.get(SSDP_SERVICE_OPEN_TIME) == null) return false;
            if (services.get(SSDP_SERVICE_OPEN_PRODUCT) == null) return false;
            if (services.get(SSDP_SERVICE_OPEN_VOLUME) == null) return false;
            return true;
        }

        return false;
    }




    public class SSDPDevice implements Runnable
        // only uses friendlyName to differentiate them
        // if conflicts, really should use the device uuids
    {
        private String device_type;
        private String device_url;
        private String location;
        private String friendlyName;
        private String icon_url;

        private HashMap<String,SSDPService> services = new HashMap<String,SSDPService>();

        public String getDeviceUrl()  { return device_url; }
        public String getDeviceType()  { return device_type; }
        public String getFriendlyName() { return friendlyName; }
        public String getIconUrl() { return icon_url; }


        public HashMap<String,SSDPService> getServices() { return services; }


        SSDPDevice(DeviceManager dm,
                   String d_type,
                   String loc)
        {
            device_manager = dm;
            device_type = d_type;
            location = loc;
            device_url = "http://" + Utils.ipFromUrl(loc) + ":" + Utils.portFromUrl(loc);
            icon_url = "";
        }

        public void run()
        {
            // String url = location + device_url;
            Utils.log(dbg_ssdp_search + 1,0,"SSDPDeviceListener::run(" + device_type + ") at " + location);
            Document doc = Utils.xml_request(location);
            if (doc == null)
            {
                Utils.error("Could not get device description document for " + device_type + " at " + location);
                return;
            }
            Utils.log(dbg_ssdp_search+1,0,"Got device_description(" + device_type + ") from " + location);

            // get the icon and friendly name

            Element doc_ele = doc.getDocumentElement();
            if (doc_ele == null)
            {
                Utils.error("Could not get device description document element for " + device_type + " at " + location);
                return;
            }

            friendlyName = Utils.getTagValue(doc_ele,"friendlyName");
            if (friendlyName == null || friendlyName.equals(""))
            {
                Utils.error("No friendlyName found for " + device_type + " at " + location);
                return;
            }
            Utils.log(dbg_ssdp_search,0,"Got friendlyName(" + friendlyName + ") from " + device_url);

            NodeList icons = doc.getElementsByTagName("icon");
            if (icons.getLength() > 0)
            {
                final Element elem = (Element) icons.item(0);
                icon_url =  Utils.getTagValue(elem,"url");
                if (icon_url == null) icon_url = "";
            }
            if (icon_url.equals(""))
                Utils.warning(0,0,"no icon found for " + device_type + " at " + location);

            // loop through the services, creating SSDPServices
            // but don't get documents yet

            NodeList servs = doc.getElementsByTagName("service");
            if (servs.getLength() > 0)
            {
                for (int i=0; i<servs.getLength(); i++)
                {
                    Element serv_ele = (Element) servs.item(i);
                    SSDPService service = new SSDPService(this);
                    service.service_type = Utils.getTagValue(serv_ele,"serviceType");
                    service.desc_url = fix_url(Utils.getTagValue(serv_ele,"SCPDURL"));
                    service.control_url = fix_url(Utils.getTagValue(serv_ele,"controlURL"));
                    service.event_url = fix_url(Utils.getTagValue(serv_ele,"eventSubURL"));
                    if (service.valid())
                        services.put(service.service_type,service);
                }
            }

            // if the device is sufficient, proceed to get documents
            // if we can't get a service document for any service,
            // the whole device is considered invalid, and we bail

            if (hasRequiredServices(device_type,services))
            {
                for (SSDPService service : services.values())
                {
                    if (!service.getServiceDoc())
                        return;
                }
            }
            else
            {
                Utils.warning(0,0,"Device: " + friendlyName + " does not present a sufficent list of services for " + device_type);
                return;
            }


            // Finally, we can create the Device, which will in turn create the
            // derived Services and tell the DeviceManager  the device

            Utils.log(dbg_ssdp_search,1,"CREATING DEVICE(" + device_type + ") '" + friendlyName + "' at " + device_url);
            device_manager.createDevice(this);

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

        public String service_type;
        public String desc_url;
        public String control_url;
        public String event_url;
        public Document service_doc;


        public boolean getServiceDoc()
        {
            String url = device.getDeviceUrl() + desc_url;
            Utils.log(dbg_ssdp_search,1,"Getting SSDP_Service(" + service_type +") from " + url);
            Utils.log(dbg_ssdp_search+1,2,"desc_url=" + desc_url);
            Utils.log(dbg_ssdp_search+1,2,"control_url=" + control_url);
            Utils.log(dbg_ssdp_search+1,2,"event_url="+event_url);

            service_doc = Utils.xml_request(url);
            if (service_doc == null)
            {
                Utils.error("Could not get service_doc for " + service_type + " from " + url);
                return false;
            }
            return true;
        }


        public SSDPService(SSDPDevice d)
        {
            device = d;
        }

        public boolean valid()
        {
            String msg = null;
            if (service_type == null || service_type.equals(""))
                msg = "Missing Service Type";
            else if (desc_url == null || desc_url.equals(""))
                msg = "Missing Description Url";
            else if (control_url == null || control_url.equals(""))
                msg = "Missing Control Url";
            else if (event_url == null || event_url.equals(""))
                msg = "Missing Event Url";
            if (msg != null)
            {
                Utils.error(msg);
                return false;
            }

            // return true if it's an allowed service (that we can create)
            // for the device_type

            String device_type = device.getDeviceType();

            if (device_type.equals(SSDP_DEVICE_MEDIA_SERVER))
                if (service_type.equals(SSDP_SERVICE_CONTENT_DIRECTORY)) return true;
            if (device_type.equals(SSDP_DEVICE_MEDIA_RENDERER))
            {
                if (service_type.equals(SSDP_SERVICE_AV_TRANSPORT)) return true;
                if (service_type.equals(SSDP_SERVICE_RENDERING_CONTROL)) return true;
            }
            if (device_type.equals(SSDP_DEVICE_OPEN_HOME))
            {
                if (service_type.equals(SSDP_SERVICE_OPEN_PRODUCT)) return true;
                if (service_type.equals(SSDP_SERVICE_OPEN_PLAYLIST)) return true;
                if (service_type.equals(SSDP_SERVICE_OPEN_INFO)) return true;
                if (service_type.equals(SSDP_SERVICE_OPEN_TIME)) return true;
                if (service_type.equals(SSDP_SERVICE_OPEN_PRODUCT)) return true;
                if (service_type.equals(SSDP_SERVICE_OPEN_VOLUME)) return true;
            }

            Utils.warning(dbg_ssdp_search+1,1,"Skipping unsupported service:" + device_type + "::" + service_type);
            return false;
        }

    }   // SSDPService


}   // class SSDPSearch



