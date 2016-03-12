//----------------------------------------------
// OpenHome Playlist Actions
//----------------------------------------------

package prh.server.http;

import android.app.usage.UsageEvents;

import org.w3c.dom.Document;

import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;

import prh.artisan.EventHandler;
import prh.artisan.LocalPlaylist;
import prh.artisan.Playlist;
import prh.artisan.PlaylistSource;
import prh.artisan.Renderer;
import prh.artisan.Track;
import prh.server.HTTPServer;
import prh.utils.DlnaUtils;
import prh.utils.Utils;


public class OpenPlaylist implements OpenEventHandler
{
    private static int dbg_playlist = 0;

    private final static int MAX_TRACKS = 10000;
        // OpenPlaylists have a max

    private static String DEBUG_ACTION = ""; //"ReadList";
        // set this string to an action name and the
        // response for the action will be display
        // at debug level 0

    private Artisan artisan;
    private HTTPServer http_server;
    private OpenHomeServer open_home;

    public OpenPlaylist(Artisan ma, HTTPServer http, OpenHomeServer oh)
    {
        artisan = ma;
        http_server = http;
        open_home = oh;
    }

    public Playlist getRendererPlaylist()
        // set a point to the open home object
        // into the playlist for open home management.
        // It will be cleared upon playlist stop()
    {
        Renderer renderer = artisan.getRenderer();
        Playlist playlist = renderer==null ? null : renderer.getPlaylist();
        if (playlist == null)
        {
            Utils.error("unexpected Renderer without a playlist");
        }
        else
        {
            playlist.setOpenHome(open_home);
        }
        return playlist;
    }


    // Utilities

    public NanoHTTPD.Response error_response(HTTPServer server, int error, int data)
    {
        String msg = "ERROR " + error;
        NanoHTTPD.Response.IStatus status = NanoHTTPD.Response.Status.BAD_REQUEST;

        if (error == 800)
        {
            msg += " - item(" + data + ") not found";
            status = NanoHTTPD.Response.Status.BAD_PARAMETER;
        }
        if (error == 801)
        {
            msg += " - Playlist Full";
            status = NanoHTTPD.Response.Status.RESOURCES_EXHAUSTED;
       }
        Utils.error(msg);
        NanoHTTPD.Response response = server.newFixedLengthResponse(
            status,NanoHTTPD.MIME_PLAINTEXT,msg);
        return response;
    }


    //--------------------------------------------------------
    // actions
    //--------------------------------------------------------

