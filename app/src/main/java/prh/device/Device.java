package prh.device;


import java.util.HashMap;

import prh.artisan.Artisan;
import prh.device.service.AVTransport;
import prh.device.service.ContentDirectory;
import prh.device.service.OpenInfo;
import prh.device.service.OpenPlaylist;
import prh.device.service.OpenProduct;
import prh.device.service.OpenTime;
import prh.device.service.OpenVolume;
import prh.device.service.RenderingControl;
import prh.utils.Utils;


public abstract class Device
    // A base classes for Devices
    // Should have UUID, but we just use friendlyName as unique id
{
    private static int dbg_d = 1;


    public static final String DEVICE_MEDIA_SERVER = "MediaServer";
    public static final String DEVICE_MEDIA_RENDERER = "MediaRenderer";
    public static final String DEVICE_OPEN_HOME = "OpenHomeRenderer";
        // actual ssdp device type is "Source"
    public static final String DEVICE_LOCAL_LIBRARY = "LocalLibrary";
    public static final String DEVICE_LOCAL_RENDERER = "LocalRenderer";
    public static final String DEVICE_REMOTE_LIBRARY = "RemoteLibrary";
    public static final String DEVICE_REMOTE_RENDERER = "RemoteRenderer";

    public static final String SERVICE_CONTENT_DIRECTORY = "ContentDirectory";
    public static final String SERVICE_AV_TRANSPORT = "AVTransport";
    public static final String SERVICE_RENDERING_CONTROL = "RenderingControl";
    public static final String SERVICE_OPEN_PRODUCT = "Product";
    public static final String SERVICE_OPEN_PLAYLIST = "Playlist";
    public static final String SERVICE_OPEN_INFO = "Info";
    public static final String SERVICE_OPEN_TIME = "Time";
    public static final String SERVICE_OPEN_VOLUME = "Volume";



    protected Artisan artisan;

    private String device_url;
    private String device_type;
    private String friendlyName;
    private HashMap<String,Service> services = new HashMap<String,Service>();

    public String getDeviceUrl() { return device_url; }
    public String getDeviceType() { return device_type; }
    public String getFriendlyName() { return friendlyName; }
    public HashMap<String,Service> getServices() { return services; }


    public static String short_type(String what, String st)
        // utility to change an SSDP device or service type
        // into one of our more simple names. Can be called
        // over again with no harm.
    {
        st = st.replaceAll("^.*" + what + ":","");
        st = st.replaceAll(":.*$","");
        return st;
    }


    // Constructor

    public Device(Artisan ma, String name, String short_type, String url)
    {
        artisan = ma;
        friendlyName = name;
        device_type = short_type;
        device_url = url;

        Utils.log(dbg_d,3,"new Device(" + device_type + "," + friendlyName + ") at " + device_url);
    }

    // Service Management

    public void addService(Service service)
        // returns true if it already existed
        // we don't check if they're different
    {
        services.put(service.getFriendlyName(),service);
    }


    // Default Service Construction

    public void createSSDPServices(SSDPSearch.SSDPDevice ssdp_device)
    {
        HashMap<String,SSDPSearch.SSDPService> ssdp_services = ssdp_device.getServices();
        for (SSDPSearch.SSDPService ssdp_service : ssdp_services.values())
        {
            Service service;
            String service_type = short_type("service",ssdp_service.service_type);
            Utils.log(dbg_d,4,"Creating " + service_type + " Service on " + friendlyName);

            if (service_type.equals(SERVICE_CONTENT_DIRECTORY))
                service = new ContentDirectory(
                    artisan,
                    this,
                    service_type,
                    ssdp_service.control_url,
                    ssdp_service.event_url,
                    ssdp_service.service_doc);

            else if (service_type.equals(SERVICE_AV_TRANSPORT))
                service = new AVTransport(
                    artisan,
                    this,
                    service_type,
                    ssdp_service.control_url,
                    ssdp_service.event_url,
                    ssdp_service.service_doc);

            else if (service_type.equals(SERVICE_RENDERING_CONTROL))
                service = new RenderingControl(
                    artisan,
                    this,
                    service_type,
                    ssdp_service.control_url,
                    ssdp_service.event_url,
                    ssdp_service.service_doc);

            else if (service_type.equals(SERVICE_OPEN_PRODUCT))
                service = new OpenProduct(
                    artisan,
                    this,
                    service_type,
                    ssdp_service.control_url,
                    ssdp_service.event_url,
                    ssdp_service.service_doc);

            else if (service_type.equals(SERVICE_OPEN_PLAYLIST))
                service = new OpenPlaylist(
                    artisan,
                    this,
                    service_type,
                    ssdp_service.control_url,
                    ssdp_service.event_url,
                    ssdp_service.service_doc);

            else if (service_type.equals(SERVICE_OPEN_INFO))
                service = new OpenInfo(
                    artisan,
                    this,
                    service_type,
                    ssdp_service.control_url,
                    ssdp_service.event_url,
                    ssdp_service.service_doc);

            else if (service_type.equals(SERVICE_OPEN_TIME))
                service = new OpenTime(
                    artisan,
                    this,
                    service_type,
                    ssdp_service.control_url,
                    ssdp_service.event_url,
                    ssdp_service.service_doc);

            else if (service_type.equals(SERVICE_OPEN_VOLUME))
                service = new OpenVolume(
                    artisan,
                    this,
                    service_type,
                    ssdp_service.control_url,
                    ssdp_service.event_url,
                    ssdp_service.service_doc);


        }   // for each service
   }    // Device Ctor




}   // class Device


