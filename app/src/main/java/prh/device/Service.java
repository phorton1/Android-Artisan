package prh.device;

import org.w3c.dom.Document;

import prh.artisan.Artisan;
import prh.utils.Utils;

public abstract class Service
    // base class of services
    // ONLY DERIVED CLASSES SEE THE SERVICE_DESCRIPTION DOC
{
    protected Artisan artisan;
    private Device device;
    private String service_type;
    private String control_path;
    private String event_path;
    private String urn;

    public Device getDevice()       { return device; }
    public String getFriendlyName() { return device.getFriendlyName(); }
    public String getServiceType()  { return service_type; }
    public String getControlUrl()   { return device.getDeviceUrl() + control_path; }
    public String getEventUrl()     { return  device.getDeviceUrl()  + event_path; }
    public String getUrn()          { return urn; }

    public Service(Artisan ma,
                   Device d,
                   String u,
                   String short_type,
                   String ctrl_path,
                   String evt_path)
    {
        artisan = ma;
        device = d;
        urn = u;
        service_type = short_type;
        control_path = ctrl_path;
        event_path = evt_path;
    }


    protected Service(Artisan ma, Device d)
    {
        artisan = ma;
        device = d;
    }

    public String toString()
    {
        return
            service_type + "\t" +
            control_path + "\t" +
            event_path + "\t" +
            service_type + "\t" +
            urn + "\t";
    }

    protected boolean fromString(StringBuffer buffer)
    {
        service_type = Utils.pullTabPart(buffer);
        control_path = Utils.pullTabPart(buffer);
        event_path   = Utils.pullTabPart(buffer);
        service_type = Utils.pullTabPart(buffer);
        urn = Utils.pullTabPart(buffer);
        return true;
    }



}   // base class Service