    public NanoHTTPD.Response playlistAction(
        NanoHTTPD.Response response,
        Document doc,
        String urn,
        String service,
        String action)
    {
        boolean ok = true;
        boolean changed = false;
        HashMap<String,String> hash = new HashMap<String,String>();
        Renderer renderer = artisan.getRenderer();
        Playlist list = getRendererPlaylist();

        // get basic info

        if (action.equals("ProtocolInfo"))
        {
            hash.put("Value",getProtocolInfo());
        }
        else if (action.equals("TracksMax"))
        {
            hash.put("Value",Integer.toString(MAX_TRACKS));
        }
        else if (action.equals("TransportState"))
        {
            hash.put("Value",DLNAStateToOpenHomeState(renderer.getRendererState()));
        }
        else if (action.equals("Repeat"))
        {
            hash.put("Value",renderer.getRepeat() ? "1" : "0");
        }
        else if (action.equals("Shuffle"))
        {
            hash.put("Value",renderer.getShuffle() ? "1" : "0");
        }


        // get list of tracks/ids

        else if (action.equals("Id"))
        {
            Track track = renderer.getTrack();
            hash.put("Value",track == null ? "0" : Integer.toString(track.getOpenId()));
        }
        else if (action.equals("IdArray"))
        {
            hash.put("Token",Integer.toString(getUpdateCount()));
            hash.put("Array",list.getIdArrayString());
        }
        else if (action.equals("IdArrayChanged"))
        {
            int token = DlnaUtils.getXMLInt(doc,"Token",true);
            Utils.log(0,0,"token=" + token);
            hash.put("Value",token == getUpdateCount() ? "0" : "1");

        }
        else if (action.equals("Read"))
        {
            int open_id = DlnaUtils.getXMLInt(doc,"Id",true);
            Utils.log(0,0,"open_id=" + open_id);
            Track track = list.getByOpenId(open_id);
            if (track == null)
                return error_response(http_server,800,open_id);
            hash.put("Uri",track.getPublicUri());
            hash.put("Metadata",track.getDidl());
        }
        else if (action.equals("ReadList"))
        {
            int id_array[] = list.string_to_id_array(DlnaUtils.getXMLString(doc,"IdList",true));
            hash.put("TrackList",list.id_array_to_tracklist(id_array));
            list.start_expose_more_thread();
        }


        // playlist modifications

        else if (action.equals("DeleteAll"))
        {
            Playlist pl = new LocalPlaylist(null,"");
            pl.setOpenHome(open_home);
            renderer.setPlaylist(pl);
            changed = true;
        }
        else if (action.equals("DeleteId"))
        {
            // 800 if not in list
            int open_id = DlnaUtils.getXMLInt(doc,"Value",true);
            Utils.log(0,0,"open_id=" + open_id);
            if (!list.removeTrack(open_id))
                return error_response(http_server,800,open_id);
            changed = true;
        }
        else if (action.equals("Insert"))
        {
            // Reports a 800 fault code if AfterId is not 0 and doesn’t appear in the playlist.
            // Reports a 801 fault code if the playlist is full (i.e. already contains TracksMax tracks).
            int after_id = DlnaUtils.getXMLInt(doc,"AfterId",true);
            String uri = DlnaUtils.getXMLString(doc,"Uri",true);
            String data = DlnaUtils.getXMLString(doc,"Metadata",true);
            Utils.log(0,0,"after_id=" + after_id);
            Utils.log(0,0,"uri=" + uri);
            Utils.log(0,0,"data=" + data);

            // an attempt to "insert" a playlist will result from it being selected
            // from the "select_playlist" virtual folder, so we jam the playlist
            // into the renderer here, and let the return value be what it is

            String pattern = Utils.server_uri + "/dlna_server/select_playlist/";
            if (uri.startsWith(pattern))
            {
                String name = uri.replace(pattern,"");
                name = name.replace(".mp3","");
                Utils.log(0,0,"SELECTING PLAYLIST via Playlist Insert action");
                PlaylistSource source = renderer.getPlaylistSource();
                Playlist playlist = source.getPlaylist(name);
                playlist.setOpenHome(open_home);
                renderer.setPlaylist(playlist);
                hash.put("NewId","0");
            }
            else    // real playlist insert
            {
                int save_index = list.getCurrentIndex();
                Track track = list.insertTrack(after_id,uri,data);
                if (track == null)
                    return error_response(http_server,800,after_id);
                if (save_index != list.getCurrentIndex() ||
                    save_index == track.getPosition())
                {
                    artisan.handleEvent(EventHandler.EVENT_TRACK_CHANGED,track);
                }
                hash.put("NewId",Integer.toString(track.getOpenId()));
            }
            changed = true;
        }


        // state setters
        // not implemented yet

        else if (action.equals("SetRepeat"))
        {
            int value = DlnaUtils.getXMLInt(doc,"Value",true);
            Utils.log(0,0,"value=" + value);
            renderer.setRepeat(value > 0);
            changed = true;
        }
        else if (action.equals("SetShuffle"))
        {
            int value = DlnaUtils.getXMLInt(doc,"Value",true);
            Utils.log(0,0,"value=" + value);
            renderer.setShuffle(value > 0);
            changed = true;
        }


        // song seeks

        else if (action.equals("SeekId"))
        {
            int open_id = DlnaUtils.getXMLInt(doc,"Value",true);
            Utils.log(0,0,"open_id=" + open_id);
            if (open_id > 0)
            {
                Track track = list.seekByOpenId(open_id);
                Utils.log(0,0,"after list.seekByOpenId() track_index=" + list.getCurrentIndex());
                artisan.handleEvent(EventHandler.EVENT_TRACK_CHANGED,track);
                if (track == null)
                    return error_response(http_server,800,open_id);
                renderer.play();
            }
        }
        else if (action.equals("SeekIndex"))
        {
            int index = DlnaUtils.getXMLInt(doc,"Value",true);
            Utils.log(0,0,"index=" + index);
            if (index > 0)
            {
                Track track = list.seekByIndex(index);
                Utils.log(0,0,"after list.seekByIndex() track_index=" + list.getCurrentIndex());
                artisan.handleEvent(EventHandler.EVENT_TRACK_CHANGED,track);
                if (track == null)
                    return error_response(http_server,800,index);
                renderer.play();
            }
        }

        // time seeks

        else if (action.equals("SeekSecondAbsolute"))
        {
            int seconds = DlnaUtils.getXMLInt(doc,"Value",true);
            Utils.log(0,0,"seconds=" + seconds);
            renderer.seekTo(seconds * 1000);
        }
        else if (action.equals("SeekSecondRelative"))
        {
            int seconds = DlnaUtils.getXMLInt(doc,"Value",true);
            Utils.log(0,0,"seconds=" + seconds);
            int position = renderer.getPosition();
            position += seconds * 1000;
            renderer.seekTo(position);
        }

        // transport controls


        else if (action.equals("Next"))
        {
            renderer.incAndPlay(1);
            changed = true;
        }
        else if (action.equals("Previous"))
        {
            renderer.incAndPlay(-1);
            changed = true;
        }
        else if (action.equals("Pause"))
        {
            renderer.pause();
            changed = true;
        }
        else if (action.equals("Play"))
        {
            renderer.play();
            changed = true;
        }
        else if (action.equals("Stop"))
        {
            renderer.stop();
            changed = true;
        }


        // unknown action

        else
        {
            ok = false;
        }

        // finished

        if (ok)
        {
            if (action.equals(DEBUG_ACTION))
                DlnaUtils.dbg_hash_response = true;
            response = DlnaUtils.hash_response(http_server,urn,service,action,hash);
            DlnaUtils.dbg_hash_response = false;
        }
        if (changed)
            incUpdateCount();

        return response;
    }


