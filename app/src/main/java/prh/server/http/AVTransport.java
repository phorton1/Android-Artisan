// DLNA Renderer

package prh.server.http;


import org.w3c.dom.Document;

import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.artisan.Playlist;
import prh.artisan.Renderer;
import prh.artisan.Track;
import prh.server.HTTPServer;
import prh.server.httpRequestHandler;
import prh.utils.DlnaUtils;
import prh.utils.Utils;


public class AVTransport extends httpRequestHandler
{

    private static int dbg_av = 0;

    private Artisan artisan;
    private HTTPServer http_server;

    public AVTransport(HTTPServer http, Artisan ma)
    {
        artisan = ma;
        http_server = http;
    }


    //-----------------------------------------------------------
    // control_response (Actions)
    //-----------------------------------------------------------

    @Override
    public NanoHTTPD.Response response(
            NanoHTTPD.IHTTPSession session,
            NanoHTTPD.Response response,
            String uri,
            String urn,
            String service,
            String action,
            Document doc )
    {
        // Only handles actions, expects doc != null, and never looks at uri
        // All actions get, and ignore, an InstanceID parameter

        if (!uri.equals("control"))
            return response;

        HashMap<String,String> hash = new HashMap<String,String>();
        Renderer renderer = artisan.getRenderer();

        if (action.equals("GetDeviceCapabilities"))
        {
            hash.put("PlayMedia","NETWORK");
            hash.put("RecMedia","NOT_IMPLEMENTED");
            hash.put("RecQualityModes","NOT_IMPLEMENTED");
            response = DlnaUtils.hash_response(http_server,urn,service,action,hash);
        }
        else if (action.equals("SetAVTransportURI"))
        {
            String cur_uri = DlnaUtils.getXMLString(doc,"CurrentURI",true);
            String metadata = DlnaUtils.getXMLString(doc,"CurrentURIMetaData",false);
            Utils.log(dbg_av,0,"cur_uri="+cur_uri);
            Utils.log(dbg_av,0,"metadata=" + metadata);
            Track track = new Track(cur_uri,metadata);
            renderer.setTrack(track,true);
            response = DlnaUtils.ok_response(http_server,urn,service,action);
        }
        else if (action.equals("Play"))
        {
            int speed = DlnaUtils.getXMLInt(doc,"Speed",false);
            Utils.log(dbg_av,0,"speed="+speed);
            if (speed == 0) speed = 1;
            renderer.play();
            response = DlnaUtils.ok_response(http_server,urn,service,action);
        }
        else if (action.equals("Stop"))
        {
            renderer.stop();
            response = DlnaUtils.ok_response(http_server,urn,service,action);
        }
        else if (action.equals("Pause"))
        {
            renderer.pause();
            response = DlnaUtils.ok_response(http_server,urn,service,action);
        }
        else if (action.equals("Next"))
        {
            renderer.incAndPlay(1);
            response = DlnaUtils.ok_response(http_server,urn,service,action);
        }
        else if (action.equals("Previous"))
        {
            renderer.incAndPlay(-1);
            response = DlnaUtils.ok_response(http_server,urn,service,action);
        }
        else if (action.equals("Seek"))
        {
            // we only support UNIT=RELTIME
            String unit = DlnaUtils.getXMLString(doc,"Unit",true);
            String target = DlnaUtils.getXMLString(doc,"Target",true);
            Utils.log(dbg_av,0,"unit="+unit+" target="+target);
            int position = Utils.stringToDuration(target);
            renderer.seekTo(position);
            response = DlnaUtils.ok_response(http_server,urn,service,action);
        }
        else if (action.equals("SetPlayMode"))
        {
            String mode = DlnaUtils.getXMLString(doc,"NewPlayMode",true);
            Utils.log(dbg_av,0,"mode="+mode);
            response = DlnaUtils.ok_response(http_server,urn,service,action);
        }
        else if (action.equals("GetTransportInfo"))
        {
            hash.put("CurrentTransportStatus",renderer.getRendererStatus());
            hash.put("CurrentTransportState",renderer.getRendererState());
            hash.put("CurrentSpeed",renderer.getPlaySpeed());
            response = DlnaUtils.hash_response(http_server,urn,service,action,hash);
        }
        else if (action.equals("GetPositionInfo"))
        {
            Track track = renderer.getTrack();
            String track_index = "1";

            Playlist playlist = renderer.getPlaylist();
            if (playlist != null)
                track_index = Integer.toString(playlist.getCurrentIndex());
            hash.put("Track",track_index);

            hash.put("RelCount","0");
            hash.put("AbsCount","0");
            hash.put("AbsTime","0:00");

            hash.put("TrackDuration",track == null ? "0:00" :
                Utils.durationToString(track.getDuration(),true));
            hash.put("TrackURI",track == null ? "" :
                track.getPublicUri());
            hash.put("RelTime",track == null ? "0:00" :
                Utils.durationToString(renderer.getPosition(),true));
            hash.put("TrackMetaData",track == null ? "" :
                track.getDidl());

            // DlnaUtils.dbg_hash_response = true;
            response = DlnaUtils.hash_response(http_server,urn,service,action,hash);
            // DlnaUtils.dbg_hash_response = false;
        }
        else if (action.equals("GetTransportSettings"))
        {
            Utils.log(dbg_av,0,"");
            hash.put("PlayMode",renderer.getPlayMode());
            hash.put("RecQualityMode","NOT_IMPLEMENTED");
            response = DlnaUtils.hash_response(http_server,urn,service,action,hash);
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
