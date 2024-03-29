//----------------------------------------------
// OpenHome Time Actions
//----------------------------------------------

package prh.server.http;

import org.w3c.dom.Document;

import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.base.HttpRequestHandler;
import prh.base.Renderer;
import prh.artisan.Track;
import prh.server.HTTPServer;
import prh.server.utils.UpnpEventSubscriber;
import prh.server.utils.updateCounter;
import prh.base.UpnpEventHandler;
import prh.utils.httpUtils;


public class OpenTime implements HttpRequestHandler,UpnpEventHandler
{
    private Artisan artisan;
    private HTTPServer http_server;
    private String urn;

    public OpenTime(Artisan ma, HTTPServer http, String the_urn)
    {
        artisan = ma;
        http_server = http;
        urn = the_urn;
    }


    @Override public void start()
    {
        http_server.getEventManager().RegisterHandler(this);
    }

    @Override public void stop()
    {
        http_server.getEventManager().UnRegisterHandler(this);
    }

    @Override public void notifySubscribed(UpnpEventSubscriber subscriber,boolean subscribe)
    {}


    @Override public NanoHTTPD.Response response(
        NanoHTTPD.IHTTPSession session,
        NanoHTTPD.Response response,
        String unused_uri,
        String service,
        String action,
        Document unused_doc,
        UpnpEventSubscriber unused_subscriber)
    {
        if (action.equals("Time"))
        {
            HashMap<String,String> hash = new HashMap<String,String>();
            Renderer renderer = artisan.getRenderer();
            Track track = renderer.getRendererTrack();

            hash.put("TrackCount",Integer.toString(renderer.getTotalTracksPlayed()));
            hash.put("Duration",Integer.toString(track==null?0:track.getDuration()/1000));
            hash.put("Seconds",Integer.toString(renderer.getPosition()/1000));
            response = httpUtils.hash_response(http_server,urn,service,action,hash);
        }

        return response;
    }

    //----------------------------------------
    // Event Dispatching
    //----------------------------------------
    // the count is bumped in the Renderer

    private updateCounter update_counter = new updateCounter();
    @Override public int getUpdateCount()  { return update_counter.get_update_count(); }
    @Override public int incUpdateCount()  { return update_counter.inc_update_count(); }
    @Override public String getName() { return "Time"; };

    @Override public String getEventContent(UpnpEventSubscriber unused_subscriber)
    {
        HashMap<String,String> hash = new HashMap<String,String>();
        Renderer renderer = artisan.getRenderer();
        Track track = renderer.getRendererTrack();

        hash.put("TrackCount",Integer.toString(renderer.getTotalTracksPlayed()));
        hash.put("Duration",Integer.toString(track==null?0:track.getDuration()/1000));
        hash.put("Seconds",Integer.toString(renderer.getPosition()/1000));
        return httpUtils.hashToXMLString(hash,true);
    }





}   // class OpenTime