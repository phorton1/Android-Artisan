package prh.device;


//-----------------------------------------------------------
// SSDPDevice
//-----------------------------------------------------------

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.HashMap;

import prh.utils.Utils;


public class SSDPSearchDevice implements Runnable
    // only uses friendlyName to differentiate them
    // if conflicts, really should use the device uuids
{
    public int dbg_ssdp_device = 1;
    public class ServiceHash extends HashMap<Service.serviceType,SSDPSearchService> {}

    private String location;
    private ServiceHash services = new ServiceHash();
    private DeviceManager device_manager;

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


    private String fix_url(String st)
    // add a leading slash to a service url in case it doesn't have one
    {
        if (st!=null && !st.equals("") && !st.startsWith("/"))
            st = "/" + st;
        return st;
    }


    //---------------------------------------------------
    // constructor
    //---------------------------------------------------
    // usn is of the form:
    // uuid:987f-887d-d98d-d97d97d::urn:schemas-upnp-org:device:MediaServer:1

    SSDPSearchDevice(DeviceManager dm,
                     String loc,
                     Device.deviceType dtype,
                     String uuid,
                     String urn )
    {
        location = loc;
        device_manager = dm;
        device_type = dtype;
        device_uuid = uuid;
        device_urn = urn;
        device_group = Device.groupOf(device_type);
        device_url = "http://" + Utils.ipFromUrl(loc) + ":" + Utils.portFromUrl(loc);
    }



    //---------------------------------------------------
    // run
    //---------------------------------------------------
    // get the device description and parse it for friendlyName, icon, services, etc

    public void run()
    {
        synchronized(device_manager)
        {
            // String url = location + device_url;
            Utils.log(dbg_ssdp_device + 1,0,"SSDPDeviceListener::run(" + device_type + ") at " + location);
            Document doc = Utils.xml_request(location);
            if (doc == null)
            {
                Utils.error("Could not get device description document for " + device_type + " at " + location);
                device_manager.incDecBusy(-1);
                return;
            }
            Utils.log(dbg_ssdp_device + 1,0,"Got device_description(" + device_type + ") from " + location);

            // get the friendly name

            Element doc_ele = doc.getDocumentElement();
            if (doc_ele == null)
            {
                Utils.error("Could not get device description document element for " + device_type + " at " + location);
                device_manager.incDecBusy(-1);
                return;
            }

            friendlyName = Utils.getTagValue(doc_ele,"friendlyName");
            if (friendlyName == null || friendlyName.equals(""))
            {
                Utils.error("No friendlyName found for " + device_type + " at " + location);
                device_manager.incDecBusy(-1);
                return;
            }

            friendlyName = friendlyName.replace("WDTVLive - currently in use","WDTVLive");
            // kludge - we should really be using UUIDs and the most recent name
            Utils.log(dbg_ssdp_device,0,"Got friendlyName(" + friendlyName + ") from " + device_url);

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
                        Utils.warning(dbg_ssdp_device,1,"Skipping invalid service type(" + service_type_string + ")");
                    }

                    if (ok)
                    {
                        SSDPSearchService service = new SSDPSearchService(
                            this,
                            service_type,
                            fix_url(Utils.getTagValue(serv_ele,"SCPDURL")),
                            fix_url(Utils.getTagValue(serv_ele,"controlURL")),
                            fix_url(Utils.getTagValue(serv_ele,"eventSubURL")));

                        if (service.valid())
                            services.put(service.getServiceType(),service);
                    }
                }
            }

            // if the device is sufficient, proceed to get documents
            // if we can't get a service document for any service,
            // the whole device is considered invalid, and we bail

            if (hasRequiredServices())
            {
                for (SSDPSearchService service : services.values())
                {
                    if (!service.loadServiceDoc())
                    {
                        device_manager.incDecBusy(-1);
                        return;
                    }
                }
            }
            else
            {
                Utils.warning(0,1,"Device: " + friendlyName + " does not present a sufficient list of services for " + device_type);
                device_manager.incDecBusy(-1);
                return;
            }

            // Finally, we can create the Device, which will in turn create the
            // derived Services and tell the DeviceManager  the device

            Utils.log(dbg_ssdp_device,1,"Calling device_manager.createDevice(" + device_type + ") '" + friendlyName + "' at " + device_url);
            device_manager.createDevice(this);
            device_manager.incDecBusy(-1);

        }   // synchronized
    }   // run()

}   // class SSDPDevice

