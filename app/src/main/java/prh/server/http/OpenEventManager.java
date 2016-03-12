//---------------------------------------------------------
// OpenEventManager - manages SUBSCRIPTIONS and EVENTS
//---------------------------------------------------------

package prh.server.http;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import prh.artisan.Artisan;
import prh.server.HTTPServer;
import prh.utils.Utils;

public class OpenEventManager
{
    private static int dbg_event = 0;
    private static int dbg_subscribe = 1;
    private static String DEBUG_HANDLER_EVENT = ""; // "Playlist";
    private static String DEBUG_HANDLER_REPLY = ""; // "Playlist";
        // Set these to the name of a handler and the
        // contents of the event or reply will be shown at
        // debug level 0

    private LinkedList<OpenEventSubscriber> clients = new LinkedList<OpenEventSubscriber>();
    private HashMap<String,OpenEventSubscriber> clients_by_ssid = new HashMap<String,OpenEventSubscriber>();
    static boolean in_thread = false;

    Artisan artisan;
    HTTPServer http_server;

    public OpenEventManager(Artisan ma, HTTPServer http)
    {
        artisan = ma;
        http_server = http;
    }


    public OpenEventSubscriber subscribe(
        OpenEventHandler handler,
        String event_url,
        String user_agent )
    {
        Utils.log(dbg_subscribe,0,"Subscribe(" + handler.eventHandlerName() + ") " + Utils.ipFromUrl(event_url) + ":" + Utils.portFromUrl(event_url) + " " + user_agent);
        Utils.log(dbg_subscribe+2,1,"url=" + event_url);

        OpenEventSubscriber subscriber = new OpenEventSubscriber(
            handler,
            event_url,
            user_agent);

        Utils.log(dbg_subscribe+2,1,"got sid=" + subscriber.getSid());

        clients.add(subscriber);
        clients_by_ssid.put(subscriber.getSid(),subscriber);
        return subscriber;
    }


    public OpenEventSubscriber unsubscribe(String sid)
    {
        Utils.log(dbg_subscribe,0,"Unsubscribe(" + sid + ")");
        OpenEventSubscriber subscriber = clients_by_ssid.get(sid);
        if (subscriber == null)
        {
            Utils.error("No subscriber found for UNSUBSCRIBE(" + sid + ")");
        }
        else
        {
            remove(subscriber);
        }
        return subscriber;
    }


    public OpenEventSubscriber refresh(String sid)
    {
        Utils.log(dbg_subscribe,0,"Refresh(" + sid + ")");
        OpenEventSubscriber subscriber = clients_by_ssid.get(sid);
        if (subscriber == null)
        {
            Utils.error("unknown subscriber(" + sid + ") in Refresh");
        }
        else
        {
            subscriber.refresh();
        }
        return subscriber;
    }


    public void remove(OpenEventSubscriber subscriber)
    {
        clients_by_ssid.remove(subscriber.getSid());
        clients.remove(subscriber);
    }



    public void send_events()
        // called whenever the renderer client calls incUpdateCount(service)
        // could be optimized to just look at the particular service.
        // checks all subscribers to see if they are out of date wrt the service
        // and sends events to them if so.
    {
        Utils.log(dbg_event+2,0,"OpenEventManager.send_events()");
        ArrayList<OpenEventSubscriber> expired = new ArrayList<OpenEventSubscriber>();
        for (int i=0; i<clients.size(); i++)
        {
            Utils.log(dbg_event+2,0,"checking client(" + i + ")");
            OpenEventSubscriber subscriber = clients.get(i);
            OpenEventHandler handler = subscriber.getHandler();
            Utils.log(dbg_event+2,1,"Checking subscriber " + handler.eventHandlerName() + " : " + subscriber.getUserAgent());
            Utils.log(dbg_event+2,2,"update_count(" + handler.eventHandlerName() + ")  subscriber=" + subscriber.getUpdateCount() + "     handler=" + handler.getUpdateCount());

            if (subscriber.expired())
            {
                Utils.log(dbg_event+2,2,"expired");
                expired.add(subscriber);
            }
            else if (subscriber.getUpdateCount() != handler.getUpdateCount())
            {
                Utils.log(dbg_event+1,2,"event needs sending ... ");
                Utils.log(dbg_event+1,3,"subscriber=" + subscriber.getUpdateCount() + "     handler=" + handler.getUpdateCount());
                subscriber.setUpdateCount(handler.getUpdateCount());
                String content = handler.getEventContent();

                // SEND THE EVENT

                asyncNOTIFY send = new asyncNOTIFY(subscriber,content);
                send.start();
            }
        }

        // remove expired subscribers

        for (int i=0; i<expired.size(); i++)
        {
            OpenEventSubscriber subscriber = clients.get(i);
            Utils.log(dbg_event,0,"Expiring(" + subscriber.getHandler().eventHandlerName() + ") " + subscriber.getSid() + " " + subscriber.getIp() + ":" + subscriber.getPort() + " " + subscriber.getUserAgent());
            remove(subscriber);
        }

    }



