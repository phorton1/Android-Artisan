package prh.device;


import org.w3c.dom.Document;

import java.util.HashMap;

import prh.artisan.Artisan;
import prh.artisan.EventHandler;
import prh.device.service.AVTransport;
import prh.device.service.ContentDirectory;
import prh.device.service.OpenInfo;
import prh.device.service.OpenPlaylist;
import prh.device.service.OpenProduct;
import prh.device.service.OpenTime;
import prh.device.service.OpenVolume;
import prh.device.service.RenderingControl;
import prh.utils.Utils;
import prh.utils.networkRequest;
import prh.types.stringHash;


public abstract class Device implements Comparable<Device>
    // A base classes for Devices
    // Should have UUID, but we just use friendlyName as unique id
{
    private static int dbg_d = 1;

    // Constants and Types

    public static enum deviceStatus
    {
        UNKNOWN,
        ONLINE,
        OFFLINE
    }

    public static enum deviceGroup
    {
        DEVICE_GROUP_NONE,
        DEVICE_GROUP_LIBRARY,
        DEVICE_GROUP_RENDERER,
        DEVICE_GROUP_PLAYLIST_SOURCE
    }

    public static enum deviceType
    {
        DeviceNone,
        LocalRenderer,
        LocalLibrary,
        LocalPlaylistSource,
        MediaServer,
        MediaRenderer,
        OpenHomeRenderer
    }

    public class ServiceHash extends HashMap<Service.serviceType,Service> {}

    // member variables

    protected Artisan artisan;
    protected deviceGroup device_group = deviceGroup.DEVICE_GROUP_NONE;
    protected deviceType device_type = deviceType.DeviceNone;
    protected String device_uuid;
    protected String device_urn;
    protected String device_url;
    protected String friendlyName;
    protected String icon_path;
    protected deviceStatus device_status = deviceStatus.UNKNOWN;
    protected ServiceHash services = new ServiceHash();

    // accessors
    // isLocal() MUST be overriden by local devices

    public boolean isLocal()                { return false; }
    public ServiceHash getServices()        { return services; }
    public deviceType getDeviceType()       { return device_type; }
    public deviceGroup getDeviceGroup()     { return device_group; };
    public String getDeviceUUID()           { return device_uuid; }
    public String getDeviceUrn()            { return device_urn; }
    public String getDeviceUrl()            { return device_url; }
    public String getFriendlyName()         { return friendlyName; }
    public deviceStatus getDeviceStatus()   { return device_status; }

    // failure scheme

    protected int failure_count = 0;
    public static int NUM_FAILURE_RETRIES = 3;
        // networkRequest times out after about 10 seconds
        // so this means we might retry for 30 seconds

    public void deviceSuccess() { failure_count = 0; }
    public void deviceFailure()
    {
        failure_count++;
        Utils.warning(0,0,"Device(" + getFriendlyName() + ") FAILURE count=" + failure_count);
        if (failure_count >= NUM_FAILURE_RETRIES)
        {
            failure_count = 0;
            setDeviceStatus(deviceStatus.OFFLINE);
        }
    }


    public void setDeviceStatus(deviceStatus new_status)
        // Change the status AND send Artisan Event
    {
        boolean changed = device_status != new_status;
        device_status = new_status;
        if (changed)
        artisan.handleArtisanEvent(EventHandler.EVENT_DEVICE_STATUS_CHANGED,this);

    }


    public String getIconUrl()
    {
        if (icon_path.isEmpty())
            return "";
        return device_url + icon_path;
    }

    public void addService(Service service)
    {
        services.put(service.getServiceType(),service);
    }


    // Comparable interface
    // Sorts by name, with local items last

    public int compareTo(Device other)
    {
        if (isLocal() && !other.isLocal())
            return 1;
        if (!isLocal() && other.isLocal())
            return -1;
        return friendlyName.compareTo(other.friendlyName);
    }


    // static utilities

    public static deviceGroup groupOf(deviceType type)
    {
        deviceGroup group = deviceGroup.DEVICE_GROUP_NONE;
        if (type == deviceType.LocalLibrary ||
            type == deviceType.MediaServer)
            group = deviceGroup.DEVICE_GROUP_LIBRARY;
        else if (
            type == deviceType.LocalRenderer ||
                type == deviceType.MediaServer.MediaRenderer ||
                type == deviceType.MediaServer.OpenHomeRenderer)
            group = deviceGroup.DEVICE_GROUP_RENDERER;
        else if (type == deviceType.LocalPlaylistSource)
            group = deviceGroup.DEVICE_GROUP_PLAYLIST_SOURCE;
        return group;
    }


    //-------------------------------------------
    // Constructors
    //-------------------------------------------

    public Device(Artisan ma, SSDPSearchDevice ssdp_device)
    {
        artisan = ma;
        friendlyName = ssdp_device.getFriendlyName();
        device_group = ssdp_device.getDeviceGroup();
        device_uuid = ssdp_device.getDeviceUUID();
        device_urn = ssdp_device.getDeviceUrn();
        device_type = ssdp_device.getDeviceType();
        device_url = ssdp_device.getDeviceUrl();
        icon_path = ssdp_device.getIconPath();

        if (!icon_path.isEmpty() && !icon_path.startsWith("/"))
            icon_path = "/" + icon_path;
        Utils.log(dbg_d,3,"new Device(" + device_type + "," + friendlyName + ") at " + device_url);
    }

    // To and From String

    protected Device(Artisan ma)
    {
        artisan = ma;
    }

    public String toString()
    {
        String retval =
            friendlyName + "\t" +
            device_type.toString() + "\t" +
            device_group.toString() + "\t" +
            device_uuid + "\t" +
            device_urn + "\t" +
            device_url + "\t" +
            icon_path + "\t" +
            services.size() + "\t";
        String service_part = "";
        for (Service service : services.values())
        {
            retval += service.getServiceType() + "\t";
            service_part += service.toString() + "\n";
        }
        return retval + "\n" + service_part;
    }


    protected boolean fromString(StringBuffer buffer)
    {
        StringBuffer device_part = Utils.readBufferLine(buffer);
        friendlyName = Utils.pullTabPart(device_part);
        String s = Utils.pullTabPart(device_part);

        try
        {
            device_type = deviceType.valueOf(s);
        }
        catch (Exception e)
        {
            Utils.error("Illegal device type: " + s);
            return false;
        }

        s = Utils.pullTabPart(device_part);

        try
        {
            device_group = deviceGroup.valueOf(s);
        }
        catch (Exception e)
        {
            Utils.error("Illegal device group: " + s);
            return false;
        }
        device_uuid = Utils.pullTabPart(device_part);
        device_urn =  Utils.pullTabPart(device_part);
        device_url = Utils.pullTabPart(device_part);
        icon_path = Utils.pullTabPart(device_part);

        int num_services = Utils.parseInt(Utils.pullTabPart(device_part));

        for (int i = 0; i < num_services; i++)
        {
            Service service = null;
            Service.serviceType service_type =
                Service.serviceType.valueOf(Utils.pullTabPart(device_part));

            if (service_type == Service.serviceType.ContentDirectory)
                service = new ContentDirectory(artisan,this);
            else if (service_type == Service.serviceType.AVTransport)
                service = new AVTransport(artisan,this);
            else if (service_type == Service.serviceType.RenderingControl)
                service = new RenderingControl(artisan,this);
            else if (service_type == Service.serviceType.OpenProduct)
                service = new OpenProduct(artisan,this);
            else if (service_type == Service.serviceType.OpenPlaylist)
                service = new OpenPlaylist(artisan,this);
            else if (service_type == Service.serviceType.OpenInfo)
                service = new OpenInfo(artisan,this);
            else if (service_type == Service.serviceType.OpenTime)
                service = new OpenTime(artisan,this);
            else if (service_type == Service.serviceType.OpenVolume)
                service = new OpenVolume(artisan,this);

            if (service == null)
            {
                Utils.error("Unknown service type: '" + service_type + "'");
                return false;
            }

            if (!service.fromString(Utils.readBufferLine(buffer)))
            {
                Utils.error("service(" + service_type + ").fromString() failed!");
                return false;
            }

            services.put(service_type,service);
        }

        // Device.fromString() aok

        return true;
    }



    //-------------------------------------------
    // SSDPDevice Construction
    //-------------------------------------------
    // Called from DeviceManager during createDevice() callback
    // from SSDPSearch after validating sufficient services, etc,
    // this creates the services from an SSDP_Device

    public boolean createSSDPServices(SSDPSearchDevice ssdp_device)
    {
        SSDPSearchDevice.ServiceHash ssdp_services = ssdp_device.getServices();
        for (SSDPSearchService ssdp_service : ssdp_services.values())
        {
            Service service = null;
            Service.serviceType service_type = ssdp_service.getServiceType();
            Utils.log(dbg_d,4,"Creating " + service_type + " Service on " + friendlyName);

            if (service_type == Service.serviceType.ContentDirectory)
                service = new ContentDirectory(artisan,this,ssdp_service);
            else if (service_type == Service.serviceType.AVTransport)
                service = new AVTransport(artisan,this,ssdp_service);
            else if (service_type == Service.serviceType.RenderingControl)
                service = new RenderingControl(artisan,this,ssdp_service);
            else if (service_type == Service.serviceType.OpenProduct)
                service = new OpenProduct(artisan,this,ssdp_service);
            else if (service_type == Service.serviceType.OpenPlaylist)
                service = new OpenPlaylist(artisan,this,ssdp_service);
            else if (service_type == Service.serviceType.OpenInfo)
                service = new OpenInfo(artisan,this,ssdp_service);
            else if (service_type == Service.serviceType.OpenVolume)
                service = new OpenVolume(artisan,this,ssdp_service);
            else if (service_type == Service.serviceType.OpenTime)
                service = new OpenTime(artisan,this,ssdp_service);

            if (service == null)
            {
                Utils.error("No Creatable Service called '" + service_type + "'");
                return false;
            }

            services.put(service_type,service);

        }   // for each service

        return true;


    }    // createSSDPServices()



    //-----------------------------------------------------------
    // doAction() - do an SSDP Action on a given service
    //-----------------------------------------------------------

    static int dbg_da = 1;


    public Document doAction(Service.serviceType service_type, String action, stringHash args)
    {
        // make sure it's a valid service

        synchronized (this)
        {
            Service service_object = getServices().get(service_type);
            if (service_object == null)
            {
                Utils.error("Could not find service for " + getFriendlyName());
                return null;
            }
            String url = service_object.getControlUrl();

            // build the SOAP xml reply
            // Note that we remove "Open" from "OpenProduct", "OpenInfo", etc

            String service_string = service_type.toString();
            service_string = service_string.replaceAll("^Open","");
            String xml = getSoapBody(device_urn,service_string,action,args);

            // create the thread and start the request

            stringHash headers = new stringHash();
            headers.put("soapaction","\"urn:" + device_urn + ":service:" + service_string + ":1#" + action + "\"");

            networkRequest request = new networkRequest("text/xml",null,null,url,headers,xml);
            Thread request_thread = new Thread(request);
            request_thread.start();
            request.wait_for_result();

            if (request.the_result == null)
                Utils.warning(0,0,"doAction(" + service_string + "," + action + ") failed: " + request.error_reason);

            return (Document) request.the_result;
        }
    }


    private static String getSoapBody(String urn,String service,String action,stringHash args)
    {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n";
        xml += "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n";
        xml += "<s:Body>\r\n";
        xml += "<u:" + action + " xmlns:u=\"urn:" + urn + ":service:" + service + ":1\">\r\n";

        if (args != null)
        {
            for (String key : args.keySet())
            {
                String value = args.get(key);
                xml += "<" + key + ">" + value + "</" + key + ">\r\n";
            }
        }
        xml += "</u:" + action + ">\r\n";
        xml += "</s:Body>\r\n";
        xml += "</s:Envelope>\r\n";
        return xml;
    }



}   // class Device


