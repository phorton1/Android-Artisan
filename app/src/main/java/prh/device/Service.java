package prh.device;

import org.w3c.dom.Document;

import prh.artisan.Artisan;
import prh.utils.Utils;

public abstract class Service
    // base class of services
    // ONLY DERIVED CLASSES SEE THE SERVICE_DESCRIPTION DOC
{

    public static enum serviceType {
        ServiceNone,
        ContentDirectory,
        AVTransport,
        RenderingControl,
        OpenProduct,
        OpenPlaylist,
        OpenInfo,
        OpenTime,
        OpenVolume
    }


    protected Artisan artisan;
    private Device device;
    private serviceType service_type;
    private String control_path;
    private String event_path;

    public Device getDevice()       { return device; }
    public String getFriendlyName() { return device.getFriendlyName(); }
    public serviceType getServiceType()  { return service_type; }
    public String getControlUrl()   { return device.getDeviceUrl() + control_path; }
    public String getEventUrl()     { return  device.getDeviceUrl()  + event_path; }
    public String getEventPath()    { return event_path; }

    public Service(Artisan ma,
                   Device d,
                   SSDPSearchService ssdp_service)
    {
        artisan = ma;
        device = d;
        service_type = ssdp_service.getServiceType();
        control_path = ssdp_service.getControlPath();
        event_path = ssdp_service.getEventPath();
    }


    protected Service(Artisan ma, Device d)
    {
        artisan = ma;
        device = d;
    }

    public String toString()
    {
        return
            service_type.toString() + "\t" +
            control_path + "\t" +
            event_path + "\t";
    }

    protected boolean fromString(StringBuffer buffer)
    {
        service_type = serviceType.valueOf(Utils.pullTabPart(buffer));
        control_path = Utils.pullTabPart(buffer);
        event_path   = Utils.pullTabPart(buffer);
        return true;
    }



}   // base class Service
