// HTTP Server

package prh.server;

import android.content.res.Resources;
import android.content.res.AssetManager;

import org.w3c.dom.Document;

import java.io.InputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.artisan.Prefs;
import prh.server.http.AVTransport;
import prh.server.http.ContentDirectory;
import prh.server.http.OpenHomeServer;
import prh.server.http.RenderingControl;
import prh.utils.DlnaUtils;
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
    private static int dbg_favicon_requests = 0;
    private static int dbg_icon_requests = 0;
    private static int dbg_service_requests = 0;
    private static int dbg_device_requests = 0;
    private static int dbg_control_requests = 0;
    private static int dbg_event_requests = 0;
    private static int dbg_other_requests = 0;

    private static int dbg_looping_control_requests = 1;
        // show GetTransportInfo, GetPosition, and Time actions

    private Artisan artisan;
        // the instance keeps a Context
        // for getting resources

    private ContentDirectory content_directory;
    private AVTransport av_transport;
    private RenderingControl rendering_control;
    private OpenHomeServer open_home;


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

    private HashMap<String,httpRequestHandler> handlers = new HashMap<String,httpRequestHandler>();


    public HTTPServer(Artisan ma) throws IOException
    // Constructed with a Context for resources
    {
        super(Utils.server_port);
        artisan = ma;

        Utils.log(dbg_http,1,"creating HTTPServer ...");

        if (Prefs.getBoolean(Prefs.id.START_HTTP_MEDIA_SERVER) &&
            artisan.getLocalLibrary() != null)
        {
            Utils.log(dbg_http,1,"starting MediaServer http listener ...");
            content_directory = new ContentDirectory(this,artisan);
            handlers.put("ContentDirectory",content_directory);
        }

        if ((Prefs.getBoolean(Prefs.id.START_HTTP_MEDIA_RENDERER) ||
            Prefs.getBoolean(Prefs.id.START_HTTP_OPEN_HOME_SERVER))&&
            artisan.getLocalRenderer() != null)
        {
            Utils.log(dbg_http,1,"starting MediaRenderer http listeners ...");

            av_transport = new AVTransport(this,artisan);
            handlers.put("AVTransport",av_transport);

            rendering_control = new RenderingControl(this,artisan);
            handlers.put("RenderingControl",rendering_control);
        }

        if (Prefs.getBoolean(Prefs.id.START_HTTP_OPEN_HOME_SERVER) &&
            artisan.getLocalRenderer() != null)
        {
            Utils.log(dbg_http,1,"starting OpenHomeRenderer http listener ...");
            open_home = new OpenHomeServer(this,artisan);
            handlers.put("OpenHome",open_home);
        }

        Utils.log(dbg_http,0,"HTTPServer() created");
    }


    private boolean checkService(String service)
    {
        if (service.equals("AVTransport") && av_transport != null)
            return true;
        else if (service.equals("RenderingControl") && rendering_control != null)
            return true;
        else if (service.equals("ContentDirectory") && content_directory != null)
            return true;
        else if (service.startsWith("Open") && open_home != null)
            return true;
        Utils.warning(0,0,"checkService("+service+") returning false");
        return false;
    }


    @Override
    public void start()
        // override to start() the ssdp server
        // after the http server is started
    {
        boolean started = true;
        Utils.log(dbg_http,1,"HTTPServer.start() called  ...");

        try
        {
            super.start();
        }
        catch (Exception e)
        {
            Utils.error("Could not start http server: " + e.toString());
            started = false;
        }

        // start the ssdp server

        if (Artisan.START_SSDP_IN_HTTP && started)
        {
            Utils.log(0,0,"starting ssdp_server ...");
            artisan.ssdp_server = new SSDPServer(artisan);
            Thread ssdp_thread = new Thread(artisan.ssdp_server);
            ssdp_thread.start();
            Utils.log(0,0,"ssdp_server started");
        }
        Utils.log(dbg_http,1,"HTTPServer.start() finished");
    }


    @Override
    public void stop()
        // override to stop() the ssdp server
    {
        Utils.log(dbg_http,1,"HTTPServer.stop() called  ...");
        super.stop();

        if (Artisan.START_SSDP_IN_HTTP)
        {
            if (artisan.ssdp_server != null)
                artisan.ssdp_server.shutdown();
            artisan.ssdp_server = null;
        }
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

            String dbg_from = session.getHeaders().get("remote-addr") +": ";
            Utils.log(dbg_requests,0,dbg_from + " " + session.getMethod() + " " +  uri);

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


                //------------------------------------------------------
                // device and service description responses
                //------------------------------------------------------

                else if (uri.startsWith("/device/"))
                {
                    uri = uri.replace("/device/","");
                    Utils.log(dbg_device_requests,1,dbg_from + "device request(" + uri + ")");

                    String xml = null;
                    if (content_directory != null &&
                        uri.equals("MediaServer.xml"))
                    {
                        xml = SSDPServer.MediaServer_description();
                    }
                    else if (av_transport != null &&
                        uri.equals("MediaRenderer.xml"))
                    {
                        xml = SSDPServer.MediaRenderer_description();
                    }
                    else if (open_home != null &&
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
                    String service = uri.replace("1.xml","");
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
                        String urn = service.startsWith("Open") ?
                            DlnaUtils.open_urn :
                            DlnaUtils.upnp_urn;

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

                            boolean is_loop_action =
                                action.equals("GetTransportInfo") ||
                                    action.equals("GetPositionInfo") ||
                                    action.equals("Time");
                            int use_dbg = is_loop_action ?
                                dbg_looping_control_requests :
                                dbg_control_requests;
                            Utils.log(use_dbg,1,dbg_from + "control request " + service + "(" + action + ")");

                            // get the xml document

                            doc = DlnaUtils.get_xml_from_post(session);
                            if (doc == null)
                            {
                                Utils.error("Null document in " + service + " " + action + "request");
                                return response;
                            }
                        }
                        else if (uri.equals("event"))
                        {
                            Utils.log(dbg_event_requests,1,dbg_from + "event request " + session.getMethod() + " " + service);
                        }
                        else
                        {
                            Utils.log(dbg_other_requests,1,dbg_from + "other request " + " " + service + "(" + uri + ")");
                        }

                        // dispatch the request to a service handler
                        // shorten the name of OpenHome services to just Product, Volume, etc

                        String use_service = service.startsWith("Open") ? "OpenHome" : service;
                        if (service.startsWith("Open"))
                            service = service.replace("Open","");

                        httpRequestHandler handler = handlers.get(use_service);
                        if (handler == null)
                            Utils.error("No handler found for service(" + service + ")");
                        else
                        {
                            response = handler.response(session,response,uri,urn,service,action,doc);
                        }
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

            if (response.getStatus() != Response.Status.OK)
                Utils.warning(0,0,"returning " + response.getStatus().toString() + " for " + dbg_from + " " + session.getUri());

            return response;

        }   // synchronized

    }   // HTTPServer::serve()


    //----------------------------------------------
    // Response Methods
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

    //--------------------------------------
    // dispatch openHomeEvents
    //--------------------------------------

    public void sendOpenHomeEvents()
    {
        if (open_home != null)
            open_home.sendEvents();
    }


    public void incUpdateCount(String service)
    {
        if (open_home != null)
            open_home.incUpdateCount(service);
    }


}   // class HTTPServer