    //------------------------------------------
    // asynchronous HTTP Request processor
    //------------------------------------------

    public class asyncNOTIFY extends Thread
        // fire and forget
    {
        String m_content;
        OpenEventSubscriber m_subscriber;


        private asyncNOTIFY(OpenEventSubscriber subscriber,String content)
        // Contruct this and call start() to send the notify
        {
            Utils.log(dbg_event + 3,0,"asyncNOTIFY() ctor called");
            m_subscriber = subscriber;
            m_content = content;
        }


        public void run()
        // called from superclass start()
        {
            synchronized (http_server)
            {

                //while (in_thread) Utils.sleep(1000);
                //in_thread = true;

                Utils.log(dbg_event,1,"--> asyncNOTIFY(" + m_subscriber.getHandler().eventHandlerName() + ") to " + m_subscriber.getIp() + ":" + m_subscriber.getPort() + " " + m_subscriber.getUserAgent());
                try
                {
                    int show_dbg = 1;
                    String handler_name = m_subscriber.getHandler().eventHandlerName();

                    String text = createEventMessage();
                    Utils.log(dbg_event + show_dbg,2,"SENDING MESSAGE\n" + text);
                    Socket sock = new Socket(m_subscriber.getIp(),Utils.parseInt(m_subscriber.getPort()));
                    OutputStreamWriter osw = new OutputStreamWriter(sock.getOutputStream(),"UTF-8");
                    osw.write(text);
                    osw.flush();

                    InputStream i_stream = new BufferedInputStream(sock.getInputStream());
                    StringBuilder sb = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(i_stream,"UTF-8"));

                    int use_level = dbg_event + 1;
                    if (handler_name.equals(DEBUG_HANDLER_REPLY))
                        use_level = 0;

                    String line;
                    Utils.log(use_level,2,"READING REPLY");
                    line = reader.readLine();
                    if (line == null)
                    {
                        Utils.error("No reply to " + handler_name + " event");
                    }
                    else
                    {
                        Utils.log(use_level,3,line);
                        if (!line.contains("200"))
                        {
                            Utils.error("Bad reply to " + handler_name + " event: " + line);
                        }
                    }
                    while ((line = reader.readLine()) != null)
                    {
                        Utils.log(use_level,3,line);
                    }
                    sock.close();
                }
                catch (Exception e)
                {
                    Utils.error("Error Sending asyncNOTIFYt to " + m_subscriber.getUrl());
                }

                // in_thread = false;
            }

        }   // AsyncHTTPRequest::run()



        String createEventMessage()
            // Create the NOTIFY wrapper around some xml content
            // that represents an SSDP UPnP "EVENT" notification
            // as it's own HTTP method. Bumps the subscriber's
            // next_event (SEQ) number ....
        {
            String full_content =
                "\r\n" +
                "\r\n" +
                "<?xml version=\"1.0\"  encoding=\"utf-8\" standalone=\"yes\"?>\r\n" +
                "<e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">\r\n" +
                m_content +
                "</e:propertyset>\r\n" +
                "\r\n" +
                "\r\n";

            //full_content = full_content.replaceAll("\\n","");
            //full_content = full_content.replaceAll("\\r","");

            String url_path = m_subscriber.getUrl().replace("http://" + m_subscriber.getIp() + ":" + m_subscriber.getPort(),"");
            String msg = "NOTIFY " + url_path + " HTTP/1.1\r\n";
            msg += "HOST: " + m_subscriber.getIp() + ":" + m_subscriber.getPort() + "\r\n";
            msg += "CONTENT-TYPE: text/xml\r\n";
            msg += "connection: close\r\n";
            msg += "USER-AGENT: Android/4.4.2 UPnP/1.0 product/version\r\n";
            msg += "NT: upnp:event\r\n";
            msg += "NTS: upnp:propchange\r\n";
            msg += "SID: uuid:" + m_subscriber.getSid() + "\r\n";
            msg += "SEQ: " + m_subscriber.incEventNum() + "\r\n";
            msg += "remote-addr: " + Utils.server_ip + "\r\n";
            msg += "http-client-ip: " + Utils.server_ip + "\r\n";
            msg += "CONTENT-LENGTH: " + full_content.length() + "\r\n";
            msg += "\r\n";

            msg += full_content;

            if (m_subscriber.getHandler().eventHandlerName().equals(DEBUG_HANDLER_EVENT))
                Utils.log(0,0,"EVENT MESSAGE\n" + msg);
            return msg;
        }

    }   // class asyncNOTIFY



}   // class OpenEventManager
