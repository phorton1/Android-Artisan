package prh.device;


import org.w3c.dom.Document;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilderFactory;

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
import prh.utils.networkRequest;
import prh.utils.stringHash;


public abstract class Device implements Comparable<Device>
    // A base classes for Devices
    // Should have UUID, but we just use friendlyName as unique id
{
    private static int dbg_d = 1;

    public static final String DEVICE_MEDIA_SERVER = "MediaServer";
    public static final String DEVICE_MEDIA_RENDERER = "MediaRenderer";
    public static final String DEVICE_OPEN_HOME = "OpenHomeRenderer";
        // actual ssdp device type is "Source"
        // and it goes in the list of MediaRenderers

    public static final String DEVICE_LOCAL_LIBRARY = "Local Library";
    public static final String DEVICE_LOCAL_RENDERER = "Local Renderer";
    public static final String DEVICE_REMOTE_LIBRARY = "Remote Library";
    public static final String DEVICE_REMOTE_RENDERER = "Remote Renderer";

    public static final String SERVICE_CONTENT_DIRECTORY = "ContentDirectory";
    public static final String SERVICE_AV_TRANSPORT = "AVTransport";
    public static final String SERVICE_RENDERING_CONTROL = "RenderingControl";
    public static final String SERVICE_OPEN_PRODUCT = "Product";
    public static final String SERVICE_OPEN_PLAYLIST = "Playlist";
    public static final String SERVICE_OPEN_INFO = "Info";
    public static final String SERVICE_OPEN_TIME = "Time";
    public static final String SERVICE_OPEN_VOLUME = "Volume";

    // member variables

    protected Artisan artisan;
    private String device_url;
    private String device_type;
    private String friendlyName;
    private String icon_url = "";
    private HashMap<String,Service> services = new HashMap<String,Service>();

    // accessors

    public String getDeviceUrl()    { return device_url; }
    public String getDeviceType()   { return device_type; }
    public String getFriendlyName() { return friendlyName; }
    public String getIconUrl()
    {
        if (icon_url.isEmpty())
            return "";
        return device_url + icon_url;
    }

    public HashMap<String,Service> getServices()
    {
        return services;
    }

    // Comparable interface

    public int compareTo(Device other)
    {
        return friendlyName.compareTo(other.friendlyName);
    }


    // public utilities

    public static String short_type(String what, String st)
        // utility to change an SSDP device or service type
        // i.e. urn:schemas-upnp-org:service:AVTransport:1
        // into one of our more simple names (i.e. AVTransport)
        // Can be called over again with no harm.
    {
        st = st.replaceAll("^.*" + what + ":","");
        st = st.replaceAll(":.*$","");
        return st;
    }

    public static String get_urn(String what, String st)
        // utility to get the urn from a device/service type
        // like urn:schemas-upnp-org:service:AVTransport:1
    {
        st = st.replaceAll("^.*urn:","");
        st = st.replaceAll(":" + what + ".*$","");
        return st;
    }


    // Constructor

    public Device(Artisan ma, String name, String short_type, String url, String icon)
    {
        artisan = ma;
        friendlyName = name;
        device_type = short_type;
        device_url = url;
        icon_url = icon;
        if (!icon_url.isEmpty() && !icon_url.startsWith("/"))
            icon_url = "/" + icon_url;
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
            device_type + "\t" +
            device_url + "\t" +
            icon_url + "\t" +
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
        device_type = Utils.pullTabPart(device_part);
        device_url = Utils.pullTabPart(device_part);
        icon_url = Utils.pullTabPart(device_part);

        int num_services = Utils.parseInt(Utils.pullTabPart(device_part));

        for (int i = 0; i < num_services; i++)
        {
            Service service = null;
            String service_type = Utils.pullTabPart(device_part);

            if (service_type.equals(SERVICE_CONTENT_DIRECTORY))
                service = new ContentDirectory(artisan,this);
            else if (service_type.equals(SERVICE_AV_TRANSPORT))
                service = new AVTransport(artisan,this);
            else if (service_type.equals(SERVICE_RENDERING_CONTROL))
                service = new RenderingControl(artisan,this);
            else if (service_type.equals(SERVICE_OPEN_PRODUCT))
                service = new OpenProduct(artisan,this);
            else if (service_type.equals(SERVICE_OPEN_PLAYLIST))
                service = new OpenPlaylist(artisan,this);
            else if (service_type.equals(SERVICE_OPEN_INFO))
                service = new OpenInfo(artisan,this);
            else if (service_type.equals(SERVICE_OPEN_TIME))
                service = new OpenTime(artisan,this);
            else if (service_type.equals(SERVICE_OPEN_VOLUME))
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



    // Service Management

    public void addService(Service service)
    {
        services.put(service.getFriendlyName(),service);
    }


    // Default Service Construction

    public boolean createSSDPServices(SSDPSearch.SSDPDevice ssdp_device)
    {
        HashMap<String,SSDPSearch.SSDPService> ssdp_services = ssdp_device.getServices();
        for (SSDPSearch.SSDPService ssdp_service : ssdp_services.values())
        {
            Service service = null;
            String urn = get_urn("service",ssdp_service.service_type);
            String service_type = short_type("service",ssdp_service.service_type);
            Utils.log(dbg_d,4,"Creating " + service_type + " Service on " + friendlyName);

            if (service_type.equals(SERVICE_CONTENT_DIRECTORY))
                service = new ContentDirectory(
                    artisan,
                    this,
                    urn,
                    service_type,
                    ssdp_service.control_url,
                    ssdp_service.event_url,
                    ssdp_service.service_doc);

            else if (service_type.equals(SERVICE_AV_TRANSPORT))
                service = new AVTransport(
                    artisan,
                    this,
                    urn,
                    service_type,
                    ssdp_service.control_url,
                    ssdp_service.event_url,
                    ssdp_service.service_doc);

            else if (service_type.equals(SERVICE_RENDERING_CONTROL))
                service = new RenderingControl(
                    artisan,
                    this,
                    urn,
                    service_type,
                    ssdp_service.control_url,
                    ssdp_service.event_url,
                    ssdp_service.service_doc);

            else if (service_type.equals(SERVICE_OPEN_PRODUCT))
                service = new OpenProduct(
                    artisan,
                    this,
                    urn,
                    service_type,
                    ssdp_service.control_url,
                    ssdp_service.event_url,
                    ssdp_service.service_doc);

            else if (service_type.equals(SERVICE_OPEN_PLAYLIST))
                service = new OpenPlaylist(
                    artisan,
                    this,
                    urn,
                    service_type,
                    ssdp_service.control_url,
                    ssdp_service.event_url,
                    ssdp_service.service_doc);

            else if (service_type.equals(SERVICE_OPEN_INFO))
                service = new OpenInfo(
                    artisan,
                    this,
                    urn,
                    service_type,
                    ssdp_service.control_url,
                    ssdp_service.event_url,
                    ssdp_service.service_doc);

            else if (service_type.equals(SERVICE_OPEN_TIME))
                service = new OpenTime(
                    artisan,
                    this,
                    urn,
                    service_type,
                    ssdp_service.control_url,
                    ssdp_service.event_url,
                    ssdp_service.service_doc);

            else if (service_type.equals(SERVICE_OPEN_VOLUME))
                service = new OpenVolume(
                    artisan,
                    this,
                    urn,
                    service_type,
                    ssdp_service.control_url,
                    ssdp_service.event_url,
                    ssdp_service.service_doc);

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
    // doAction()
    //-----------------------------------------------------------
    // Implementation moved to old_DoAction.java

    static int dbg_da = 1;


    public Document doAction(String service, String action, stringHash args)
    {
        // make sure it's a valid service

        synchronized (this)
        {
            Service service_object = getServices().get(service);
            if (service == null)
            {
                Utils.error("Could not find service for " + getFriendlyName());
                return null;
            }
            String urn = service_object.getUrn();
            String url = service_object.getControlUrl();

            // build the SOAP xml reply

            String xml = getSoapBody(urn,service,action,args);

            // create the thread and start the request

            stringHash headers = new stringHash();
            headers.put("soapaction","\"urn:" + urn + ":service:" + service + ":1#" + action + "\"");

            networkRequest request = new networkRequest("text/xml",null,null,url,headers,xml);
            Thread request_thread = new Thread(request);
            request_thread.start();
            request.wait_for_result();

            if (request.the_result == null)
                Utils.warning(0,0,"doAction(" + service + "," + action + ") failed: " + request.error_reason);

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


