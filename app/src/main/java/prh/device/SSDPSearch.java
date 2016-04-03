package prh.device;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;

import prh.artisan.Artisan;
import prh.server.SSDPServer;
import prh.utils.Utils;


public class SSDPSearch implements Runnable
    // Implements a runnable that performs an aynchronous SSDP Search
    // for interesting devices and services.
    //
    // Devices are uniquely identified by their TYPE and FRIENDLY_NAME.
    // NOTE - The unique ID should really be the device UUID
    //
    // As devices are found, it asks Artisan if it should proceed to get
    // to get the device_description, a costly operation, as Artisan maintains
    // a list of existing devices, and if told to do so, then requests the
    // device description in a separate thread, which then parses the , handles and validates all responses, and
    // eventually creates Devices that create Services and that notify
    // and a factory that creates derived Services
    // and notifies Artisan about them.
{
    private static int dbg_ssdp_search = 1;
    private static int LISTEN_PORT = 8070;
    private static int SEARCH_TIME = 4;

    private DeviceManager device_manager;

    private Artisan artisan;


    //------------------------------------------------------
    // constructor and utilities
    //------------------------------------------------------

    public SSDPSearch(DeviceManager dm, Artisan ma)
    {
        device_manager = dm;
        artisan = ma;
    }


    private static String ShortType(String what, String st)
        // utility to change an SSDP device or service type
        // i.e. urn:schemas-upnp-org:service:AVTransport:1
        // into one of our more simple names (i.e. AVTransport)
        // Can be called over again with no harm.
    {
        st = st.replaceAll("^.*" + what + ":","");
        st = st.replaceAll(":.*$","");
        return st;
    }


    private static String GetUrn(String st)
        // utility to get the urn from a long device type
        // like urn:schemas-upnp-org:device:MediaServer:1
    {
        st = st.replaceAll("^.*urn:","");
        st = st.replaceAll(":device.*$","");
        return st;
    }


    //------------------------------------------------------
    // run() - do the SSDPSearch
    //------------------------------------------------------

    public void run()
    {
        Utils.log(dbg_ssdp_search,0,"SSDPSearch started");
        device_manager.incDecBusy(1);
            // the normal incDecBusy for this level of the hierarchy

        // create a DeviceListener on LISTEN_PORT

        device_manager.incDecBusy(1);
            // we do the incDecBusy() ahead of the call
            // so as to ensure that it is always 1 when
            // leaving this routine.

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
        device_manager.incDecBusy(-1);
    }


    //-----------------------------------------------------------
    // SearchListener
    //-----------------------------------------------------------

    private class SSDPSearchListener implements Runnable
        // device_manager.incDecBusy(1) has already
        // been called for this level of the hirearchy
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
                        String device_usn = null;
                        // String device_st = null;

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
                                    if (lvalue.equalsIgnoreCase("usn"))
                                        device_usn = rvalue;
                                    //if (lvalue.equalsIgnoreCase("st"))
                                    //    device_st = rvalue;
                                }
                            }   // not the first line
                        }   // for each line in the reply


                        // call the device manager to do a deviceCheck()
                        // it handles incDecBusy there-below.

                        device_manager.notifyDeviceSSDP(location, device_usn, "alive");

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


            Utils.log(dbg_ssdp_search,0,"SSDPSearch.run() finished");
            device_manager.incDecBusy(-1);
                // decrement the call from SSDPSearch::run()

        }   // run()

    }   // class SSDPSearchListener

}   // class SSDPSearch