    //---------------------------------------
    // common to actions and events
    //---------------------------------------

    private static String getProtocolInfo()
    {
        return "http-get:*:image/gif:*," +
            "http-get:*:image/jpeg:*," +
            "http-get:*:image/png:*," +
            "http-get:*:image/jpg:*," +
            "http-get:*:audio/mpeg:*," +
            "http-get:*:audio/m4a:*," +
            "http-get:*:audio/mp4:*," +
            "http-get:*:application/x-ms-wma:*," +
            "http-get:*:audio/wma:*," +
            "http-get:*:application/wma:*";
    };



    //----------------------------------------
    // Event Dispatching
    //----------------------------------------

    UpdateCounter update_counter = new UpdateCounter();
    public int getUpdateCount()  { return update_counter.get_update_count(); }
    public int incUpdateCount()  { return update_counter.inc_update_count(); }
    public String eventHandlerName() { return "Playlist"; };

    String DLNAStateToOpenHomeState(String dlna_state)
    {
        if (dlna_state.equals("STOPPED"))          return "Stopped";
        if (dlna_state.equals("PLAYING"))          return "Playing";
        if (dlna_state.equals("PAUSED_PLAYBACK"))  return "Paused";
        if (dlna_state.equals("TRANSITIONING"))    return "Buffering";
        return "";
    }


    public String getEventContent()
    {
        HashMap<String,String> hash = new HashMap<String,String>();
        Renderer renderer = artisan.getRenderer();
        Playlist list = getRendererPlaylist();
        Track track = list.getCurrentTrack();

        // following are "evented" xm

        hash.put("TransportState",DLNAStateToOpenHomeState(renderer.getRendererState()));
        hash.put("ProtocolInfo",getProtocolInfo());
        hash.put("TracksMax",Integer.toString(MAX_TRACKS));
        hash.put("Shuffle",renderer.getShuffle() ? "1" : "0");
        hash.put("Repeat",renderer.getRepeat() ? "1" : "0");
        hash.put("IdArray",list.getIdArrayString());
        hash.put("Id", track == null ? "0" : Integer.toString(track.getOpenId()));

        // state variables from XML that
        // are not in OH "state" variables list
        // are also not evented
        //
        // Absolute
        // Relative
        //
        // Index
        // Uri
        // Metadata
        //
        // IdList
        // IdArrayToken
        // IdArrayChanged
        // TrackList

        return DlnaUtils.hashToXMLString(hash,true);
    }



}   // class OpenPlaylist
