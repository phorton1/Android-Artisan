package prh.device;

import org.w3c.dom.Document;

import prh.artisan.Artisan;

public abstract class Service
    // base class of services
    // ONLY DERIVED CLASSES SEE THE SERVICE_DESCRIPTION DOC
{
    private Artisan artisan;
    private Device device;
    private String service_type;
    private String control_path;
    private String event_path;
    private String urn;

    public Device detDevice()       { return device; }
    public String getFriendlyName() { return device.getFriendlyName(); }
    public String getService_type() { return service_type; }
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

}   // base class Service
