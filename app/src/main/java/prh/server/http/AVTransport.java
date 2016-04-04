// DLNA Renderer

package prh.server.http;

// NOTE: BubbleUp does NOT notice that the track changed
// in getPositionInfo(), presumably because no RendererState
// change occurs during it's polling interval, and it does
// not use AVTransport Eventing (by default)

// TODO: May be able to detect resuming a running playlist from a remote
// if the uri matches the current playlist and NOT stop() the current playlist

import org.w3c.dom.Document;

import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.artisan.Track;
import prh.base.EditablePlaylist;
import prh.base.Renderer;
import prh.base.UpnpEventHandler;
import prh.device.LocalRenderer;
import prh.server.HTTPServer;
import prh.server.utils.UpnpEventSubscriber;
import prh.base.HttpRequestHandler;
import prh.server.utils.updateCounter;
import prh.types.stringHash;
import prh.utils.httpUtils;
import prh.utils.Utils;


public class AVTransport implements
    HttpRequestHandler,
    UpnpEventHandler
{

    private static int dbg_av = 0;

    private Artisan artisan;
    private HTTPServer http_server;
    private String urn;

    // BubbleUPnP Kludge
    //
    // BubbleUp does not notice tracks changing in GetPositionInfo.
    // In fact it only seems to notice tracks changing if it gets a
    // STOPPED state during it's polling hit to GetTransportInfo
    //
    // So, if the track changes on a BubbleUPnP client, we send out
    // one fake GetTransportInfo with STOPPED.

    stringHash last_bubbleup_uri = new stringHash();


    //-----------------------------------------------------------
    // ctor
    //-----------------------------------------------------------

    public AVTransport(Artisan ma,HTTPServer http,String the_urn )
    {
        artisan = ma;
        http_server = http;
        urn = the_urn;
    }


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
        if (local_renderer == null)
        {
            Utils.error("No LocalRenderer found in AVTransport.response()");
            return response;
        }
        if (local_renderer != artisan.getRenderer())
        {
            Utils.error("Only LocalRenderer allowed in AVTransport.response()");
            return response;
        }

        EditablePlaylist current_playlist = artisan.getCurrentPlaylist();
        Track track = local_renderer.getRendererTrack();

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
            Track new_track = new Track(cur_uri,metadata);
            local_renderer.setRendererTrack(new_track,true);
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
            String use_state =  local_renderer.getRendererState();

            // implementation of BubbleUPnP kludge

            String user_agent = session.getHeaders().get("user-agent");
            if (Utils.isBubbleUp(user_agent))
            {
                String uri = track == null ? "" : track.getPublicUri();
                String remote_ip = session.getHeaders().get("remote-addr");
                String ip_ua = remote_ip + ":" + user_agent;

                String old_uri = last_bubbleup_uri.get(ip_ua);
                if (old_uri == null || !old_uri.equals(uri))
                {
                    last_bubbleup_uri.put(ip_ua,uri);
                    Utils.log(dbg_av,0,"BubbleUPnP song changed: " + uri);
                    use_state = Renderer.RENDERER_STATE_STOPPED;
                }
            }

            // regular code

            hash.put("CurrentTransportState",use_state);
            hash.put("CurrentTransportStatus",local_renderer.getRendererStatus());
            hash.put("CurrentSpeed","1");
            response = httpUtils.hash_response(http_server,urn,service,action,hash);
        }
        else if (action.equals("GetPositionInfo"))
        {
            hash.put("Track",Integer.toString(
                current_playlist.getCurrentIndex()));

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
        else if (action.equals("GetMediaInfo"))
        {
            Utils.log(dbg_av,0,"");
            hash.put("NrTracks",Integer.toString(current_playlist.getNumTracks()));
            hash.put("CurrentURI",track == null ?
                "" : track.getPublicUri());
            hash.put("CurrentURIMetaData",track == null ? "" :
                track.getDidl());

            // unsupported
            // hash.put("MediaDuration","");
            // hash.put("NextURI","");
            // hash.put("NextURIMetaData","");
            // hash.put("PlayMedium","");
            // hash.put("RecordMedium","");
            // hash.put("WriteStatus","");

            response = httpUtils.hash_response(http_server,urn,service,action,hash);
        }

        // We currently do not support GetCurrentTransportActions
        // Cannot seem to get BubbleUp Transport buttons to work agains
        // our playlist, prolly cuz BuP considers itself to have the ONLY playlist

        else
        {
             Utils.error("unknown/unsupported action(" + action + ") in AVTransport request");
        }

        return response;
    }




    //----------------------------------------
    // UPnP Event Dispatching
    //----------------------------------------
    // We rely on the underlyling LocalRenderer to dispatch
    // EVENT_TRACK_CHANGED or EVENT_STATE_CHANGED for incUpdateCount()

    private updateCounter update_counter = new updateCounter();

    @Override public String getName()      { return "AVTransport"; };
    @Override public int getUpdateCount()  { return update_counter.get_update_count(); }
    @Override public int incUpdateCount()  { return update_counter.inc_update_count(); }
    @Override public void notifySubscribed(UpnpEventSubscriber subscriber,boolean subscribe) {}
    @Override public void start()          { http_server.getEventManager().RegisterHandler(this) ;}
    @Override public void stop()           { http_server.getEventManager().UnRegisterHandler(this) ;}

    private Track last_track = null;
    private int track_update_count = 0;


    @Override public String getEventContent(UpnpEventSubscriber subscriber)
        // We do not send CurrentTransportActions
        // We send CurrenTrackDuration and Bup does not.
    {
        LocalRenderer local_renderer = artisan.getLocalRenderer();
        if (local_renderer == null)
        {
            Utils.warning(0,0,"No local_renderer in Event");
            return "";
        }
        if (local_renderer != artisan.getRenderer())
        {
            Utils.warning(0,0,"Cannot return Events for non-local Renderers");
            return "";
        }
        EditablePlaylist current_playlist = artisan.getCurrentPlaylist();

        // the client_update_count is known to be different than our update count
        // we only want to send them the changes that have occurred since client_update_count
        // particularly for the track meta data. Therefore we keep track of the last track,
        // and our update_count at the point it was last evented.

        Track track = local_renderer.getRendererTrack();
        if (track != last_track)
        {
            last_track = track;
            track_update_count = getUpdateCount();
        }
        int client_update_count  = subscriber.getUpdateCount();
        int client_event_num = subscriber.getEventNum();

        // only send the constant stuff on the 0th event

        String text = httpUtils.startSubEventText();
        if (client_event_num == 0)
        {
            text += httpUtils.subEventText("PresetNameList","FactoryDefaults",null);
            text += httpUtils.subEventText("CurrentPlayMode","",null);
            text += httpUtils.subEventText("CurrentSpeed","1",null);
        }

        // always send the state and status

        text += httpUtils.subEventText("TransportState",local_renderer.getRendererState(),null);
        text += httpUtils.subEventText("TransportStatus",local_renderer.getRendererStatus(),null);

        // only send track stuff if client out of date

        if (client_event_num == 0 || client_update_count < track_update_count)
        {
            String metadata = track == null ? "" : track.getMetadata();
            String encoded = httpUtils.encode_xml(
                httpUtils.start_didl() +
                metadata +
                httpUtils.end_didl());

            String uri = track == null ? "" : track.getPublicUri();
            int duration = track == null ? 0 : track.getDuration();
            String duration_str = Utils.durationToString(duration,Utils.how_precise.FOR_SEEK);

            text += httpUtils.subEventText("NumberOfTracks",Integer.toString(current_playlist.getNumTracks()),null);
            text += httpUtils.subEventText("CurrentTrack",Integer.toString(current_playlist.getCurrentIndex()),null);
            text += httpUtils.subEventText("CurrentTrackDuration",duration_str,null);
            text += httpUtils.subEventText("CurrentTrackURI",uri,null);
            text += httpUtils.subEventText("CurrentTrackMetaData",encoded,null);
            text += httpUtils.subEventText("AVTransportURI",uri,null);
            text += httpUtils.subEventText("AVTransportURIMetaData",encoded,null);
        }

        text += httpUtils.endSubEventText();

        // return it to the caller

        HashMap<String,String> hash = new HashMap<String,String>();
        // hash.put("InstanceID","0");
        hash.put("LastChange",httpUtils.encode_lite(text));
        String rslt = httpUtils.hashToXMLString(hash,true);
        return rslt;
    }



}   // class AVTransport
