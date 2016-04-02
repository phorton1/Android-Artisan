//----------------------------------------------
// OpenHome Info Actions
//----------------------------------------------

package prh.server.http;

import org.w3c.dom.Document;

import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.artisan.interfaces.Renderer;
import prh.artisan.Track;
import prh.server.HTTPServer;
import prh.server.utils.UpnpEventSubscriber;
import prh.server.utils.httpRequestHandler;
import prh.server.utils.updateCounter;
import prh.server.utils.UpnpEventHandler;
import prh.utils.httpUtils;


public class OpenInfo implements httpRequestHandler,UpnpEventHandler
{
    private Artisan artisan;
    private HTTPServer http_server;
    private String urn;

    public OpenInfo(Artisan ma, HTTPServer http, String the_urn)
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
        HashMap<String,String> hash = new HashMap<String,String>();
        Renderer local_renderer = artisan.getLocalRenderer();
        Track track = local_renderer.getRendererTrack();

        boolean ok = true;

        if (action.equals("Counters"))
        {
            hash.put("TrackCount",Integer.toString(local_renderer.getTotalTracksPlayed()));
            hash.put("DetailsCount","0");
            hash.put("MetatextCount","0");
        }
        else if (action.equals("Track"))
        {
            hash.put("Uri",track == null ? "" : track.getPublicUri());
            hash.put("Metadata",track==null ? "" : track.getDidl());
        }
        else if (action.equals("Details"))
        {
            hash.put("Duration",Integer.toString(track==null?0:track.getDuration()/1000));
            hash.put("BitRate","0");
            hash.put("BitDepth","16");
            hash.put("SampleRate","0");
            hash.put("Lossless","0");
            hash.put("CodecName",track==null ? "" : track.getType());
        }
        else if (action.equals("MetaText"))
        {
            hash.put("Metatext","");
        }
        else
        {
            ok = false;
        }

        if (ok)
            response = httpUtils.hash_response(http_server,urn,service,action,hash);
        return response;
    }


    //----------------------------------------
    // Event Dispatching
    //----------------------------------------

    updateCounter update_counter = new updateCounter();
    @Override public int getUpdateCount()  { return update_counter.get_update_count(); }
    @Override public int incUpdateCount()  { return update_counter.inc_update_count(); }
    @Override public String getName() { return "Info"; };

    @Override public String getEventContent(UpnpEventSubscriber unused_subscriber)
    {
        HashMap<String,String> hash = new HashMap<String,String>();
        Renderer local_renderer = artisan.getLocalRenderer();
        Track track = local_renderer.getRendererTrack();

        // from renderer/song

        hash.put("Uri",track==null ? "" : track.getPublicUri());
        hash.put("Duration",Integer.toString(track==null ? 0 : track.getDuration() / 1000));
        // prh hash.put("TrackCount",Integer.toString(renderer.getTotalTracksPlayed()));
        hash.put("CodecName",track==null ? "" : track.getType());
        hash.put("Metadata",track == null ? "" : track.getDidl());
            // not passed thru by renderer

        // constants

        // prh hash.put("TrackCount",Integer.toString(renderer.getTotalTracksPlayed()));
        hash.put("DetailsCount","0");
        hash.put("MetatextCount","0");
        hash.put("BitRate","0");
        hash.put("BitDepth","16");
        hash.put("SampleRate","0");
        hash.put("Lossless","0");
        hash.put("Metatext","");

        return httpUtils.hashToXMLString(hash,true);
    }


}   // class OpenInfo