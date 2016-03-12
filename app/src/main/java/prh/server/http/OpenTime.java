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
import prh.utils.DlnaUtils;


public class OpenTime implements OpenEventHandler
{
    private Artisan artisan;
    private HTTPServer http_server;

    public OpenTime(Artisan ma, HTTPServer http)
    {
        artisan = ma;
        http_server = http;
    }


    public NanoHTTPD.Response timeAction(
        NanoHTTPD.Response response,
        Document doc,
        String urn,
        String service,
        String action)
    {
        if (action.equals("Time"))
        {
            HashMap<String,String> hash = new HashMap<String,String>();
            Renderer renderer = artisan.getRenderer();
            Track track = renderer.getTrack();

            hash.put("TrackCount",Integer.toString(renderer.getTotalTracksPlayed()));
            hash.put("Duration",Integer.toString(track==null?0:track.getDuration()/1000));
            hash.put("Seconds",Integer.toString(renderer.getPosition()/1000));
            response = DlnaUtils.hash_response(http_server,urn,service,action,hash);
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
    public String eventHandlerName() { return "Time"; };

    public String getEventContent()
    {
        HashMap<String,String> hash = new HashMap<String,String>();
        Renderer renderer = artisan.getRenderer();
        Track track = renderer.getTrack();

        hash.put("TrackCount",Integer.toString(renderer.getTotalTracksPlayed()));
        hash.put("Duration",Integer.toString(track==null?0:track.getDuration()/1000));
        hash.put("Seconds",Integer.toString(renderer.getPosition()/1000));
        return DlnaUtils.hashToXMLString(hash,true);
    }





}   // class OpenTime