package prh.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;
import java.util.Enumeration;
import java.util.Collections;


import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import prh.artisan.Artisan;
import prh.utils.DlnaUtils;
import prh.utils.Utils;


public class SSDPServer implements Runnable
{

    public static int dbg_ssdp = 1;

    // There are three root devices.

    public static int IDX_DLNA_SERVER = 0;
    public static int IDX_DLNA_RENDERER = 1;
    public static int IDX_OPEN_HOME = 2;

    // configuration constants

    private static boolean WITH_CONNECTION_MANAGER = false;
        // trying to get open_home working
    private static int HIGH_DEV = IDX_OPEN_HOME;
        // set to 1 to include DLNA Renderer
    private static int ALIVE_BROADCAST_INTERVAL = 15000;

    // The uuid is a based on the hex representation of
    // the UNIQUE Build.ID, with my device "type" tacked on the end

    private static String uuid_root = getUUIDRoot();
    private static String getUUIDRoot()
    {
        String hex_string = Utils.hex_encode(Build.ID);
        while (hex_string.length()<20) hex_string += "0";
        String uuid_root = hex_string.substring(0,8) + "-";
        uuid_root += hex_string.substring(8,12) + "-";
        uuid_root += hex_string.substring(12,16) + "-";
        uuid_root += hex_string.substring(16,20);
        return uuid_root;
    }

    // names of device description files

    private static String[] deviceType = {
        "MediaServer",
        "MediaRenderer",
        "OpenHome"
    };

    // The MediaServer is a "feed"
    // the MediaRnderer is a "face". and
    // the OpenHome server is a bace.

    public static String[] dlna_uuid = {
        uuid_root + "-aaaaaaaafeed",
        uuid_root + "-aaaaaaaaface",
        uuid_root + "-aaaaaaaabace" };

    // All device description requests to the HTTP server
    // are of the form /device/DeviceType.xml, and all
    // service description requests are of the form
    // /service/ServiceType1.xml

    private static String[][] dlna_types =
    {
        {   // dlna server types
            "uuid:" + dlna_uuid[IDX_DLNA_SERVER],
            "upnp:rootdevice",
            "urn:schemas-upnp-org:device:MediaServer:1",
            "urn:schemas-upnp-org:service:ContentDirectory:1",
            WITH_CONNECTION_MANAGER ? "urn:schemas-upnp-org:service:ConnectionManager:1" : null,
        },
        {   // dlna renderer types
            "uuid:" + dlna_uuid[IDX_DLNA_RENDERER],
            "upnp:rootdevice",
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "urn:schemas-upnp-org:service:RenderingControl:1",
            "urn:schemas-upnp-org:service:AVTransport:1",
            WITH_CONNECTION_MANAGER ? "urn:schemas-upnp-org:service:ConnectionManager:1" : null,
        },
        {   // open_home types (all return same device description file)
            "uuid:" + dlna_uuid[IDX_OPEN_HOME],
            "upnp:rootdevice",
            "urn:linn-co-uk:device:Source:1",
            "urn:av-openhome-org:service:Product:1",
            "urn:av-openhome-org:service:Volume:1",
            "urn:av-openhome-org:service:Time:1",
            "urn:av-openhome-org:service:Info:1",
            "urn:av-openhome-org:service:Playlist:1",
        }
    };


    public static int ssdp_port = 1900;
    public static String ssdp_ip = "239.255.255.250";
    public static SocketAddress mMulticastGroupAddress = new InetSocketAddress(ssdp_ip, ssdp_port);
    public static NetworkInterface mNetIf = findNetworkInterface();


    // VARIABLES

    private int running = 0;   // 0=stopped, 1=running, 2=stopping
    public MulticastSocket mMulticastSocket = null;
    private DatagramSocket mUnicastSocket = null;
    Handler alive_loop = null;
    AliveSender alive_sender = null;
    private Context mContext;


    //-------------------------------------------------
    // construction, startup, etc
    //-------------------------------------------------

    public SSDPServer(Context ctx)
    {
        Utils.log(dbg_ssdp,0,"SSDPServer() ctor called");
        mContext = ctx;
        running = 0;
    }


    // @Override
    // public synchronized void start()
    // // client calls start() after construction
    // {
    //     Utils.log(dbg_ssdp,0,"SSDPServer::start() called");
    //     super.start();
    // }


