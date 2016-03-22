package prh.device;

import android.os.Handler;
import android.os.Looper;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import prh.artisan.Artisan;
import prh.artisan.EventHandler;
import prh.artisan.Playlist;
import prh.artisan.PlaylistSource;
import prh.artisan.Renderer;
import prh.artisan.Track;
import prh.artisan.Volume;
import prh.artisan.VolumeControl;
import prh.device.service.RenderingControl;
import prh.utils.Utils;
import prh.types.stringHash;



public class MediaRenderer extends Device implements Renderer
    // Represents a DLNA AVTransport, with a possible
    // DLNA RenderingControl for volume changes.
    //
    // Gets current state, track, position, if any, from remote renderer.
    // Basically advances playlist if remote_renderer is STOPPED.
    // Has special cases to detect changes made on remote_renderer:
    //
    //     Detects STOP, NEXT, and PREV buttons pressed using
    //          Pref.DETECT_MEDIARENDERER_CONTROLS and
    //          the song position is >= DETECT_TRANSPORT_MILLISECONDS and
    //          song_position < duration - DETECT_TRASNPORT_MILLISECONDS
    //
    //     Detects song change on remote_renderer from current track/playlist
    //          Gives up control (stops the current playlist) if it's not
    //          the expected track that we expect it to be playing
{

    public MediaRenderer(Artisan artisan, SSDPSearch.SSDPDevice ssdp_device)
    {
        super(artisan,ssdp_device);
        Utils.log(0,1,"new MediaRenderer(" + ssdp_device.getFriendlyName() + "," + ssdp_device.getDeviceType() + "," + ssdp_device.getDeviceUrl());
    }


    public MediaRenderer(Artisan artisan)
    {
        super(artisan);
    }


    private static int dbg_mr = 0;

    private static int REFRESH_INTERVAL = 400;
        // actually the delay between refreshes
    private static int DETECT_TRANSPORT_SECONDS = 3;
        // if the song_position is > this, or < duration-this
        // and the renderer is STOPPED, then we consider it
        // a user control, and stop the current playlist


    //------------------------------------------
    // VARIABLES
    //------------------------------------------

    private RenderingControl volume;

    // Status and State

    private String renderer_status = "";
    private String renderer_state = RENDERER_STATE_NONE;
    private boolean repeat = true;
    private boolean shuffle = false;

    // Currently Playing Item

    private int song_position = 0;
        // The current position gotten from the renderer

    private Track current_track = null;
        // The current track playing on the remote renderer, which
        // may be the current track we are playing OVER the playlist,
        // the current track we are playing FROM the playlist,
        // or some other track completely local to the renderer.
    private Playlist current_playlist = null;
        // The playlist, if any, that we are playing.

    // pseudo constants

    private String play_mode = "";
    private String play_speed = "1";

    // Refresh Loop Management

    private Updater updater = null;
    private Handler update_handler = null;
    boolean quitting = false;
    private boolean in_update = false;

    // change detection

    int last_position = 0;
    String last_track_uri = "";

    // counter of tracks played

    private int total_tracks_played = 0;

    // pending play

    private int do_play_on_next_update = 0;


    //--------------------------------------------
    // construct, start, and stop
    //--------------------------------------------


    public boolean startRenderer()
    {
        Utils.log(dbg_mr,0,"MediaRenderer.startRenderer()");

        current_playlist = artisan.createEmptyPlaylist();

        // Hit the DLNA Renderer to make sure it is online,
        // Return false if it is not.

        setRendererState(RENDERER_STATE_STOPPED);
        Boolean ok = getUpdateRendererState();

        if (ok)
        {
            // If it has an RenderingControl, the RenderingControl
            // implements the Volume Interface, so set it as the
            // Volume object

            volume = (RenderingControl) getServices().get(Service.serviceType.RenderingControl);
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
        // external objects

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
    public String getRendererState()          { return renderer_state; }
    public int getPosition()                  { return song_position; }
    public void setRepeat(boolean value)      { repeat = value; }
    public void setShuffle(boolean value)     { shuffle = value; };

    // public void setPlaylistSource(PlaylistSource source) { current_playlist_source = source; }


    //-----------------------------------------------
    // Duplicated methods
    //-----------------------------------------------
    // These have same or nearly same implementation as MediaRenderer,
    // but Renderer is an interface (as Device is more fundamental),
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
        // Start playing the given track in "immediate mode"
        // possibly interrupting the current playlist, if any,
        // in which case we substitute a new empty playlist
        // so as to stop things when the track finishes.
    {
        if (track != null)
        {
            Utils.log(dbg_mr,0,"setTrack(" + track.getTitle() + ") interrupt=" + interrupt_playlist);
            // stop();
            if (interrupt_playlist)
                current_playlist = artisan.createEmptyPlaylist();
            current_track = track;
            Utils.log(1,1,"setTrack() calling play()");
            play();
        }
    }


    public void setPlaylist(Playlist playlist)
        // stop the current playlist, if any, and
        // start the new one if not null
    {
        String dbg_name = playlist != null ? playlist.getName() : "null";
        Utils.log(0,0,"setPlaylist(" + dbg_name + ")");

        if (current_playlist != null)
            current_playlist.stop();

        stop();     // lower level?

        current_playlist = playlist != null ? playlist :
            artisan.createEmptyPlaylist();
        current_playlist.start();
        artisan.handleArtisanEvent(EventHandler.EVENT_PLAYLIST_CHANGED,playlist);

        if (playlist != null)
        {
            Utils.log(1,1,"setPlaylist() calling incAndPlay(0)");
            incAndPlay(0);
        }
    }



    public void incAndPlay(int inc)
    // does nothing if no playlist
    {
        Utils.log(dbg_mr,0,"incAndPlay(" + inc + ")");
        Track track = current_playlist.incGetTrack(inc);

        if (track == null)
        {
            if (!current_playlist.getName().equals(""))
                Utils.noSongsMsg(current_playlist.getName());
            Utils.log(dbg_mr,1,"incAndPlay(" + inc + ") calling stop()");
            stop();
        }
        else
        {
            // stop();
            Utils.log(dbg_mr,1,"incAndPlay(" + inc + ") calling play(" + track.getPosition() + ":" + track.getTitle() +")");
            song_position = 0;
            current_track = track;

            // for better local behavior with Next/Prev buttons
            // we send the new track event right away for the UI

            artisan.handleArtisanEvent(EventHandler.EVENT_TRACK_CHANGED,track);

            // but defer the actual doAction(Play) network call till two more
            // calls to getUpdateState() by setting do_play_on_next_update=2

            do_play_on_next_update = 2;
                // play();
        }
    }




    //--------------------------------------------------------------------
    // AVTransport DLNA Actions
    //--------------------------------------------------------------------


    public void seekTo(int position)
    {
        Utils.log(dbg_mr + 1,0,"seekTo(" + position + ") state=" + getRendererState());
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
        {
            song_position = 0;
            current_track = null;
            current_playlist = null;
            doCommand("Stop",null);
        }
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
        return doAction(Service.serviceType.AVTransport,action,args);
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
        // short ending if there's pending play

        if (do_play_on_next_update > 0)
        {
            do_play_on_next_update--;
            if (do_play_on_next_update == 0)
                play();
            return true;
        }

        //--------------------------------------------
        // get info from remote renderer
        //--------------------------------------------
        // the state and status

        stringHash args = new stringHash();
        args.put("InstanceID","0");
        Document doc = doAction(Service.serviceType.AVTransport,"GetTransportInfo",args);
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
            // return false;
        }
        String new_state = Utils.getTagValue(doc_ele,"CurrentTransportState");

        // get the position and track_uri

        doc = doAction(Service.serviceType.AVTransport,"GetPositionInfo",args);
        if (doc == null)
        {
            Utils.warning(0,0,"Could not get AVTransport::GetPositionInfo for " + getFriendlyName());
            return false;
        }

        doc_ele = doc.getDocumentElement();
        String pos_str = Utils.getTagValue(doc_ele,"RelTime");
        song_position = Utils.stringToDuration(pos_str);
        String new_track_uri = Utils.getTagValue(doc_ele,"TrackURI");

        //----------------------------------------------------
        // Detect Stops and/or Advance Playlist
        //----------------------------------------------------
        // OK, so now we know everything we need to know to
        // check for a unexpected song change on the rendererer,
        // see if they pressed STOP on the renderer, and/or
        // to advance the song automatically

        if (current_track != null && current_playlist != null)      // playlist is playing
        {
            int cur_duration = current_track.getDuration()/1000;
                // last_position is in SECONDS

            if (!new_track_uri.isEmpty() &&
                !new_track_uri.equals(current_track.getPublicUri()))
            {
                Utils.log(0,0,"MediaRenderer :: SONG CHANGE DETECTED ON REMOTE RENDERER");

                // give up control of the renderer by turning off our track/playlist.
                // of course, we don't want to STOP the renderer, since it's not ours anymore!

                song_position = 0;
                current_track = null;
                current_playlist = null;

                // event that the playlist has gone to null
                // the track and position changes will be evented below

                artisan.handleArtisanEvent(EventHandler.EVENT_PLAYLIST_CHANGED,null);
            }

            // Remote Renderer has stopped

            else if (new_state.equals(RENDERER_STATE_STOPPED) &&
                     !renderer_state.equals(RENDERER_STATE_STOPPED))
            {
                // Detect STOP button pressed on the Renderer
                // if the last_position we noticed was in our little window,
                // then we will advance, otherwise we will stop it

                if (last_position > DETECT_TRANSPORT_SECONDS &&
                    last_position < cur_duration - DETECT_TRANSPORT_SECONDS)
                {
                    Utils.log(0,0,"MediaRenderer :: STOP DETECTED ON REMOTE RENDERER last_position=" + last_position + " cur_duration=" + cur_duration);
                    setRendererState(RENDERER_STATE_STOPPED);
                    return true;
                }

                // normal advance of playlist

                else
                {
                    Utils.log(0,0,"MediaRenderer :: ADVANCE PLAYLIST");
                    incAndPlay(1);      // advance the playlist (sends all needed events)
                    return true;
                }

            }   // Remote Renderer changed to STOPPED while playlist playing
        }   // Playing a playlist


        //----------------------------------------------------
        // event any state, position or track changes
        //----------------------------------------------------

        if (!renderer_state.equals(new_state))
        {
            setRendererState(new_state);
        }

        if (last_position != song_position / 1000)
        {
            last_position = song_position / 1000;
            artisan.handleArtisanEvent(EventHandler.EVENT_POSITION_CHANGED,new Integer(song_position));
        }

        if (!new_track_uri.equals(last_track_uri))
        {
            Track track = null;
            last_track_uri = new_track_uri;
            if (!new_track_uri.isEmpty())
            {
                String didl = Utils.getTagValue(doc_ele,"TrackMetaData");
                didl = didl.replace("127.0.0.1",Utils.ipFromUrl(getDeviceUrl()));
                track = new Track(new_track_uri,didl);
            }
            current_track = track;
            artisan.handleArtisanEvent(EventHandler.EVENT_TRACK_CHANGED,track);
        }

        //----------------------------------------
        // Give the volume control a timeslice
        //----------------------------------------
        // To see if needs to get new values, etc

        VolumeControl volume_control = artisan.getVolumeControl();
        if (volume_control != null)
            volume_control.checkVolumeChangesForRenderer();

        // Dispatch the IDLE event for Artisan

        artisan.handleArtisanEvent(EventHandler.EVENT_IDLE,null);
        return true;

    }   // MediaRenderer.getUpdateRendererState()


}   // class MediaRenderer
