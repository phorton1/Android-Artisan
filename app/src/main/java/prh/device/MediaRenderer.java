package prh.device;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import prh.artisan.Artisan;
import prh.artisan.Prefs;
import prh.base.ArtisanEventHandler;
import prh.base.EditablePlaylist;
import prh.base.Renderer;
import prh.artisan.Track;
import prh.base.Volume;
import prh.artisan.VolumeControl;
import prh.utils.loopingRunnable;
import prh.device.service.RenderingControl;
import prh.utils.Utils;
import prh.types.stringHash;

// TODO: Implemenent as UpnpEventSubscriber
// TODO: Pref to use external MediaRenderer events

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

public class MediaRenderer extends Device implements
    Renderer,
    loopingRunnable.handler
{

    private static int dbg_mr = 0;

    private static int STOP_RETRIES = 20;
    private static int REFRESH_INTERVAL = 400;
    private static int DETECT_TRANSPORT_SECONDS = 3;
        // if the song_position is > this, or < duration-this
        // and the renderer is STOPPED, then we consider it
        // a user control, and stop the current playlist
    private static boolean GET_REMOTE_MEDIA_INFO = true;


    //------------------------------------------
    // VARIABLES
    //------------------------------------------

    private static int total_tracks_played = 0;
        // counter of tracks played

    private RenderingControl volume;
    private loopingRunnable my_looper;

    // Status and State

    private String renderer_status;
    private String renderer_state;
    private boolean repeat;
    private boolean shuffle;
    private int song_position;
        // The current position gotten from the renderer

    private Track current_track;
        // The current track playing on the remote renderer, which
        // may be the current track we are playing OVER the playlist,
        // the current track we are playing FROM the playlist,
        // or some other track completely local to the renderer.
    private Renderer.how_playing how_playing_track;
        // Describes how are playing the track, which for us
        // can only be IMMEDIATE or INTERNAL


    // separate, remote number of tracks and track index
    // and the mode boolean telling us how to report and advance

    private int remote_num_tracks;
    private int remote_track_num;
    private boolean use_external_playlist = false;

    // pending play scheme, on incAndPlay() for local files
    // being sent to remote, we delay for two updates loops
    // to give a settle time in case multiple next/previous
    // buttons are pressed

    private int do_play_on_next_update;

    // change detection used in getUpdateRenderer()

    private int last_position;
        // used to detect position and dispatch EVENT_POSITION_CHANGED
    private String last_track_uri;
        // used to detect track change on the remote renderer
    private int last_remote_num_tracks;
        // used to reset use_external_playlist if the number changes
    private String last_immediate_track_uri;


    //--------------------------------------------
    // construct, start, and stop
    //--------------------------------------------

    public MediaRenderer(Artisan artisan)
    {
        super(artisan);
        clean_init();
    }


    public MediaRenderer(Artisan artisan, SSDPSearchDevice ssdp_device)
    {
        super(artisan,ssdp_device);
        Utils.log(0,1,"new MediaRenderer(" +
            ssdp_device.getFriendlyName() + "," +
            ssdp_device.getDeviceType() + "," +
            ssdp_device.getDeviceUrl());
        clean_init();
    }


   private void clean_init()
    {
        volume = null;
        my_looper = null;

        renderer_status = "";
        renderer_state = RENDERER_STATE_NONE;
        repeat = true;
        shuffle = false;
        song_position = 0;

        current_track = null;
        how_playing_track = how_playing.INTERNAL;

        remote_num_tracks = 0;
        remote_track_num = 0;
        use_external_playlist = false;

        do_play_on_next_update = 0;

        last_position = 0;
        last_track_uri = "";
        last_remote_num_tracks = 0;
        last_immediate_track_uri = "";
    }


    @Override public boolean startRenderer()
    {
        Utils.log(dbg_mr,0,"MediaRenderer.startRenderer()");
        clean_init();

        // Hit the DLNA Renderer to make sure it is online,
        // Return false if it is not.

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
            // though using update_handler causes it to be on the same thread

            Updater updater = new Updater();
            my_looper = new loopingRunnable(
                "MediaRenderer(" + getFriendlyName() + ")",
                this,
                updater,
                STOP_RETRIES,
                REFRESH_INTERVAL,
                loopingRunnable.DEFAULT_USE_POST_DELAYED,
                this );
            my_looper.start();
        }

        Utils.log(dbg_mr,0,"MediaRenderer started ok=" + ok);
        return ok;
    }


    @Override public boolean continue_loop()
    // called by loopingRunnable
    {
        return getDeviceStatus() != deviceStatus.OFFLINE;
    }


    private class Updater implements Runnable
    {
        public void run()
        {
            getUpdateRendererState();
            if (my_looper != null && my_looper.continue_loop())
                artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_IDLE,null);
                    // TODO this goes away in new EVENT_IDLE architecture
        }
    }


    @Override public void stopRenderer(boolean wait_for_stop)
    // parent is responsible for sending out RENDERER_CHANGED event
    {
        Utils.log(dbg_mr,1,"MediaRenderer.stopRenderer()");

        remote_num_tracks = 0;
        remote_track_num = 0;
        use_external_playlist = false;
        how_playing_track = how_playing.INTERNAL;

        //---------------------------------
        // stop the looper
        //---------------------------------

        if (my_looper != null)
        {
            my_looper.stop(wait_for_stop);
            my_looper = null;
        }

        //--------------------------------------------------
        // get rid of any references to external objects
        //--------------------------------------------------

        current_track = null;

        // stop the volume control

        if (volume != null)
            volume.stop();
        volume = null;

        // reset all members to their default state

        last_track_uri = "";
        song_position = 0;
        renderer_status = "";

        // We used to be responsible for sending out the null VOLUME_CONFIG_CHANGED message
        // Now sent by Artisan when it changes renderers
        // artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_VOLUME_CONFIG_CHANGED,local_volume);

        Utils.log(dbg_mr,1,"MediaRenderer stopped");
    }


    //----------------------------------------
    // Renderer API
    //----------------------------------------

    @Override public String getRendererName()           { return getFriendlyName(); }
    @Override public Volume getVolume()                 { return volume; }
    @Override public String getRendererStatus()         { return renderer_status; }
    @Override public boolean getShuffle()               { return shuffle; }
    @Override public boolean getRepeat()                { return repeat; }
    @Override public int getTotalTracksPlayed()         { return total_tracks_played; }
    @Override public String getRendererState()          { return renderer_state; }
    @Override public int getPosition()                  { return song_position; }
    @Override public void setRepeat(boolean value)      { repeat = value; }
    @Override public void setShuffle(boolean value)     { shuffle = value; };
    @Override public boolean hasExternalPlaylist()      { return remote_num_tracks>1; };
    @Override public boolean usingExternalPlaylist()    { return use_external_playlist; };
    @Override public how_playing getHowPlaying()        { return how_playing_track; }


    @Override public void setUseExternalPlaylist(boolean b)
        // Let the client set the external mode based
        // on whether its possible, and a pref/
   {
        if (b && !hasExternalPlaylist())
        {
            Utils.log(0,0,"Client setUseExternalPlaylist(" + b + ") - no playlist - leaving false");
            return;
        }
        boolean use_b = b;
        if (Prefs.getHowExternalPlaylist() == Prefs.how_external_playlist.Never)
            use_b = false;

        if (use_external_playlist != b)
        {
            Utils.log(0,0,"Client setUseExternalPlaylist(" + b + ") setting to " + use_b);
            use_external_playlist = use_b;

            // The remote cannot detect a change to FALSE, and would
            // normally just keep playing it's playlist.
            // So, if the remote is playing a remote playlist and the
            // client turns the mode off, we call incAndPlay(0)
            // to start playing the local internal playlist if any ...
            // This is the only reasonable behavior

            if (artisan.getCurrentPlaylist().getNumTracks() > 0 &&
                how_playing_track == how_playing.EXTERNAL)
            {
                incAndPlay(0);
            }

        }
    }


    @Override public int getRendererTrackNum()
        // return the track number corresponding
        // to getRenderNumTracks()
    {
        return use_external_playlist ?
            remote_track_num :
            artisan.getCurrentPlaylist().getCurrentIndex();
    }


    @Override public int getRendererNumTracks()
    {
        return use_external_playlist ?
            remote_num_tracks :
            artisan.getCurrentPlaylist().getNumTracks();
    }


    @Override public Track getRendererTrack()
    {
        if (current_track != null)
            return current_track;
        return artisan.getCurrentPlaylist().
            getCurrentTrack();
    }


    @Override public void setRendererTrack(Track track,boolean unused_from_remote)
    {
        if (track != null)
        {
            Utils.log(dbg_mr,0,"setTrack(" + track.getTitle() + ")");
            current_track = track;
            use_external_playlist = false;
            last_immediate_track_uri = track==null ? "" : track.getPublicUri();
            Utils.log(1,1,"setTrack() calling play()");
            transport_play();
        }
    }


    public void incAndPlay(int inc)
        // inc(0) ==> initializing a new internal playlist
        // inc(1) ==> client call or upon advance of internal playlist
        // inc(-1) ==> client call only
    {
        if (renderer_state.equals(RENDERER_STATE_NONE))
            return;

        // if initializing a new internal playlist, turn of
        // use_external_playlist mode

        if (inc == 0)
            use_external_playlist = false;

        // Client call for Next/Prev on external playlist

        if (use_external_playlist)
        {
            Utils.log(dbg_mr,1,"external_incAndPlay(" + inc + ")");
            if (inc > 0)
                doCommand("Next",null);
            else if (inc < 0)
                doCommand("Previous",null);
        }

        // Normal call .. try to get a playlist track and play it
        // call transport_stop() if no playable track
        // otherwise use do_play_on_next_udpate scheme to play it.

        else
        {
            Utils.log(dbg_mr,1,"local_incAndPlay(" + inc + ")");

            EditablePlaylist current_playlist = artisan.getCurrentPlaylist();
            Track track = current_playlist.incGetTrack(inc);

            if (track == null)
            {
                if (!current_playlist.getName().equals(""))
                    Utils.noSongsMsg(current_playlist.getName());
                Utils.log(dbg_mr,1,"incAndPlay(" + inc + ") calling stop()");
                transport_stop();
            }
            else
            {
                Utils.log(dbg_mr,1,"incAndPlay(" + inc + ") calling play(" + track.getPosition() + ":" + track.getTitle() + ")");
                song_position = 0;
                current_track = track;
                do_play_on_next_update = 2;
            }

            // for better local behavior, if we can, we event the track right away
            // but defer the actual doAction(Play) network call till two more
            // calls to getUpdateState() by setting do_play_on_next_update=2

            artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_TRACK_CHANGED,track);
        }
    }


    //--------------------------------------------------------------------
    // AVTransport Actions
    //--------------------------------------------------------------------

    @Override public void seekTo(int position)
    {
        Utils.log(dbg_mr + 1,0,"seekTo(" + position + ") state=" + getRendererState());
        if (getRendererTrack() != null)
        {
            String time_str = Utils.durationToString(position,Utils.how_precise.FOR_SEEK);
            Utils.log(dbg_mr,0,"seekTo(" + position + "=" + time_str + ") state=" + getRendererState());
            stringHash args = new stringHash();
            args.put("Unit","REL_TIME");
            args.put("Target",time_str);
            doCommand("Seek",args);
        }
    }


    @Override public void transport_stop()
    {
        Utils.log(dbg_mr,0,"stop() state=" + getRendererState());
        if (!renderer_state.equals(RENDERER_STATE_NONE))
        {
            song_position = 0;
            current_track = null;
            doCommand("Stop",null);
        }
    }


    @Override public void transport_pause()
    {
        Utils.log(dbg_mr,0,"pause() state=" + getRendererState());
        if (renderer_state.equals(RENDERER_STATE_PLAYING))
            doCommand("Pause",null);
    }


    @Override public void transport_play()
    {
        Utils.log(dbg_mr,0,"play() state=" + getRendererState());

        // If using external playlist, skip all this
        // and just issue remote Play action.
        // Otherwise, in normal case, get the current_track
        // from the playlist and try playing it.

        if (!use_external_playlist)
        {
            Track track = current_track;
            if (current_track == null)
            {
                track = artisan.getCurrentPlaylist().
                    getCurrentTrack();
            }
            if (track == null)
            {
                Utils.error("nothing to play");
                return;
            }
            if (!Utils.supportedType(track.getType()))
            {
                Utils.error("Unsupported song type(" + track.getType() + ") " + track.getTitle());
                // transport_stop();
                return;
            }
            if (!renderer_state.equals(RENDERER_STATE_PAUSED))
            {
                stringHash args = new stringHash();
                args.put("CurrentURI",track.getPublicUri());
                args.put("CurrentURIMetaData",track.getDidl());
                doCommand("SetAVTransportURI",args);
            }
        }
        stringHash args = new stringHash();
        args.put("Speed","1");
        doCommand("Play",args);

    }   // transport_play()


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
    // Implementation
    //-----------------------------------------------

    private void setRendererState(String to_state)
    // any time the renderer state changes
    // other clients must be evented
    {
        renderer_state = to_state;
        Utils.log(0,0,renderer_state);
        artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_STATE_CHANGED,renderer_state);
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
                transport_play();
            return true;
        }

        // setup from current playlist

        EditablePlaylist current_playlist = artisan.getCurrentPlaylist();

        //--------------------------------------------
        // get info from remote renderer
        //--------------------------------------------

        stringHash args = new stringHash();
        args.put("InstanceID","0");
        Document transport_doc = doAction(Service.serviceType.AVTransport,"GetTransportInfo",args);
        if (transport_doc == null)
        {
            Utils.warning(0,0,"Could not get AVTransport::GetTransportState for " + getFriendlyName());
            deviceFailure();
            return false;
        }

        Element transport_ele = transport_doc.getDocumentElement();
        renderer_status = Utils.getTagValue(transport_ele,"CurrentTransportStatus");
        if (!renderer_status.equals("OK"))
        {
            Utils.warning(0,0,"Got non-ok status=" + renderer_status);
            // return false;
        }
        String new_state = Utils.getTagValue(transport_ele,"CurrentTransportState");

        // get the position and track_uri

        Document position_doc = doAction(Service.serviceType.AVTransport,"GetPositionInfo",args);
        if (position_doc == null)
        {
            Utils.warning(0,0,"Could not get AVTransport::GetPositionInfo for " + getFriendlyName());
            deviceFailure();
            return false;
        }

        deviceSuccess();

        Element position_ele = position_doc.getDocumentElement();
        String new_track_uri = Utils.getTagValue(position_ele,"TrackURI");
        String pos_str = Utils.getTagValue(position_ele,"RelTime");
        song_position = Utils.stringToDuration(pos_str);

        //---------------------------------------------
        // Detect position changes
        //---------------------------------------------

        boolean position_changed = false;
        if (last_position != song_position / 1000)
        {
            last_position = song_position / 1000;
            position_changed = true;
        }

        //--------------------------------------------------------------------
        // Detect track changes and set how_playing based on them
        //--------------------------------------------------------------------
        // we need to keep current_track for below ...

        boolean track_changed = false;
        if (!new_track_uri.equals(last_track_uri))
        {
            track_changed = true;
            last_track_uri = new_track_uri;

            how_playing_track = how_playing.INTERNAL;
            current_track = null;
            if (!new_track_uri.isEmpty())
            {
                String didl = Utils.getTagValue(position_ele,"TrackMetaData");
                didl = didl.replace("127.0.0.1",Utils.ipFromUrl(getDeviceUrl()));
                current_track = new Track(new_track_uri,didl);

                Track cur_pl_track = current_playlist.getCurrentTrack();
                if (new_track_uri.equals(last_immediate_track_uri))
                    how_playing_track = how_playing.IMMEDIATE;
                else if (cur_pl_track != null && cur_pl_track.getPublicUri().equals(new_track_uri))
                    how_playing_track = how_playing.INTERNAL;
                else
                    how_playing_track = how_playing.EXTERNAL;
            }
        }

        //---------------------------------------------------
        // Detect changes to number of external tracks
        //---------------------------------------------------
        // We don't detect if how_playing == IMMEDIATE, since
        // we forced the song onto the remote renderer, and
        // we don't want to lose the external NumberOfTracks
        // and TrackIndex

        if (how_playing_track != how_playing.IMMEDIATE)
        {
            remote_num_tracks = 0;
            remote_track_num = Utils.parseInt(Utils.getTagValue(position_ele,"Track"));

            if (GET_REMOTE_MEDIA_INFO)
            {
                Document media_doc = doAction(Service.serviceType.AVTransport,"GetMediaInfo",args);
                if (media_doc == null)
                    Utils.warning(0,0,"Could not get AVTransport::GetPositionInfo for " + getFriendlyName());
                else
                {
                    Element media_ele = media_doc.getDocumentElement();
                    remote_num_tracks = Utils.parseInt(Utils.getTagValue(media_ele,"NrTracks"));
                }
            }

            // if it is less than 2, turn off use_external_playlist,
            // otherwise if the number changed, clear, use the pref to
            // set the mode default value

            if (remote_num_tracks < 2)
            {
                if (use_external_playlist)
                    Utils.log(0,0,"setting use_external_playlist=FALSE  remote_num_tracks=" + remote_num_tracks);
                use_external_playlist = false;
            }
            else if (last_remote_num_tracks != remote_num_tracks)
            {
                int num_local = current_playlist.getNumTracks();

                Prefs.how_external_playlist pref = Prefs.getHowExternalPlaylist();

                boolean new_use_external =
                    pref == Prefs.how_external_playlist.First ||
                        (num_local == 0 &&
                            pref != Prefs.how_external_playlist.Never);

                if (use_external_playlist != new_use_external)
                {
                    Utils.log(0,0,"setting use_external_playlist=" + new_use_external + " remote_num_tracks=" + remote_num_tracks + " num_local=" + num_local);
                    use_external_playlist = new_use_external;
                }
            }
            last_remote_num_tracks = remote_num_tracks;

        }

        //----------------------------------------------------
        // Handle STOP on remote renderer
        //----------------------------------------------------
        // Only if there is a current_track from the remote,
        // we are not using an external_playlist or playing one
        // and only if how_playing == INTERNAL ...

        if (current_track != null &&
            !use_external_playlist &&
            how_playing_track != how_playing.EXTERNAL &&
            new_state.equals(RENDERER_STATE_STOPPED) &&
            !renderer_state.equals(RENDERER_STATE_STOPPED))
        {
            // If the last_position as in the middle of the song,
            // (not in our little windows at start and end)
            // we fall thru to just set our state to STOPPED to
            // match the remote.

            int cur_duration = current_track.getDuration()/1000;
            if (last_position > DETECT_TRANSPORT_SECONDS &&
                last_position < cur_duration - DETECT_TRANSPORT_SECONDS)
            {
                Utils.log(0,0,"MediaRenderer :: STOP DETECTED ON REMOTE RENDERER last_position=" + last_position + " cur_duration=" + cur_duration);
            }

            // Otherwise, its a full STOP on the remote renderer,
            // and we will push another song to it, and we are done
            // inAndPlay will dispatch a whole new set of events

            else
            {
                last_remote_num_tracks = 0;

                Utils.log(0,0,"MediaRenderer :: ADVANCE PLAYLIST");
                incAndPlay(1);      // advance the playlist (sends all needed events)
                return true;
            }

        }   // Remote Renderer changed to STOPPED while playing our playlist


        //----------------------------------------
        // dispatch events
        //----------------------------------------

        if (!renderer_state.equals(new_state))
            setRendererState(new_state);
        if (track_changed)
            artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_TRACK_CHANGED,current_track);
        else if (position_changed)
            artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_POSITION_CHANGED,new Integer(song_position));

        // Give the volume control a Timeslice

        VolumeControl volume_control = artisan.getVolumeControl();
        if (volume_control != null)
            volume_control.checkVolumeChangesForRenderer();

        // dispatch an IDLE event on our timer, for Artisan

        artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_IDLE,null);


        //--------------------------------------
        // finished
        //--------------------------------------

        return true;

    }   // MediaRenderer.getUpdateRendererState()


}   // class MediaRenderer
