//---------------------------------------------------------
// UpnpEventManager - manages SUBSCRIPTIONS and EVENTS
//---------------------------------------------------------

package prh.server.utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.base.UpnpEventHandler;
import prh.server.HTTPServer;
import prh.utils.Utils;

public class UpnpEventManager
{
    private static int dbg_event = 0;
    private static int dbg_subscribe = 1;
    private static String DEBUG_HANDLER_EVENT = ""; // "Playlist";
    private static String DEBUG_HANDLER_REPLY = ""; // "Playlist";
        // Set these to the name of a handler and the
        // contents of the event or reply will be shown at
        // debug level 0

    Artisan artisan;
    HTTPServer http_server;

    private HashMap<String,UpnpEventHandler> handlers = new HashMap<String,UpnpEventHandler>();
    private HashMap<String,UpnpEventSubscriber> clients = new HashMap<String,UpnpEventSubscriber>();

    private class exposerHash extends HashMap<Integer,UpnpEventSubscriber> {}
    private exposerHash exposer_subscribers = new exposerHash();
    public exposerHash getExposerSubscribers() {return exposer_subscribers; }


    public UpnpEventManager(Artisan ma,HTTPServer http)
    {
        artisan = ma;
        http_server = http;
    }

    public void RegisterHandler(UpnpEventHandler handler)
    {
        handlers.put(handler.getName(),handler);
    }

    public void UnRegisterHandler(UpnpEventHandler handler)
    {
        // remove any clients that are subscribed to the service?
        handlers.remove(handler);
    }

    public UpnpEventHandler getHandler(String name)
    {
        return handlers.get(name);
    }


    public void incUpdateCount(String service_name)
    {
        UpnpEventHandler handler = getHandler(service_name);
        if (handler != null)
        {
            handler.incUpdateCount();
            Utils.log(9,0,"external incUpdateCount(" + service_name + ")=" + handler.getUpdateCount());
        }
        else
        {
            Utils.warning(0,0,"No handler for incUpdateCount(" + service_name + ")");
        }
    }


    // EVENT (SUBSCRIBE/UNSUBSCRIBE) requests
    // this logic will be common to all servers
    // and probably moved into the HTTPServer
    // at some point

    public NanoHTTPD.Response subscription_request_response(
        NanoHTTPD.IHTTPSession session,
        NanoHTTPD.Response response,
        String service)