    public synchronized void shutdown()
    {
        Utils.log(0,0,"shutting down ssdp_server ...");

        // signal the loop to stop

        if (running == 1)
            running = 2;

        if (alive_loop != null)
            alive_loop.removeCallbacks(alive_sender);
        alive_loop = null;
        alive_sender = null;

        if (mUnicastSocket != null)
        {
            send_byebye();
            mUnicastSocket.close();
            mUnicastSocket = null;
        }
        if (mMulticastSocket != null)
        {
            mMulticastSocket.close();
            mMulticastSocket = null;
        }

        while (running > 0)
        {
            Utils.log(0,1,"waiting for SSDP stopping=2");
            Utils.sleep(1000);
        }
        Utils.log(0,0,"ssdp_server shut down");
    }


    private class AliveSender implements Runnable
    {
        public void run()
        {
            Utils.log(dbg_ssdp, 0, "------ > send_alive");
            send_broadcast("alive");
            alive_loop.postDelayed(this,ALIVE_BROADCAST_INTERVAL);
        }
    }


    public void send_byebye()
    // send byebye messages twice as all ssdp_device_types
    {
        Utils.log(dbg_ssdp,0,"------> send_byebye");
        send_broadcast("bye_bye");
        send_broadcast("bye_bye");
    }



    //------------------------------------------------
    // The listener loop
    //------------------------------------------------

    @Override
    public void run()
    {
        Utils.log(dbg_ssdp,0,"SSDPServer::run() called");

        try
        {
            mMulticastSocket = new MulticastSocket(ssdp_port);
            mMulticastSocket.setReuseAddress(true);
            mMulticastSocket.setLoopbackMode(true);
            mMulticastSocket.joinGroup(mMulticastGroupAddress,mNetIf);
            Utils.log(dbg_ssdp,1,"created ssdp multicast socket");

            mUnicastSocket = new DatagramSocket(null);
            mUnicastSocket.setReuseAddress(true);
            mUnicastSocket.bind(new InetSocketAddress(Utils.server_ip,0));
            Utils.log(dbg_ssdp,1,"created ssdp unicast socket");
        }
        catch (IOException e)
        {
            Utils.error("Setup SSDP failed:" + e);
            return;
        }

        running = 1;

        // initialization - send bye bye and start alive_thread
        // WDTVLive is fucked up ...
        // ... we never send bye-bye, cuz it ALWAYS takes us offline, even if we come back up before doing anything on the TV
        // ... we only broadcast an alive to start with.  Thereafter, it kills the TV ...

        // send_byebye();

        if (true)   // set to false to not start alive_thread
        {
            Utils.log(dbg_ssdp,1,"starting alive thread");
            Looper.prepare();
            alive_sender = new AliveSender();
            alive_loop = new Handler();
            alive_loop.postDelayed(alive_sender,ALIVE_BROADCAST_INTERVAL);
        }
        else
        {
            send_broadcast("alive");
        }

        //-------------------------
        // the loop
        //-------------------------
        // will fail calls if mMulticastSocket goes to null or is closed
        // otherwise, we check stopping in the loop

        Utils.log(dbg_ssdp,1,"running ...");
        while (running == 1 && mMulticastSocket != null)
        {
            try
            {
                byte[] buf = new byte[1024];
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                mMulticastSocket.receive(dp);

                String startLine = parseStartLine(dp);
                if (startLine.equals("M-SEARCH * HTTP/1.1"))
                {
                    String man = parseHeaderValue(dp, "MAN");

                    if (man==null || !man.equals("\"ssdp:discover\""))
                    {
                        if (man == null) man = "null";
                        Utils.log(dbg_ssdp,0,"M_SEARCH(" + dp.getAddress() + ":" + dp.getPort() + ") Skipping non ssdp:discover(" + man + ")");
                    }
                    else
                    {
                        String st = parseHeaderValue(dp, "ST");
                        String mx = parseHeaderValue(dp, "MX");

                        if (mx == null) mx = "3";
                        int int_mx = Utils.parseInt(mx);
                        if (int_mx > 3 || int_mx < 0) int_mx = 3;
                        Random rand = new Random();
                        Utils.log(dbg_ssdp + 1,0,"M_SEARCH(" + dp.getAddress() + ":" + dp.getPort() + ") st = " + st);

                        for (int dev_idx=0; running==1 && dev_idx <= HIGH_DEV; dev_idx++)
                        {
                            String[] ssdp_types = dlna_types[dev_idx];
                            for (String ssdp_type: ssdp_types)
                            {
                                if (ssdp_type != null && (st.equals("ssdp:all") || st.equals(ssdp_type)))
                                {
                                    Utils.log(dbg_ssdp,1,"M_SEARCH_REPLY(" +
                                            deviceType[dev_idx] + ") to " +
                                            dp.getAddress() + ":" + dp.getPort() + " " +
                                            ssdp_type);
                                    String msg = create_search_message(dev_idx,ssdp_type);
                                    DatagramPacket response = new DatagramPacket(
                                            msg.getBytes(),
                                            msg.length(),
                                            new InetSocketAddress(dp.getAddress(),dp.getPort()));

                                    // Utils.sleep(rand.nextInt(1000 * int_mx));
                                    mUnicastSocket.send(response);
                                    Utils.log(dbg_ssdp + 1,1,"M_SEARCH_REPLY response sent");
                                    Utils.log(dbg_ssdp + 1,2,msg);
                                }
                            }
                        }
                    }
                }
                else if (false)
                {
                    Utils.log(0,0,"NOT M_SEARCH: " + dp.getAddress() + ":" + dp.getPort() + " = " + new String(dp.getData()));
                }
            }
            catch (Exception e)
            {
                if (running == 1)
                {
                    Utils.error("SSDP fail." + e);
                }
            }
        }
        Utils.log(dbg_ssdp,1,"SSDPServer::run() finished");
        running = 0;
    }




