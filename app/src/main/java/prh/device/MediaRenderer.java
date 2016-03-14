package prh.device;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.HashMap;

import prh.artisan.Artisan;
import prh.artisan.EventHandler;
import prh.artisan.LocalPlaylist;
import prh.artisan.LocalPlaylistSource;
import prh.artisan.LocalVolume;
import prh.artisan.Playlist;
import prh.artisan.PlaylistSource;
import prh.artisan.Renderer;
import prh.artisan.Track;
import prh.artisan.Volume;
import prh.artisan.VolumeControl;
import prh.device.service.RenderingControl;
import prh.utils.Utils;
import prh.utils.stringHash;



public class MediaRenderer extends Device implements Renderer
    // Represents a DLNA AVTransport, with a possible
    // DLNA RenderingControl for volume changes
{

    public MediaRenderer(Artisan artisan, String friendlyName, String device_type, String device_url)
    {
        super(artisan,friendlyName,device_type,device_url);
        Utils.log(0,0,"new MediaRenderer(" + friendlyName + "," + device_type + "," + device_url);
    }


    private static int dbg_mr = 0;
    private static int REFRESH_INTERVAL = 400;
        // actually the delay between refreshes

    //------------------------------------------
    // VARIABLES
    //------------------------------------------

    private RenderingControl volume;

    // State

    private String renderer_state = RENDERER_STATE_NONE;
    private int total_tracks_played = 0;

    private int song_position = 0;
    private Track current_track = null;
    private Playlist current_playlist = new LocalPlaylist();
    private PlaylistSource current_playlist_source = null;
    private boolean repeat = true;
    private boolean shuffle = false;

    // Refresh Loop

    private Updater updater = null;
    private Handler update_handler = null;
    boolean quitting = false;
    private boolean in_update = false;
    int last_position = 0;
    String last_track_uri = "";

    // nearly constants

    private String play_mode = "";
    private String play_speed = "";
    private String renderer_status = "";


    //--------------------------------------------
    // construct, start, and stop
    //--------------------------------------------


    public boolean startRenderer()
    {
        Utils.log(dbg_mr,0,"MediaRenderer.startRenderer()");

        // Hit the DLNA Renderer to make sure it is online,
        // Return false if it is not.

        setRendererState(RENDERER_STATE_STOPPED);
        Boolean ok = getUpdateRendererState();

        if (ok)
        {
            // If it has an RenderingControl, the RenderingControl
            // implements the Volume Interface, so set it as the
            // Volume object

            volume = (RenderingControl) getServices().get("RenderingControl");
            if (volume != null)
            {
                volume.start(); // should be boolean start()
                int values[] = volume.getValues();
                if (values[Volume.CTRL_VOL]==0)
                {
                    volume = null;  // so there!
                    Utils.warning(0,0,"Turning off volume control with no MAX_VOL");
                }
            }

            // This DLNA MediaRenderer uses the LocalPlaylistSource
            // so we can jam Playlists down its throat

            current_playlist_source = new LocalPlaylistSource();
            current_playlist_source.start();
            current_playlist = new LocalPlaylist();

            // start the update handler on a separate thread
            // not currently working (timer does not advance)

            if (false)
            {
                UpdateThread updateThread = new UpdateThread();
                Thread update_thread = new Thread(updateThread);
                update_thread.start();
            }
            else    // old way
            {
                update_handler = new Handler();
                updater = new Updater();
                update_handler.postDelayed(updater,REFRESH_INTERVAL);
            }

        }

        Utils.log(dbg_mr,0,"MediaRenderer started ok=" + ok);
        return ok;
    }



    private class UpdateThread extends Thread
    {
        public void run()
        {
            Looper.prepare();
            update_handler = new Handler();
            updater = new Updater();
            update_handler.postDelayed(updater,REFRESH_INTERVAL);
        }
    }


    public void stopRenderer()
    // parent is responsible for sending out RENDERER_CHANGED event
    {
        Utils.log(dbg_mr,1,"MediaRenderer.stopRenderer()");

        // stop the refresh handler

        quitting = true;
        while (updater != null &&
              update_handler != null &&
              in_update )
        {
            Utils.log(dbg_mr,2,"waiting for MediaRenderer Updater() to stop()");
            Utils.sleep(100);
        }
        in_update = false;
        quitting = false;

        if (update_handler != null)
            update_handler.removeCallbacks(updater);
        update_handler = null;
        updater = null;

        Utils.log(dbg_mr,2,"Updater() stopped");

        // get rid of any references to
        // extranal objects

        if (current_playlist_source != null)
            current_playlist_source.stop();
        current_playlist_source = null;
        current_playlist = null;
        current_track = null;

        // stop the volume control

        if (volume != null)
            volume.stop();
        volume = null;

        // reset all members to their default state

        last_track_uri = "";
        song_position = 0;
        play_mode = "";
        play_speed = "";
        renderer_status = "";

        // We used to be responsible for sending out the null VOLUME_CONFIG_CHANGED message
        // Now sent by Artisan when it changes renderers
        // artisan.handleArtisanEvent(EventHandler.EVENT_VOLUME_CONFIG_CHANGED,local_volume);

        Utils.log(dbg_mr,1,"MediaRenderer stopped");
    }


    //----------------------------------------
    // Renderer API
    //----------------------------------------

    public String getName()                   { return getFriendlyName(); }
    public Volume getVolume()                 { return volume; }
    public String getRendererStatus()         { return renderer_status; }
    public String getPlayMode()               { return play_mode; }
    public String getPlaySpeed()              { return play_speed; }
    public boolean getShuffle()               { return shuffle; }
    public boolean getRepeat()                { return repeat; }
    public int getTotalTracksPlayed()         { return total_tracks_played; }
    public Playlist getPlaylist()             { return current_playlist; }
    public PlaylistSource getPlaylistSource() { return current_playlist_source; }
    public String getRendererState()          { return renderer_state; }
    public int getPosition()                  { return song_position; }
    public void setRepeat(boolean value)      { repeat = value; }
    public void setShuffle(boolean value)     { shuffle = value; };

    // public void setPlaylistSource(PlaylistSource source) { current_playlist_source = source; }


    //-----------------------------------------------
    // Duplicated methods
    //-----------------------------------------------
    // These have same implementation as MediaRenderer, but
    // Renderer is an interface (as Device is more fundamental),
    // and cannot currently have default implementations



    public Track getTrack()
    {
        if (current_track != null)
            return current_track;
        if (current_playlist != null)
            return current_playlist.getCurrentTrack();
        return null;
    }


    public void setTrack(Track track, boolean interrupt_playlist)
        // start playing the given track, possibly
        // interrupting the current playlist if from DLNA
        // call with null does nothing
    {
        if (track != null)
        {
            Utils.log(dbg_mr,0,"setTrack(" + track.getTitle() + ") interrupt=" + interrupt_playlist);
            // stop();
            if (interrupt_playlist)
                current_playlist = new LocalPlaylist();
            current_track = track;
            Utils.log(1,1,"setTrack() calling play()");
            play();
        }
    }


    public void setPlaylist(Playlist playlist)
        // it is assumed that artisan will save the previous
        // playlist as necessary, and call start() on the new one.
        // This just sets it and starts playing it.
    {
        String dbg_name = playlist != null ? playlist.getName() : "null";
        Utils.log(0,0,"setPlaylist(" + dbg_name + ")");
        if (current_playlist != null)
            current_playlist.stop();
        stop();
        current_track = null;
        current_playlist = playlist != null ? playlist :
            new LocalPlaylist();

        current_playlist.start();
        artisan.handleArtisanEvent(EventHandler.EVENT_PLAYLIST_CHANGED,playlist);
        Utils.log(1,1,"setPlaylist() calling incAndPlay(0)");
        incAndPlay(0);
    }



    public void incAndPlay(int inc)
    // does nothing if no playlist
    {
        Utils.log(dbg_mr,0,"incAndPlay(" + inc + ")");

        song_position = 0;

        current_track = null;
        current_playlist.incGetTrack(inc);
        Track track = current_playlist.getCurrentTrack();
        if (track == null)
        {
            if (!current_playlist.getName().equals(""))
                Utils.noSongsMsg(current_playlist.getName());
            Utils.log(dbg_mr,1,"incAndPlay(" + inc + ") calling stop()");
            stop();
        }
        else
        {
            stop();
            Utils.log(dbg_mr,1,"incAndPlay(" + inc + ") calling play(" + track.getPosition() + ":" + track.getTitle() +")");
            current_track = track;
            play();
        }
    }




    //--------------------------------------------------------------------
    // AVTransport DLNA Actions
    //--------------------------------------------------------------------


    public void seekTo(int position)
    {
        Utils.log(dbg_mr+1,0,"seekTo(" + position + ") state=" + getRendererState());
        if (getTrack() != null)
        {
            String time_str = Utils.durationToString(position,Utils.how_precise.FOR_SEEK);
            Utils.log(dbg_mr,0,"seekTo(" + position + "=" + time_str + ") state=" + getRendererState());

            stringHash args = new stringHash();
            args.put("Unit","REL_TIME");
            args.put("Target",time_str);
            doCommand("Seek",args);
        }
    }


    public void stop()
    {
        Utils.log(dbg_mr,0,"stop() state=" + getRendererState());
        if (!renderer_state.equals(RENDERER_STATE_NONE))
            doCommand("Stop",null);
    }


    public void pause()
    {
        Utils.log(dbg_mr,0,"pause() state=" + getRendererState());
        if (renderer_state.equals(RENDERER_STATE_PLAYING))
            doCommand("Pause",null);
    }



    public void play()
    {
        Utils.log(dbg_mr,0,"play() state=" + getRendererState());
        Track track = current_track;
        if (current_track == null && current_playlist != null)
            track = current_playlist.getCurrentTrack();

        if (track == null)
        {
            Utils.error("nothing to play");
        }
        else if (!Utils.supportedType(track.getType()))
        {
            Utils.error("Unsupported song type(" + track.getType() + ") " + track.getTitle());
            stop();
        }
        else
        {
            if (!renderer_state.equals(RENDERER_STATE_PAUSED))
            {
                stringHash args = new stringHash();
                args.put("CurrentURI",track.getPublicUri());
                args.put("CurrentURIMetaData",track.getDidl());
                doCommand("SetAVTransportURI",args);
            }

            stringHash args = new stringHash();
            args.put("Speed",play_speed);
            doCommand("Play",args);

        }   // got a track to play
    }   // play()


    //-----------------------------------------------
    // low level doCommand()
    //-----------------------------------------------
    // Exists to add the <InstanceID> and call doAction

    private Document doCommand(String action,stringHash args)
        // Starts a (non-ui) thread to run the command
        // adds the pseudo-constant InstanceID=0
    {
        if (args == null)
            args = new stringHash();
        args.put("InstanceID","0");
        return doAction("AVTransport",action,args);
    }


    //-----------------------------------------------
    // UpdateState()
    //-----------------------------------------------
    // All Events (except for PlaylistChanged)
    // and state changes are recognized and evented
    // by the update loop ...


    private class Updater implements Runnable
        // Runs in a separate thread
        // Starts the refresh, etc
    {
        public void run()
        {
            synchronized (this)
            {
                if (!quitting &&
                    updater != null &&
                    update_handler != null)
                {
                    in_update = true;
                    getUpdateRendererState();
                    artisan.handleArtisanEvent(EventHandler.EVENT_IDLE,null);   // prh !!!
                    if (!quitting &&
                        updater != null &&
                        update_handler != null)
                    {
                        update_handler.postDelayed(updater,REFRESH_INTERVAL);
                    }
                    in_update = false;
                }
            }
        }   // updateState.run()
    }   // class updateState



    private void setRendererState(String to_state)
    // any time the renderer state changes
    // other clients must be evented
    {
        renderer_state = to_state;
        Utils.log(0,0,renderer_state);
        artisan.handleArtisanEvent(EventHandler.EVENT_STATE_CHANGED,renderer_state);
    }


    public boolean getUpdateRendererState()
        // Get the renderer status, and if its Playing,
        // The position and metadata.
    {
        // get the state and status
        // event the state change, if any

        stringHash args = new stringHash();
        args.put("InstanceID","0");

        Document doc = doAction("AVTransport","GetTransportInfo",args);
        if (doc == null)
        {
            Utils.warning(0,0,"Could not get AVTransport::GetTransportState for " + getFriendlyName());
            return false;
        }

        Element doc_ele = doc.getDocumentElement();
        renderer_status = Utils.getTagValue(doc_ele,"CurrentTransportStatus");
        if (!renderer_status.equals("OK"))
        {
            Utils.warning(0,0,"Got non-ok status=" + renderer_status);
            return false;
        }

        String new_state = Utils.getTagValue(doc_ele,"CurrentTransportState");
        if (!new_state.equals(renderer_state))
            setRendererState(new_state);

        // get the position, and event any changes

        //if (renderer_state.equals(RENDERER_STATE_PLAYING) ||
        //    renderer_state.equals(RENDERER_STATE_PAUSED))

        doc = doAction("AVTransport","GetPositionInfo",args);
        if (doc == null)
        {
            Utils.warning(0,0,"Could not get AVTransport::GetPositionInfo for " + getFriendlyName());
            return false;
        }

        doc_ele = doc.getDocumentElement();
        String pos_str = Utils.getTagValue(doc_ele,"RelTime");
        song_position = Utils.stringToDuration(pos_str);
        if (last_position != song_position / 100)
        {
            last_position = song_position / 100;
            artisan.handleArtisanEvent(EventHandler.EVENT_POSITION_CHANGED,new Integer(song_position));
        }

        // get the uri, and if it changed build a track
        // from the metadata and event the track change/
        // Bup local library serves up 127.0.0.1, but works
        // at some other port ...

        String uri = Utils.getTagValue(doc_ele,"TrackURI");

        if (!uri.equals(last_track_uri))
        {
            Track track = null;
            last_track_uri = uri;
            if (!uri.isEmpty())
            {
                String didl = Utils.getTagValue(doc_ele,"TrackMetaData");
                didl = didl.replace("127.0.0.1",Utils.ipFromUrl(getDeviceUrl()));
                track = new Track(uri,didl);
            }
            current_track = track;
            artisan.handleArtisanEvent(EventHandler.EVENT_TRACK_CHANGED,track);
        }

        // Handle any "automatic" behaviours
        //
        //     advancing the current track
        //     detecting stalled renderer,
        //     checking for advance on DLNA renderer
        //

        // event any changes by second

        // Let the volume control see if needs
        // to get new values, etc

        VolumeControl volume_control = artisan.getVolumeControl();
        if (volume_control != null)
            volume_control.checkVolumeChangesForRenderer();

        // Dispatch the IDLE event for Artisan

        artisan.handleArtisanEvent(EventHandler.EVENT_IDLE,null);

        return true;
    }





}   // class MediaRenderer