    {
        UpnpEventSubscriber subscriber = null;
        String method = session.getMethod().toString();
        String sid = session.getHeaders().get("sid");
        String remote_ip = session.getHeaders().get("remote-addr");
        String user_agent = HTTPServer.getAnyUserAgent(session);
        Utils.log(dbg_subscribe,0,"OpenHome Event request(" + remote_ip + ")" + method + " " + service);

        if (method.equals("SUBSCRIBE"))
        {
            if (sid == null || sid.equals(""))  // new subscription
            {
                Utils.log(dbg_subscribe,1,"new subscription");
                String event_url;
                try
                {
                    event_url = session.getHeaders().get("callback");
                }
                catch (Exception e)
                {
                    Utils.error("Exception in SUBSCRIBE session.getHeaders: " + e.toString());
                    return response;
                }

                event_url = event_url.replaceAll("<","");
                event_url = event_url.replaceAll(">","");
                Utils.log(dbg_subscribe,2,"event_url=" + event_url);

                UpnpEventHandler handler = getHandler(service);
                if (handler == null)
                    Utils.warning(0,0,"Unknown service '" + service + "' in SUBSCRIBE");
                else
                    subscriber = subscribe(handler,event_url,user_agent);
            }
            else    // refresh subscription
            {
                sid = sid.replace("uuid:","");
                Utils.log(dbg_subscribe,1,"refresh subscription");
                subscriber = refresh(sid);
            }

            // send the subscription response
            // HTTP/1.1 200 OK
            // DATE: when response was generated
            // SERVER: OS/version UPnP/1.1 product/version
            // SID: uuid:subscription-UUID
            // CONTENT-LENGTH: 0
            // TIMEOUT: Second-actual subscription duration

            if (subscriber != null)
            {
                Utils.log(dbg_subscribe + 1,1,"returning SUBSCRIBE response");
                response = http_server.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,"text/plain","");
                response.addHeader("sid","uuid:" + subscriber.getSid());
                response.addHeader("timeout","Second-" + subscriber.getDuration());
                response.addHeader("content-length","0");
                // send the initial event (before returning from the subscribe request!!!
                send_events();
            }
        }
        else if (method.equals("UNSUBSCRIBE"))
        {
            if (sid == null || sid.equals(""))
                Utils.error("Missing sid in UNSUBSCRIBE");
            else
            {
                sid = sid.replace("uuid:","");
                unsubscribe(sid);
                response = http_server.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,"text/plain","");
            }
        }
        else
            Utils.error("Unsupported method " + method + " in SUBSCRIBE");

        Utils.log(dbg_subscribe + 1,1,method + " /open_home/event/" + service + " finished");
        return response;

    }   // EVENT



    public UpnpEventSubscriber subscribe(
        UpnpEventHandler handler,
        String event_url,
        String user_agent )
    {
        String name = handler.getName();
        Utils.log(dbg_subscribe,0,"Subscribe(" + name + ") " + Utils.ipFromUrl(event_url) + ":" + Utils.portFromUrl(event_url) + " " + user_agent);
        Utils.log(dbg_subscribe+2,1,"url=" + event_url);

        UpnpEventSubscriber subscriber = new UpnpEventSubscriber(
            handler,
            event_url,
            user_agent);

        Utils.log(dbg_subscribe + 2,1,"got sid=" + subscriber.getSid());
        clients.put(subscriber.getSid(),subscriber);
        handler.notifySubscribed(subscriber,true);
        return subscriber;
    }


    public UpnpEventSubscriber unsubscribe(String sid)
    {
        Utils.log(dbg_subscribe,0,"Unsubscribe(" + sid + ")");
        UpnpEventSubscriber subscriber = clients.get(sid);
        if (subscriber == null)
        {
            Utils.warning(0,0,"No subscriber found for UNSUBSCRIBE(" + sid + ")");
        }
        else
        {
            subscriber.getHandler().notifySubscribed(subscriber,false);
            remove(subscriber);
        }
        return subscriber;
    }


    public UpnpEventSubscriber refresh(String sid)
    {
        Utils.log(dbg_subscribe,0,"Refresh(" + sid + ")");
        UpnpEventSubscriber subscriber = clients.get(sid);
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


    public void remove(UpnpEventSubscriber subscriber)
    {
        clients.remove(subscriber.getSid());
    }



    public void send_events()
        // called whenever the renderer client calls incUpdateCount(service)
        // could be optimized to just look at the particular service.
        // checks all subscribers to see if they are out of date wrt the service
        // and sends events to them if so.
    {
        Utils.log(dbg_event+2,0,"UpnpEventManager.send_events()");
        ArrayList<UpnpEventSubscriber> expired = new ArrayList<UpnpEventSubscriber>();
        for (UpnpEventSubscriber subscriber:clients.values())
        {
            Utils.log(dbg_event+2,0,"checking client(" + subscriber.getUserAgent() + ")");
            UpnpEventHandler handler = subscriber.getHandler();
            Utils.log(dbg_event+2,1,"Checking subscriber " + handler.getName() + " : " + subscriber.getUserAgent());
            Utils.log(dbg_event+2,2,"update_count(" + handler.getName() + ")  subscriber=" + subscriber.getUpdateCount() + "     handler=" + handler.getUpdateCount());

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

                String content;

                // EVENT DATA REQUEST
                // The upnEventHandlers are synchronized with their
                // httpEventHandlers so that actions and events are atomic

                synchronized (handler)
                {
                    content = handler.getEventContent(subscriber);
                }

                // SEND THE EVENT

                asyncNOTIFY send = new asyncNOTIFY(subscriber,content);
                send.start();
            }
        }

        // remove expired subscribers

        for (UpnpEventSubscriber subscriber:expired)
        {
            Utils.log(dbg_event,0,"Expiring(" + subscriber.getHandler().getName() + ") " + subscriber.getSid() + " " + subscriber.getIp() + ":" + subscriber.getPort() + " " + subscriber.getUserAgent());
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
        UpnpEventSubscriber m_subscriber;


        private asyncNOTIFY(UpnpEventSubscriber subscriber,String content)
        // Contruct this and call start() to send the notify
        {
            Utils.log(dbg_event + 3,0,"asyncNOTIFY() ctor called");
            m_subscriber = subscriber;
            m_content = content;
        }


        public void run()
        // called from superclass start()
        {
            // the http_server may not (should not) be synchronized
            // the invividuaal http handlers are synchrnonized

            // synchronized (http_server)
            {
                boolean is_loop_call =  m_subscriber.getHandler().getName().equals("Time");
                if (!is_loop_call)
                    Utils.log(dbg_event,1,"--> asyncNOTIFY(" + m_subscriber.getHandler().getName() + ") to " + m_subscriber.getIp() + ":" + m_subscriber.getPort() + " " + m_subscriber.getUserAgent());
                try
                {
                    int show_dbg = 1;
                    String handler_name = m_subscriber.getHandler().getName();
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
            }

        }   // networkRequest::run()



        String createEventMessage()
            // Create the NOTIFY wrapper around some xml content
            // that represents an SSDP UPnP "EVENT" notification
            // as it's own HTTP method. Bumps the subscriber's
            // next_event (SEQ) number ....
        {
            String full_content =
                "<?xml version=\"1.0\"  encoding=\"utf-8\" standalone=\"yes\"?>\r\n" +
                "<e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">" + // "\r\n" +
                m_content +
                "</e:propertyset>\r\n";

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

            if (m_subscriber.getHandler().getName().equals(DEBUG_HANDLER_EVENT))
                Utils.log(0,0,"EVENT MESSAGE\n" + msg);
            return msg;
        }

    }   // class asyncNOTIFY


    //---------------------------------------------------------
    // support for EXPOSE_SCHEME
    //---------------------------------------------------------

    public UpnpEventSubscriber findOpenPlaylistSubscriber(String ip, String user_agent)
    {
        for (UpnpEventSubscriber subscriber : clients.values())
        {
            if (subscriber.getHandler().getName().equals("Playlist") &&
                subscriber.getIp().equals(ip) &&
                subscriber.getUserAgent().equals(user_agent))
            {
                return subscriber;
            }
        }
        return null;
    }



}   // class UpnpEventManager