    //-------------------------------------------------
    // Parser utilities (only used to get ST out of M_SEARCH messages)!!
    //-------------------------------------------------

    private String parseHeaderValue(String content, String headerName) {
        Scanner s = new Scanner(content);
        s.nextLine(); // Skip the start line

        while (s.hasNextLine())
        {
            String line = s.nextLine();
            int index = line.indexOf(':');
            String header = line.substring(0, index);
            if (headerName.equalsIgnoreCase(header.trim()))
                return line.substring(index + 1).trim();
        }

        return null;
    }

    private String parseHeaderValue(DatagramPacket dp, String headerName) {
        return parseHeaderValue(new String(dp.getData()), headerName);
    }

    private String parseStartLine(String content) {
        Scanner s = new Scanner(content);
        return s.nextLine();
    }

    private String parseStartLine(DatagramPacket dp) {
        return parseStartLine(new String(dp.getData()));
    }



    //-------------------------------------------------
    // Utilities
    //-------------------------------------------------

    private static NetworkInterface findNetworkInterface()
    // find the network interface based on the ip_address
    {
        NetworkInterface retval = null;
        Utils.log(dbg_ssdp+1,0,"findNetworkInterface()");
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                Utils.log(dbg_ssdp+2,1,"Display name: " + netint.getDisplayName());
                Utils.log(dbg_ssdp+2,2,"Name: " + netint.getName());
                Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                    Utils.log(dbg_ssdp+2,2,"InetAddress: " + inetAddress);
                    if (inetAddress.toString().equals("/" + Utils.server_ip)) {
                        Utils.log(dbg_ssdp+1,2,"FOUND " + inetAddress.toString() + "!!");
                        retval = netint;
                    }
                }
            }
        }
        catch (Exception e)
        {
            Utils.error("Exception in findNetworkInterface:" + e);
        }
        return retval;
    }


    static String http_date()
    // having the correct weird unix format was important
    // to the WDTV Live ... otherwise it would keep losing
    // the server ...
    {
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("ccc dd MMM yyyy HH:mm:ss z");
        // format = "Tue 21 Dec 2015 11:12:13 GMT
        String retval = sdf.format(date);
        Utils.log(dbg_ssdp+2,0,"http_date() returning '" + retval + "'");
        return retval;
    }


    String generate_usn(int dev_idx,String ssdp_type)
    {
        String usn = "uuid:" + dlna_uuid[dev_idx];
        if (!ssdp_type.equals(usn))
            usn = usn + "::" + ssdp_type;
        return usn;
    }

    String device_desc_url(int dev_idx)
    {
        String device_type = deviceType[dev_idx];
        return "http://" + Utils.server_ip + ":" + Utils.server_port + "/device/" + device_type + ".xml";
    }


    String create_search_message(int dev_idx, String ssdp_type)
    {
        return "HTTP/1.1 200 OK\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "LOCATION: " + device_desc_url(dev_idx) + "\r\n" +
            "SERVER: prh UPnP/1.0 " + Utils.programName + "\r\n" +
            "EXT:\r\n" +
            "ST: " + ssdp_type + "\r\n" +
            "USN: " + generate_usn(dev_idx,ssdp_type) + "\r\n" +
            "DATE: " + http_date() + "\r\n" +  // !! important to WDTVLive
            // "HOST: " + ssdp_ip + ":" + ssdp_port + "\r\n" +
            "\r\n";
    }

    String create_notify_message(int dev_idx, String action, String ssdp_type)
            // action id 'byebye' or 'alive'
    {
        String msg = "NOTIFY * HTTP/1.1\r\n";
        msg += "HOST: " + ssdp_ip + ":" + ssdp_port + "\r\n";
        if (action.equals("alive"))
        {
            msg += "CACHE-CONTROL: max-age=1800\r\n";
            msg += "DATE: " + http_date() + "\r\n";  // !! important to WDTVLive
            msg += "LOCATION: " + device_desc_url(dev_idx) + "\r\n";
        }
        msg += "NT: " + ssdp_type + "\r\n";
        msg += "NTS: ssdp:" + action + "\r\n";
        if (action.equals("alive"))
            msg += "SERVER: prh UPnP/1.0 " + Utils.programName + "\r\n";
        msg += "USN: " + generate_usn(dev_idx,ssdp_type) + "\r\n";
        msg += "\r\n";
        return msg;
    }


    public void send_broadcast(String action)
    {
        for (int dev_idx=0; dev_idx <= HIGH_DEV; dev_idx++)
        {
            String[] ssdp_types = dlna_types[dev_idx];
            for (String ssdp_type: ssdp_types)
            {
                if (ssdp_type != null)
                {
                    String msg = create_notify_message(dev_idx,action,ssdp_type);
                    Utils.log(dbg_ssdp + 2,2,"sending M_NOTIFY message=\n" + msg);

                    try
                    {
                        DatagramPacket response = new DatagramPacket(
                            msg.getBytes(),
                            msg.length(),
                            new InetSocketAddress(ssdp_ip,ssdp_port));
                        mMulticastSocket.send(response);
                    }
                    catch (Exception e)
                    {
                        // don't report errors if stopping

                        if (running == 1)
                        {
                            Utils.error("Could not send_broadcast:" + e);
                        }
                    }
                }
            }
        }
    }




    //-------------------------------------------
    // device descriptions
    //-------------------------------------------
    // see OpenHome.java for it's description


    public static String MediaRenderer_description()
    {
        String xml = "<?xml version=\"1.0\"?>\r\n" +
            "<root xmlns=\"urn:schemas-upnp-org:device-1-0\">\r\n" +
                "<specVersion>\r\n" +
                    "<major>1</major>\r\n" +
                    "<minor>5</minor>\r\n" +
                "</specVersion>\r\n" +
                "<device>\r\n" +
                    "<deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>\r\n" +
                    DlnaUtils.commonDeviceDescription("") +
                    "<UDN>uuid:" + dlna_uuid[IDX_DLNA_RENDERER] + "</UDN>\r\n" +
                    "<iconList>\r\n" +
                        "<icon>\r\n" +
                            "<mimetype>image/png</mimetype>\r\n" +
                            "<width>256</width>\r\n" +
                            "<height>256</height>\r\n" +
                            "<depth>24</depth>\r\n" +
                            "<url>/icons/artisan.png</url>\r\n" +
                        "</icon>\r\n" +
                    "</iconList>\r\n" +
                    "<serviceList>\r\n" +
                        "<service>\r\n" +
                            "<serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>\r\n" +
                            "<serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>\r\n" +
                            "<SCPDURL>/service/AVTransport1.xml</SCPDURL>\r\n" +
                            "<controlURL>/AVTransport/control</controlURL>\r\n" +
                            "<eventSubURL>/AVTransport/event</eventSubURL>\r\n" +
                        "</service>\r\n" +
                        "<service>\r\n" +
                            "<serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>\r\n" +
                            "<serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>\r\n" +
                            "<SCPDURL>/service/RenderingControl1.xml</SCPDURL>\r\n" +
                            "<controlURL>/RenderingControl/control</controlURL>\r\n" +
                            "<eventSubURL>/RenderingControl/event</eventSubURL>\r\n" +
                        "</service>\r\n";

        if (WITH_CONNECTION_MANAGER)
            xml = xml + "<service>\r\n" +
                            "<serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>\r\n" +
                            "<serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>\r\n" +
                            "<SCPDURL>/service/ConnectionManager1.xml</SCPDURL>\r\n" +
                            "<controlURL>/ConnectionManager/control</controlURL>\r\n" +
                            "<eventSubURL>/ConnectionManager/event</eventSubURL>\r\n" +
                        "</service>\r\n";

        xml = xml + "</serviceList>\r\n" +
                "</device>\r\n" +
                "<URLBase>" + Utils.server_uri + "</URLBase>\r\n" +
            "</root>\r\n\r\n";
        return xml;
    }



    public static String MediaServer_description()
    {
        String xml =
            "<?xml version=\"1.0\"?>\r\n" +
            "<root xmlns=\"urn:schemas-upnp-org:device-1-0\">\r\n" +
                "<specVersion>\r\n" +
                    "<major>1</major>\r\n" +
                    "<minor>5</minor>\r\n" +
                "</specVersion>\r\n" +
                "<device>\r\n" +
                    "<deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>\r\n" +
                    DlnaUtils.commonDeviceDescription("") +
                    "<UDN>uuid:" + dlna_uuid[IDX_DLNA_SERVER] + "</UDN>\r\n" +
                    "<iconList>\r\n" +
                        "<icon>\r\n" +
                            "<mimetype>image/png</mimetype>\r\n" +
                            "<width>256</width>\r\n" +
                            "<height>256</height>\r\n" +
                            "<depth>24</depth>\r\n" +
                            "<url>/icons/artisan.png</url>\r\n" +
                            "</icon>\r\n" +
                        "</iconList>\r\n" +
                    "<serviceList>\r\n" +
                        "<service>\r\n" +
                            "<serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>\r\n" +
                            "<serviceId>urn:upnp-org:serviceId:ContentDirectory</serviceId>\r\n" +
                            "<SCPDURL>/service/ContentDirectory1.xml</SCPDURL>\r\n" +
                            "<controlURL>/ContentDirectory/control</controlURL>\r\n" +
                            "<eventSubURL>/ContentDirectory/event</eventSubURL>\r\n" +
                        "</service>\r\n";

        if (WITH_CONNECTION_MANAGER)
            xml = xml + "<service>\r\n" +
                            "<serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>\r\n" +
                            "<serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>\r\n" +
                            "<SCPDURL>/service/ConnectionManager1.xml</SCPDURL>\r\n" +
                            "<controlURL>/ConnectionManager/control</controlURL>\r\n" +
                            "<eventSubURL>/ConnectionManager/event</eventSubURL>\r\n" +
                        "</service>\r\n";

        xml = xml + "</serviceList>\r\n" +
                "</device>\r\n" +
                "<URLBase>http://" + Utils.server_ip + ":" + Utils.server_port + "</URLBase>\r\n" +
            "</root>\r\n\r\n";
        return xml;
    }



    public static String OpenHome_description()
    {
        String xml =
            "<?xml version=\"1.0\"?>\r\n" +
            "<root xmlns=\"urn:schemas-upnp-org:device-1-0\">\r\n" +
            "<specVersion>\r\n" +
                "<major>1</major>\r\n" +
                "<minor>5</minor>\r\n" +
            "</specVersion>\r\n" +
            "<device>\r\n" +
                "<deviceType>urn:linn-co-uk:device:Source:1</deviceType>\r\n" +
                DlnaUtils.commonDeviceDescription(" (OpenHome)") +
                "<UDN>uuid:" + dlna_uuid[IDX_OPEN_HOME] + "</UDN>\r\n" +
                "<iconList>\r\n" +
                    "<icon>\r\n" +
                        "<mimetype>image/png</mimetype>\r\n" +
                        "<width>256</width>\r\n" +
                        "<height>256</height>\r\n" +
                        "<depth>24</depth>\r\n" +
                        "<url>/icons/artisan.png</url>\r\n" +
                    "</icon>\r\n" +
                "</iconList>\r\n" +
                "<serviceList>\r\n";

        // urn:av-openhome-org:service:  :1

        String services_names[] = {"Product","Volume","Time","Info","Playlist"};

        for (String service : services_names)
        {
            xml = xml +
                "<service>\r\n" +
                "<serviceType>urn:av-openhome-org:service:" + service + ":1</serviceType>\r\n" +
                "<serviceId>urn:av-openhome-org:service:" + service + "</serviceId>\r\n" +
                "<SCPDURL>/service/OpenHome_" + service + "1.xml</SCPDURL>\r\n" +
                "<controlURL>/" + service + "/control</controlURL>\r\n" +
                "<eventSubURL>/" + service + "/event</eventSubURL>\r\n" +
                "</service>\r\n";
        }

        xml = xml +
                    "</serviceList>\r\n" +
                "</device>\r\n" +
                "<URLBase>" + Utils.server_uri + "</URLBase>\r\n" +
            "</root>\r\n\r\n";

        return xml;
    }




}   // class SSDPServer