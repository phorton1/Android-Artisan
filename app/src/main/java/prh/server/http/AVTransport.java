// DLNA Renderer

package prh.server.http;


import org.w3c.dom.Document;

import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.artisan.Track;
import prh.device.LocalRenderer;
import prh.server.HTTPServer;
import prh.server.utils.UpnpEventSubscriber;
import prh.server.utils.httpRequestHandler;
import prh.utils.httpUtils;
import prh.utils.Utils;


public class AVTransport implements httpRequestHandler
{

    private static int dbg_av = 0;

    private Artisan artisan;
    private HTTPServer http_server;
    private String urn;

    public AVTransport(Artisan ma,HTTPServer http,String the_urn )
    {
        artisan = ma;
        http_server = http;
        urn = the_urn;
    }


    // not a UpnpEventHandler (yet)
    // so there are no start() or stop() methods

    //-----------------------------------------------------------
    // control_response (Actions)
    //-----------------------------------------------------------

    @Override
    public NanoHTTPD.Response response(
            NanoHTTPD.IHTTPSession session,
            NanoHTTPD.Response response,
            String unused_uri,
            String service,
            String action,
            Document doc,
            UpnpEventSubscriber unused_subscriber)
    {
        // Only handles actions, expects doc != null, and never looks at uri
        // All actions get, and ignore, an InstanceID parameter ...

        // Really should make the local_renderer the current_renderer
        // as otherwise, music can start playing in the background of
        // a control point !!

        HashMap<String,String> hash = new HashMap<String,String>();
        LocalRenderer local_renderer = artisan.getLocalRenderer();
            // shall never be null ..

        if (action.equals("GetDeviceCapabilities"))
        {
            hash.put("PlayMedia","NETWORK");
            hash.put("RecMedia","NOT_IMPLEMENTED");
            hash.put("RecQualityModes","NOT_IMPLEMENTED");
            response = httpUtils.hash_response(http_server,urn,service,action,hash);
        }
        else if (action.equals("SetAVTransportURI"))
        {
            String cur_uri = httpUtils.getXMLString(doc,"CurrentURI",true);
            String metadata = httpUtils.getXMLString(doc,"CurrentURIMetaData",false);
            Utils.log(dbg_av,0,"cur_uri="+cur_uri);
            Utils.log(dbg_av,0,"metadata=" + metadata);
            Track track = new Track(cur_uri,metadata);
            local_renderer.setRendererTrack(track,true);
            response = httpUtils.ok_response(http_server,urn,service,action);
        }
        else if (action.equals("Play"))
        {
            int speed = httpUtils.getXMLInt(doc,"Speed",false);
            Utils.log(dbg_av,0,"speed="+speed);
            if (speed == 0) speed = 1;
            local_renderer.transport_play();
            response = httpUtils.ok_response(http_server,urn,service,action);
        }
        else if (action.equals("Stop"))
        {
            local_renderer.transport_stop();
            response = httpUtils.ok_response(http_server,urn,service,action);
        }
        else if (action.equals("Pause"))
        {
            local_renderer.transport_pause();
            response = httpUtils.ok_response(http_server,urn,service,action);
        }
        else if (action.equals("Next"))
        {
            local_renderer.incAndPlay(1);
            response = httpUtils.ok_response(http_server,urn,service,action);
        }
        else if (action.equals("Previous"))
        {
            local_renderer.incAndPlay(-1);
            response = httpUtils.ok_response(http_server,urn,service,action);
        }
        else if (action.equals("Seek"))
        {
            // we only support UNIT=RELTIME
            String unit = httpUtils.getXMLString(doc,"Unit",true);
            String target = httpUtils.getXMLString(doc,"Target",true);
            Utils.log(dbg_av,0,"unit="+unit+" target="+target);
            int position = Utils.stringToDuration(target);
            local_renderer.seekTo(position);
            response = httpUtils.ok_response(http_server,urn,service,action);
        }
        else if (action.equals("SetPlayMode"))
        {
            String mode = httpUtils.getXMLString(doc,"NewPlayMode",true);
            Utils.log(dbg_av,0,"mode="+mode);
            response = httpUtils.ok_response(http_server,urn,service,action);
        }
        else if (action.equals("GetTransportInfo"))
        {
            hash.put("CurrentTransportStatus",local_renderer.getRendererStatus());
            hash.put("CurrentTransportState",local_renderer.getRendererState());
            hash.put("CurrentSpeed","1");
            response = httpUtils.hash_response(http_server,urn,service,action,hash);
        }
        else if (action.equals("GetPositionInfo"))
        {
            Track track = local_renderer.getRendererTrack();
            String track_index = "1";

            track_index = Integer.toString(artisan.getCurrentPlaylist().
                getCurrentIndex());
            hash.put("Track",track_index);

            hash.put("RelCount","0");
            hash.put("AbsCount","0");
            hash.put("AbsTime","0:00");

            hash.put("TrackDuration",track == null ? "0:00" :
                Utils.durationToString(track.getDuration(),Utils.how_precise.FOR_SEEK));
            hash.put("TrackURI",track == null ? "" :
                track.getPublicUri());
            hash.put("RelTime",track == null ? "0:00" :
                Utils.durationToString(local_renderer.getPosition(),Utils.how_precise.FOR_SEEK));
            hash.put("TrackMetaData",track == null ? "" :
                track.getDidl());

            // httpUtils.dbg_hash_response = true;
            response = httpUtils.hash_response(http_server,urn,service,action,hash);
            // httpUtils.dbg_hash_response = false;
        }
        else if (action.equals("GetTransportSettings"))
        {
            Utils.log(dbg_av,0,"");
            hash.put("PlayMode","");
            hash.put("RecQualityMode","NOT_IMPLEMENTED");
            response = httpUtils.hash_response(http_server,urn,service,action,hash);
        }
        else
        {
            // We currently do not support
            // GetMediaInfo and GetCurrentTransportActions
            Utils.error("unknown/unsupported action(" + action + ") in AVTransport request");
        }

        return response;
    }


}   // class AVTransport
