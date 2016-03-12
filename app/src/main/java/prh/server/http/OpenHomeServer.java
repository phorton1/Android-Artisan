package prh.server.http;
// In general OpenHome actions are not re-entrant.
    //
    // Actions are atomic in nature, and it does not make sense,
    // can be complicated implementation-wise, if two actions
    // on the same service are running at the same time, i.e.
    // adding items to the Playlist.
    //
    // Also, many actions have the potential of generating
    // events, as they can modify the state of the services,
    //
    // In addition, we don't want to send events in the
    // middle of processing an action.
    //
    // Therefore action handling and event processing
    // have re-entrancy protection, and with regards to
    // those operations, the server is syncrhonous.
    //
    // Having said that, all a particular service needs to
    // to work within the scheme is to increment it's own
    // "update_count" if it modifies the state, and the
    // appropriate events will be sent.
    //
    // The rest of the OpenHomeServer is asynchronous.

import org.w3c.dom.Document;


import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.server.HTTPServer;
import prh.server.SSDPServer;
import prh.server.httpRequestHandler;
import prh.utils.Utils;
import prh.utils.DlnaUtils;


public class OpenHomeServer extends httpRequestHandler
    // handles requests that start with /open_home/
    // which has already been removed from uri
{
    private static int dbg_control = 0;
    private static int dbg_control_time = 1;
        // a separate debug level for looping Time requests
    private static int dbg_subscribe = 1;

    private static String services_names[] = {"Product","Volume","Time","Info","Playlist"};

    private OpenEventManager open_event     = null;
    private OpenProduct      open_product   = null;
    private OpenPlaylist     open_playlist  = null;
    private OpenVolume       open_volume    = null;
    private OpenInfo         open_info      = null;
    private OpenTime         open_time      = null;

    private boolean inHomeServer = false;
    private Artisan artisan;
    private HTTPServer http_server;


    public OpenHomeServer(HTTPServer http, Artisan ma)
    {
        artisan = ma;
        http_server = http;
        open_product   = new OpenProduct(artisan,http_server);
        open_volume    = new OpenVolume(artisan,http_server);
        open_playlist  = new OpenPlaylist(artisan,http_server,this);
        open_info      = new OpenInfo(artisan,http_server);
        open_time      = new OpenTime(artisan,http_server);
        open_event     = new OpenEventManager(artisan,http_server);
    }

    public OpenEventHandler getEventHandler(String service_name)
    {
        if (service_name.equals("Product")) return open_product;
        if (service_name.equals("Playlist")) return open_playlist;
        if (service_name.equals("Volume")) return open_volume;
        if (service_name.equals("Info")) return open_info;
        if (service_name.equals("Time")) return open_time;
        return null;
    }

    public void incUpdateCount(String service_name)
    {
        OpenEventHandler handler = getEventHandler(service_name);
        handler.incUpdateCount();
        Utils.log(9,0,"external incUpdateCount(" + service_name + ")=" + handler.getUpdateCount());
    }

    public void sendEvents()
        // called from Renderer.updateState()
        // we do nothing if somebody else is in home server
        // and wait for a call with a clear semaphore.
    {
        synchronized (this)
        {
            open_event.send_events();
        }
    }



    @Override
    public NanoHTTPD.Response response(
        NanoHTTPD.IHTTPSession session,
        NanoHTTPD.Response response,
        String uri,
        String urn,
        String service,
        String action,
        Document doc)
    {
        // Only handles actions, expects doc != null, and never looks at uri

        synchronized (this)     // events and actions must be synchronized
        {
            // CONTROL requests

            if (uri.equals("control"))
            {
                int use_dbg = service.equals("Time") ?
                    dbg_control_time :
                    dbg_control;
                Utils.log(use_dbg,1,"OpenHome(" + service + ") " + action + " request");

                if (service.equals("Product"))
                    response = open_product.productAction(response,doc,urn,service,action);
                if (service.equals("Playlist"))
                    response = open_playlist.playlistAction(response,doc,urn,service,action);
                if (service.equals("Info"))
                    response = open_info.infoAction(response,doc,urn,service,action);
                if (service.equals("Time"))
                    response = open_time.timeAction(response,doc,urn,service,action);
                if (service.equals("Volume"))
                    response = open_volume.volumeAction(response,doc,urn,service,action);
            }

            // EVENT (SUBSCRIBE/UNSUBSCRIBE) requests
            // this logic will be common to all servers
            // and probably moved into the HTTPServer
            // at some point

            else if (uri.equals("event"))
            {
                OpenEventSubscriber subscriber = null;
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

                        OpenEventHandler handler = getEventHandler(service);
                        if (handler == null)
                            Utils.error("Unknown service '" + service + "' in SUBSCRIBE");
                        else
                            subscriber = open_event.subscribe(handler,event_url,user_agent);
                    }
                    else    // refresh subscription
                    {
                        sid = sid.replace("uuid:","");
                        Utils.log(dbg_subscribe,1,"refresh subscription");
                        subscriber = open_event.refresh(sid);
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
                        open_event.send_events();
                    }
                }
                else if (method.equals("UNSUBSCRIBE"))
                {
                    if (sid == null || sid.equals(""))
                        Utils.error("Missing sid in UNSUBSCRIBE");
                    else
                    {
                        sid = sid.replace("uuid:","");
                        open_event.unsubscribe(sid);
                        response = http_server.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,"text/plain","");
                    }
                }
                else
                    Utils.error("Unsupported method " + method + " in SUBSCRIBE");

                Utils.log(dbg_subscribe + 1,1,method + " /open_home/event/" + service + " finished");

            }   // EVENT
        }   // synchronize

        return response;

    }   // OpenHomeServer.response()







}   // class OpenHomeServer
