// HTTP Server

// PRH BIG NOTE:  Bubble Caches the playlist for the OpenHomeRenderer
// which means that upon startup, it thinks it has a list which doesn't exist.
// IT *SHOULD* HAVE GOTTEN THE EMPTY PLAYLIST FROM THE SERVER


package prh.server;

import android.content.res.Resources;
import android.content.res.AssetManager;

import org.w3c.dom.Document;

import java.io.InputStream;
import java.io.IOException;
import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.artisan.Prefs;
import prh.device.OpenHomeRenderer;
import prh.server.http.AVTransport;
import prh.server.http.ContentDirectory;
import prh.server.http.OpenInfo;
import prh.server.http.OpenPlaylist;
import prh.server.http.OpenProduct;
import prh.server.http.OpenTime;
import prh.server.http.OpenVolume;
import prh.server.http.RenderingControl;
import prh.base.HttpRequestHandler;
import prh.base.UpnpEventHandler;
import prh.server.utils.UpnpEventManager;
import prh.server.utils.UpnpEventSubscriber;
import prh.utils.httpUtils;
import prh.utils.Utils;


public class HTTPServer extends fi.iki.elonen.NanoHTTPD
    // http server that dispatches requests to various
    // other "server" (handlers) including the DLNAServer.
    // It "just happens" to be created to listen on the
    // same ip address that we get in MainActivity.onCreate()
    // from the WiFi manager.
{
    private static int dbg_http = 0;
        // debug startup, construction, etc

    // specific debug settings for different request types

    private static int dbg_requests = 1;
    private static int dbg_favicon_requests = 1;
    private static int dbg_icon_requests = 1;
    private static int dbg_service_requests = 0;
    private static int dbg_device_requests = 0;
    private static int dbg_control_requests = 0;
    private static int dbg_event_requests = 0;
    private static int dbg_other_requests = 1;

    private static int dbg_looping_control_requests = 1;
        // show GetTransportInfo, GetPosition, and Time actions

    private Artisan artisan;
        // the instance keeps a Context
        // for getting resources

    private UpnpEventManager event_manager = null;
    public UpnpEventManager getEventManager() { return event_manager; }
    private HashMap<String,HttpRequestHandler> handlers = null;
    private OpenHomeRenderer open_home_renderer = null;


    public HttpRequestHandler getHandler(String service_name)
    {
        return handlers.get(service_name);
    }

    public void setOpenHomeRenderer(OpenHomeRenderer open_renderer)
    {
        open_home_renderer = open_renderer;
    }


    //-----------------------------------------------
    // start_http_server and the thread
    //-----------------------------------------------

    public static HTTPServerThread start_http_server(Artisan artisan)
        // this method called directly from MainActivity()
        // creates, starts(), and returns an HTTPServerThread
    {
        Utils.log(dbg_http,0,"starting http_server thread...");
        HTTPServerThread http_server = null;
        try
        {
            http_server = new HTTPServerThread(artisan);
            http_server.start();
            Utils.log(0,0,"http_server thread started");
        }
        catch (Exception e)
        {
            Utils.error("Exception creating HTTP Server thread: " + e);
        }
        return http_server;
    }



    public static class HTTPServerThread extends Thread
        // a class derived from Thread which creates and runs
        // the actual HTTP Server.
    {
        HTTPServer theServer = null;
        Artisan artisan;

        public HTTPServerThread(Artisan ma)
        {
            artisan = ma;
        }

        @Override
        public synchronized void start()
        // client calls start() after construction
        {
            Utils.log(dbg_http,1,"HTTPServerThread::starting ...");
            try
            {
                theServer = new HTTPServer(artisan);
                theServer.start();
                Utils.log(dbg_http,0,"HTTPServer.start() called");
            }
            catch (Exception e)
            {
                Utils.error("Exception starting HTTPSeverThread:" + e);
            }
            super.start();
        }


        public synchronized void shutdown()
        // nobody calls this right now
        {
            Utils.log(dbg_http,0,"shutting down http_server ...");
            theServer.stop();
            theServer = null;
            Utils.log(dbg_http,0,"http_server shut down");
        }

    }


    //-----------------------------------------------
    // the actual server (instance methods)
    //-----------------------------------------------


    public HTTPServer(Artisan ma) throws IOException
        // Constructed with a Context for resources
        // The HTTP Server creates the handlers, so it
        // must be stopped and restarted if Prefs change.
    {
        super(Utils.server_port);
        artisan = ma;
        Utils.log(dbg_http,1,"created HTTPServer");
    }


    private boolean checkService(String service)
    {
        HttpRequestHandler handler = handlers.get(service);
        if (handler == null)
        {
            Utils.warning(0,0,"Request for non-existent service: " + service);
            return false;
        }
        return true;
    }


    @Override
    public void start()
        // override to start() the ssdp server
        // after the http server is started
    {
        boolean started = true;
        Utils.log(dbg_http,1,"HTTPServer.start() called  ...");

        handlers = new HashMap<String,HttpRequestHandler>();
        event_manager = new UpnpEventManager(artisan,this);

        // start the httpHandlers

        if (Prefs.getBoolean(Prefs.id.START_HTTP_MEDIA_SERVER) &&
            artisan.getLocalLibrary() != null)
        {
            Utils.log(dbg_http,1,"starting MediaServer http listener ...");
            handlers.put("ContentDirectory",new ContentDirectory(artisan,this,httpUtils.upnp_urn));
        }

        if ((Prefs.getBoolean(Prefs.id.START_HTTP_MEDIA_RENDERER) ||
            Prefs.getBoolean(Prefs.id.START_HTTP_OPEN_HOME_SERVER))&&
            artisan.getLocalRenderer() != null)
        {
            Utils.log(dbg_http,1,"starting MediaRenderer http listeners ...");
            handlers.put("AVTransport",new AVTransport(artisan,this,httpUtils.upnp_urn));
            RenderingControl rc = new RenderingControl(artisan,this,httpUtils.upnp_urn);
            handlers.put("RenderingControl",rc);
            rc.start();
        }

        if (Prefs.getBoolean(Prefs.id.START_HTTP_OPEN_HOME_SERVER) &&
            artisan.getLocalRenderer() != null)
        {
            Utils.log(dbg_http,1,"starting OpenHomeRenderer http listeners ...");

            OpenProduct  product  = new OpenProduct(artisan,this,httpUtils.open_service_urn);
            OpenVolume   volume   = new OpenVolume(artisan,this,httpUtils.open_service_urn);
            OpenPlaylist playlist = new OpenPlaylist(artisan,this,httpUtils.open_service_urn);
            OpenInfo     info     = new OpenInfo(artisan,this,httpUtils.open_service_urn);
            OpenTime     time     = new OpenTime(artisan,this,httpUtils.open_service_urn);

            Utils.log(dbg_http,2,"OpenHomeRenderer http listeners created");

            handlers.put("Product",product);
            handlers.put("Volume",volume);
            handlers.put("Playlist",playlist);
            handlers.put("Info",info);
            handlers.put("Time",time);

            Utils.log(dbg_http,2,"OpenHomeRenderer http listeners registered");

            // currently only the open home objects are also upnpEventHandlers
            // so these two lists of handlers are separate, but it seems they
            // should be the same, factored perhaps out of UpnpEventMangaer

            product.start();
            volume.start();
            playlist.start();
            info.start();
            time.start();

            Utils.log(dbg_http,2,"Finished starting OpenHomeRenderer http listeners");

        }


        // now start the superclass


        try
        {
            super.start();
        }
        catch (IOException e)
        {
            Utils.error("Could not start http server: " + e.toString());
            started = false;
        }

        Utils.log(dbg_http,1,"HTTPServer.start() finished");
    }



    @Override
    public void stop()
        // override to stop() the ssdp server
    {
        Utils.log(dbg_http,1,"HTTPServer.stop() called  ...");
        super.stop();

        // stop the handlers
        if (handlers != null)
        {
            for (HttpRequestHandler handler : handlers.values())
            {
                if (handler instanceof UpnpEventHandler)
                    ((UpnpEventHandler) handler).stop();
            }
            handlers.clear();
            handlers = null;
        }

        event_manager = null;
        Utils.log(dbg_http,1,"HTTPServer.stop() finished");
    }




    //---------------------------------------------------------------------------
    // SERVE
    //---------------------------------------------------------------------------


    @Override
    public Response serve(IHTTPSession session)
    {
        // synchronized (this)   // comment this out to run asynchronously
        {
            String uri = session.getUri();

            String dbg_from = session.getHeaders().get("remote-addr") + " ";
            Utils.log(dbg_requests,0,dbg_from + session.getMethod() + " " +  uri);

            // Default response is 404 not found

            Response response = newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                NanoHTTPD.MIME_PLAINTEXT,
                "ERROR 404 - Not Found: " + uri.toString());

            try
            {
                // simple responses

                if (uri.equals("/faveicon.ico"))
                {
                    Utils.log(dbg_favicon_requests,1,dbg_from + "favicon request");
                    response = asset_file_response(response,"icons/artisan.png");
                }
                else if (uri.startsWith("/icons/"))
                {
                    uri = uri.replace("/icons/","icons/");
                    Utils.log(dbg_favicon_requests,1,dbg_from + "icon request(" + uri + ")");
                    response = asset_file_response(response,uri);
                }

                //------------------------------------------
                // device.OpenHomeRenderer Event Callbacks
                //------------------------------------------
                // /openCallback/device_uuid/Service

                else if (uri.startsWith("/openCallback/"))
                {
                    if (open_home_renderer == null)
                        Utils.warning(3,0,"No open_home_renderer " + dbg_from);
                    else
                    {
                        uri = uri.replace("/openCallback/","");
                        String uuid = uri.replaceAll("\\/.*$","");
                        String service = uri.replace(uuid + "/","");

                        boolean is_loop_action = service.equals("OpenTime");
                        dbg_from = "openCallback(" + service + ") uuid=" + uuid + " from " + dbg_from;
                        if (!is_loop_action) Utils.log(0,0,dbg_from);

                        Document doc = httpUtils.get_xml_from_post(session);
                        if (doc == null)
                        {
                            Utils.error("Null document in " + dbg_from);
                            return response;
                        }
                        try
                        {
                            response = open_home_renderer.response(session,response,service,doc);
                        }
                        catch (Exception e)
                        {
                            Utils.error("Exception:" + e + "==" + e.getCause());
                        }
                    }
                }

                //------------------------------------------------------
                // device and service description responses
                //------------------------------------------------------

                else if (uri.startsWith("/device/"))
                {
                    uri = uri.replace("/device/","");
                    Utils.log(dbg_device_requests,1,dbg_from + "device request(" + uri + ")");

                    String xml = null;
                    if (handlers.get("ContentDirectory") != null &&
                        uri.equals("MediaServer.xml"))
                    {
                        xml = SSDPServer.MediaServer_description();
                    }
                    else if (handlers.get("AVTransport") != null &&
                        uri.equals("MediaRenderer.xml"))
                    {
                        xml = SSDPServer.MediaRenderer_description();
                    }
                    else if (handlers.get("Product") != null &&
                        uri.equals("OpenHome.xml"))
                    {
                        xml = SSDPServer.OpenHome_description();
                    }

                    if (xml != null)
                        response = newFixedLengthResponse(NanoHTTPD.Response.Status.OK,"text/xml",xml);
                }


                else if (uri.startsWith("/service/"))
                {
                    uri = uri.replace("/service/","");
                    Utils.log(dbg_service_requests,1,dbg_from + "service request(" + uri + ")");

                    // the xml file has a slightly different name
                    // for open home services

                    String xml_service = uri.replace("1.xml","");
                    String service = xml_service.replace("OpenHome_","");
                    if (checkService(service))
                    {
                        response = asset_file_response(response,"ssdp/" + uri);
                    }
                }


                //------------------------------------------------------
                // Service responses
                //------------------------------------------------------

                else if (uri.matches("^\\/.+\\/.+"))
                {
                    uri = uri.replaceFirst("\\/","");
                    String parts[] = uri.split("\\/");
                    String service = parts[0];              // SERVICE

                    if (checkService(service))
                    {
                        uri = uri.replace(service + "/","");

                        //----------------------------------------------------------------------
                        // event_subscription_requests are handed off to the event manager
                        //----------------------------------------------------------------------

                        if (uri.equals("event"))
                        {
                            Utils.log(dbg_event_requests,1,dbg_from + "event request " + session.getMethod() + " " + service);
                            response = event_manager.subscription_request_response(session,response,service);
                        }

                        // All other /control and uri requests are passed off to services.

                        else
                        {
                            String action = "";
                            Document doc = null;

                            // parse some extra stuff for /control requests
                            // common to all services

                            if (uri.equals("control"))
                            {
                                // get the action

                                action = session.getHeaders().get("soapaction");
                                if (action == null)
                                {
                                    Utils.error("Null action in " + service + "/control request");
                                    return response;
                                }
                                action = action.replaceAll("^.*#","");
                                action = action.replaceAll("\"","");

                                boolean is_loop_action = false
                                    || action.equals("GetTransportInfo")
                                    || action.equals("GetPositionInfo")
                                    || action.equals("Time")
                                    // || (service.equals("RenderingControl") && action.startsWith("Get"))
                                    ;

                                int use_dbg = is_loop_action ?
                                    dbg_looping_control_requests :
                                    dbg_control_requests;
                                Utils.log(use_dbg,1,dbg_from + " control request " + service + "(" + action + ")");

                                // get the xml document

                                doc = httpUtils.get_xml_from_post(session);
                                if (doc == null)
                                {
                                    Utils.error("Null document in " + service + "(" + action + ") request");
                                    return response;
                                }
                            }
                            else
                            {
                                Utils.log(dbg_other_requests,1,dbg_from + "other request " + " " + service + "(" + uri + ")");
                            }

                            // dispatch the request to a service handler
                            // shorten the name of OpenHome services to just Product, Volume, etc

                            String use_service = service.startsWith("Open") ?
                                service.replace("Open","") : service;
                            boolean isOpenPlaylist = use_service.equals("Playlist");

                            // OpenPlaylist
                            // gets the matching subscriber, if any, for EXPOSE_SCHEME
                            // is currently only service to get a subscriber. We consider
                            // the Playlist request to have come from the subscriber if the ip
                            // and the user agent match a subscriber to the Playlist

                            UpnpEventSubscriber open_playlist_subscriber = null;
                            if (isOpenPlaylist)
                            {
                                String ip = session.getHeaders().get("remote-addr");
                                String ua = session.getHeaders().get("user-agent");
                                open_playlist_subscriber = event_manager.findOpenPlaylistSubscriber(ip,ua);
                            }


                            // HANDLER REQUEST
                            // The httpHandlers are synchronized with their potential
                            // UpnpEventHandlers so that actions and events are atomic

                            HttpRequestHandler handler = handlers.get(use_service);
                            if (handler == null)
                                Utils.error("No handler found for service(" + service + ")");
                            else synchronized (handler)
                            {
                                response = handler.response(session,response,uri,service,action,doc,
                                    open_playlist_subscriber);
                            }

                        }   // ! Event request
                    }   // checkServices
                }   // starts with /
            }
            catch (Exception e)
            {
                // Catch exceptions thrown by NanoHTTPD.serve(),
                // which, for some reason, do not require us to
                // use a try/catch otherwise ..

                Utils.error("Exception in HTTPServer.serve():" + e.toString());
            }

            if (response.getStatus() != Response.Status.OK &&
                (!session.getUri().contains("openCallback") || open_home_renderer != null))
                Utils.warning(0,0,"returning " + response.getStatus().toString() + " for " + dbg_from + " " + session.getUri());

            return response;

        }   // synchronized

    }   // HTTPServer::serve()



    //----------------------------------------------
    // Utility Response Methods
    //----------------------------------------------

    public static String getAnyUserAgent(IHTTPSession session)
        // helper method for dlna /event handlers to get
        // a user agent. if one not provided uses the ip
        // address, and then wtf.
    {
        String user_agent = session.getHeaders().get("user-agent");
        if (user_agent == null || user_agent.equals(""))
        user_agent = session.getHeaders().get("remote-addr");
        if (user_agent == null)
        user_agent = "wtf user agent";
        return user_agent;
    }


    public Response asset_file_response (Response response, String uri)
    {
        Resources res = artisan.getResources();

        String mime_type =
            uri.matches(".*\\.jpg$") ? "image/jpeg" :
            uri.matches(".*\\.png$") ? "image/png" :
            uri.matches(".*\\.xml$") ? "text/xml" :
            uri.matches(".*\\.html$") ? "text/html" :
            "";

        if (mime_type.equals(""))
        {
            Utils.error("unknown mime type in asset_file_response: " + uri);
        }

        AssetManager am = res.getAssets();
        Utils.log(dbg_http+1, 0, "asset_file_response(" + uri + "," + mime_type + ")");

        try
        {
            InputStream stream = am.open(uri);
            response = newChunkedResponse(Response.Status.OK, mime_type, stream);
        } catch (Exception e)
        {
            Utils.error("could not open asset_file=" + uri);
        }
        return response;
    }







}   // class HTTPServer
