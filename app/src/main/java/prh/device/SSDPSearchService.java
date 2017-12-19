package prh.device;


//-----------------------------------------------------------
// SSDPService
//-----------------------------------------------------------

import org.w3c.dom.Document;

import prh.utils.Utils;


public class SSDPSearchService
    // what's available from parsing the device description
{
    private static int dbg_ssdp_service = 1;

    private SSDPSearchDevice device;
    private Service.serviceType service_type;
    private String desc_path;
    private String control_path;
    private String event_path;
    private Document service_doc = null;

    public Service.serviceType getServiceType()  { return service_type; }
    public String getControlPath()  { return control_path; }
    public String getEventPath()    { return event_path; }
    public Document getServiceDoc()   { return service_doc; }


    public SSDPSearchService(
        SSDPSearchDevice d,
        Service.serviceType type,
        String d_path,
        String c_path,
        String e_path )
    {
        Utils.log(dbg_ssdp_service,1,"new SSDPService(" + type + ")");

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
        Utils.log(dbg_ssdp_service,1,"Getting SSDP_Service(" + service_type + ") from " + url);
        Utils.log(dbg_ssdp_service + 1,2,"desc_url=" + desc_path);
        Utils.log(dbg_ssdp_service + 1,2,"control_url=" + control_path);
        Utils.log(dbg_ssdp_service + 1,2,"event_url=" + event_path);

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

        Utils.warning(dbg_ssdp_service +1,1,"Skipping unsupported service:" + device_type + "::" + service_type);
        return false;
    }

}   // SSDPService
