//----------------------------------------------
// OpenHome Time Actions
//----------------------------------------------

package prh.server.http;

import org.w3c.dom.Document;

import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.artisan.Renderer;
import prh.artisan.Track;
import prh.server.HTTPServer;
import prh.server.utils.UpnpEventSubscriber;
import prh.server.utils.httpRequestHandler;
import prh.server.utils.UpdateCounter;
import prh.server.utils.UpnpEventHandler;
import prh.utils.httpUtils;


public class OpenTime extends httpRequestHandler implements UpnpEventHandler
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


    public void start()
    {
        http_server.getEventManager().RegisterHandler(this);
    }

    public void stop()
    {
        http_server.getEventManager().UnRegisterHandler(this);
    }

    public void notifySubscribed(UpnpEventSubscriber subscriber,boolean subscribe)
    {}


    public NanoHTTPD.Response response(
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
            Track track = renderer.getTrack();

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

    UpdateCounter update_counter = new UpdateCounter();
    public int getUpdateCount()  { return update_counter.get_update_count(); }
    public int incUpdateCount()  { return update_counter.inc_update_count(); }
    public String getName() { return "Time"; };

    public String getEventContent(UpnpEventSubscriber unused_subscriber)
    {
        HashMap<String,String> hash = new HashMap<String,String>();
        Renderer renderer = artisan.getRenderer();
        Track track = renderer.getTrack();

        hash.put("TrackCount",Integer.toString(renderer.getTotalTracksPlayed()));
        hash.put("Duration",Integer.toString(track==null?0:track.getDuration()/1000));
        hash.put("Seconds",Integer.toString(renderer.getPosition()/1000));
        return httpUtils.hashToXMLString(hash,true);
    }





}   // class OpenTime