package prh.device;


//--------------------------------------------
// Playing and auto-advancing
//--------------------------------------------
// The renderer can be playing a single ad-hoc local song
// which does not change the state of the current PlayList.
// When the song is done, or navigated out of, the underlying
// PlayList will resume.
//
// On the other hand, if the single song play request
// comes from a remote source, it MUST interrupt,
// and not resume the underlying playlist, because
// DLNA clients typically wait for the "STOPPED" state,
// and immediately request to play the next song, so
// it can mess things up if we resume the songlist,
// and start playing a different song.
//
// Therefore every attempt to play a single remote
// song (thru the DLNA Renderer) causes a switch
// to a new empty default PlayList.


import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;

import prh.artisan.Artisan;
import prh.artisan.Prefs;
import prh.base.ArtisanEventHandler;
import prh.base.EditablePlaylist;
import prh.base.Renderer;
import prh.artisan.Track;
import prh.base.Volume;
import prh.artisan.VolumeControl;
import prh.utils.complexTranscoder;
import prh.utils.loopingRunnable;
import prh.server.SSDPServer;
import prh.utils.Utils;
import prh.utils.httpUtils;
import prh.utils.simpleTranscoder;


public class LocalRenderer extends Device implements
    Renderer,
    loopingRunnable.handler,
    MediaPlayer.OnErrorListener,
    MediaPlayer.OnCompletionListener
{
    private static int dbg_ren = 0;

    private static boolean USE_COMPLEX_TRANSCODER = false;
        // which transcoder to use:
        // simple == transcode whole stream to file and hand it to media_player
        // complex == start a serverSocket and server it to media_player on http://local_host

    private static int STOP_RETRIES = 10;
    private static int REFRESH_INTERVAL = 200;
        // refresh the position pointer millieseconds
        // sends out an event on second changes

    // Theoretical internal state of the media_player,
    // according to the published state diagram.
    //
    // These states help me keep track of things.
    // Not all are "used" in that, some of these
    // states only exist transiently within single methods.
    // For example, we never actually leave the
    // media_player in a STOPPED state. In the implementation
    // we always advance the media_player to the IDLE
    // state, in accordance with the state diagram, in
    // a "ready state" in preparation for the next song.
    //
    // Clients should never use the media_player state,
    // though a string representation of it is available
    // for debugging or other display-only purposes.

    private static int MP_STATE_ERROR = -1;
    private static int MP_STATE_NONE = 0;          // mine, before media_player is constructed
    private static int MP_STATE_IDLE = 1;
    private static int MP_STATE_INITIALIZED = 1;
    private static int MP_STATE_PREPARING = 2;
    private static int MP_STATE_PREPARED = 3;
    private static int MP_STATE_STARTED = 4;
    private static int MP_STATE_PAUSED = 5;
    private static int MP_STATE_STOPPED = 6;
    private static int MP_STATE_COMPLETED = 7;

    //------------------------------------------
    // VARIABLES
    //------------------------------------------

    private MediaPlayer media_player;
    private LocalVolume local_volume;
    private loopingRunnable my_looper;

    // state

    private int mp_state = MP_STATE_NONE;
    private String renderer_state = RENDERER_STATE_NONE;
    private how_playing how_playing_track = how_playing.INTERNAL;

    private int song_position = 0;
    private Track current_track = null;
    private boolean repeat = true;
    private boolean shuffle = false;
    private int total_tracks_played = 0;

    // schemes

    private int last_position = 0;
        // last position for change detection
    private boolean immediate_from_remote = false;
        // boolean indicating if we are going to try
        // the restart playlist scheme
    loopingRunnable restart_playlist_delayer = null;
        // scheme to restart internal playlist after
        // immediate_from_remote stops after a delay

    // Device accessor

    @Override public boolean isLocal()  { return true;  }

    //--------------------------------------------
    // construct and start()
    //--------------------------------------------

    public LocalRenderer(Artisan a)
    {
        super(a);

        device_type = deviceType.LocalRenderer;
        device_group = deviceGroup.DEVICE_GROUP_RENDERER;
        device_uuid = SSDPServer.dlna_uuid[SSDPServer.IDX_DLNA_RENDERER];
        device_urn = httpUtils.upnp_urn;
        friendlyName = deviceType.LocalRenderer.toString();
        device_url = Utils.server_uri;
        icon_path = "/icons/artisan.png";
        device_status = deviceStatus.ONLINE;

        Utils.log(dbg_ren+1,1,"new LocalRenderer()");
    }


    @Override public boolean startRenderer()
    {
        Utils.log(dbg_ren,0,"LocalRenderer.startRenderer()");

        local_volume = new LocalVolume(artisan);
        local_volume.start();

        media_player = new MediaPlayer();
        media_player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        media_player.setOnErrorListener(this);
        media_player.setOnCompletionListener(this);
        mp_state = MP_STATE_IDLE;

        updateState updater = new updateState();
        my_looper = new loopingRunnable(
            "LocalRenderer",
            this,
            updater,
            STOP_RETRIES,
            REFRESH_INTERVAL,
            loopingRunnable.DEFAULT_USE_POST_DELAYED,
            this );
        my_looper.start();
        setRendererState(RENDERER_STATE_STOPPED);
        Utils.log(dbg_ren,0,"LocalRenderer started");
        return true;
    }


    //-----------------------------------
    // stopRenderer()
    //-----------------------------------

    @Override public void stopRenderer(boolean wait_for_stop)
        // parent is responsible for sending out RENDERER_CHANGED event
    {
        Utils.log(dbg_ren,1,"LocalRenderer.stopRenderer()");

        if (USE_COMPLEX_TRANSCODER)
            complexTranscoder.stopTranscoder();

        if (restart_playlist_delayer != null)
            restart_playlist_delayer.stop(false);
        restart_playlist_delayer = null;

        // stop the looper

        if (my_looper != null)
        {
            my_looper.stop(wait_for_stop);
            my_looper = null;
        }

        // forget the current track

        current_track = null;

        // stop the media_player and the volume control

        if (media_player != null)
        {
            media_player.release();
            media_player = null;
        }

        if (local_volume != null)
            local_volume.stop();
        local_volume = null;

        song_position = 0;
        last_position = 0;

        Utils.log(dbg_ren,0,"LocalRenderer stopped");
    }


    //----------------------------------------
    // Renderer API
    //----------------------------------------

    @Override public String getRendererName()           { return friendlyName; };
    @Override public Volume getVolume()                 { return local_volume; }
    @Override public String getRendererStatus()         { return "OK"; }
    @Override public boolean getShuffle()               { return shuffle; }
    @Override public boolean getRepeat()                { return repeat; }
    @Override public int getTotalTracksPlayed()         { return total_tracks_played; }
    @Override public String getRendererState()          { return renderer_state; }
    @Override public int getPosition()                  { return song_position; }
    @Override public void setRepeat(boolean value)      { repeat = value; }
    @Override public void setShuffle(boolean value)     { shuffle = value; };
    @Override public boolean hasExternalPlaylist()      { return false; };
    @Override public boolean usingExternalPlaylist()    { return false; };
    @Override public void setUseExternalPlaylist(boolean b) {}
    @Override public how_playing getHowPlaying()        { return how_playing_track; }


    @Override public int getRendererTrackNum()
    {
        return artisan.getCurrentPlaylist().getCurrentIndex();
    }

    @Override public int getRendererNumTracks()
    {
        return artisan.getCurrentPlaylist().getNumTracks();
    }


    @Override public Track getRendererTrack()
        // all DLNA stuff and accessors to the
        // current track playing in the renderer
        // are thru this method.
    {
        if (current_track != null)
            return current_track;
        return artisan.getCurrentPlaylist().getCurrentTrack();
    }


    @Override public void setRendererTrack(Track track, boolean from_remote)
        // start playing the given track, possibly
        // interrupting the current playlist if from DLNA
        // call with null does nothing
    {
        if (track != null)
        {
            Utils.log(dbg_ren,0,"setRendererTrack(" + track.getTitle() + ") from_remote=" + from_remote);

            if (restart_playlist_delayer != null)
                restart_playlist_delayer.stop(false);
            restart_playlist_delayer = null;

            current_track = track;
            immediate_from_remote = from_remote;
            how_playing_track = how_playing.IMMEDIATE;

            artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_TRACK_CHANGED,track);
            transport_play();
        }
    }


    @Override public void incAndPlay(int inc)
        // does nothing if no playlist
    {
        Utils.log(dbg_ren,0,"incAndPlay(" + inc + ")");

        // clear the position, how_playing, and
        // if inc == 0 the immediate_from_remote states

        song_position = 0;
        how_playing_track = how_playing.INTERNAL;
        if (inc == 0)
            immediate_from_remote = false;

        // Normal stuff

        EditablePlaylist current_playlist = artisan.getCurrentPlaylist();
        current_playlist.incGetTrack(inc);
        current_track = current_playlist.getCurrentTrack();

        if (current_track == null)
        {
            if (!current_playlist.getName().equals(""))
                noSongsMsg();
        }
        else
        {
            Utils.log(dbg_ren,1,"incAndPlay(" + inc + ") got " + current_track.getPosition() + ":" + current_track.getTitle());
            // setRendererState(RENDERER_STATE_TRANSITIONING);
            transport_play();
        }

        if (current_track == null)
            transport_stop();
        artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_TRACK_CHANGED,current_track);
    }


    @Override public void seekTo(int position)
    {
        if (mp_state != MP_STATE_ERROR &&
            mp_state != MP_STATE_IDLE &&
            mp_state != MP_STATE_STOPPED &&
            mp_state != MP_STATE_INITIALIZED)
        {
            artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_POSITION_CHANGED,new Integer(position));
            media_player.seekTo(position);
        }
    }


    @Override public void transport_stop()
    {
        Utils.log(dbg_ren,0,"stop() mp=" + getMediaPlayerState() + " rend=" + getRendererState());

        if (USE_COMPLEX_TRANSCODER)
            complexTranscoder.stopTranscoder();

        media_player.reset();
        mp_state = MP_STATE_IDLE;
        setRendererState(RENDERER_STATE_STOPPED);
        song_position = 0;
        immediate_from_remote = false;
    }


    @Override public void transport_pause()
    {
        Utils.log(dbg_ren,0,"pause() mp=" + getMediaPlayerState() + " rend=" + getRendererState());
        if (mp_state == MP_STATE_STARTED)
        {
            media_player.pause();
            mp_state = MP_STATE_PAUSED;
            setRendererState(RENDERER_STATE_PAUSED);
        }
    }


    @Override public void transport_play()
    {
        Utils.log(dbg_ren,0,"play() mp=" + getMediaPlayerState() + " rend=" + getRendererState());

        EditablePlaylist current_playlist = artisan.getCurrentPlaylist();
        if (current_track==null && current_playlist != null)
            current_track = current_playlist.getCurrentTrack();

        if (current_track == null)
        {
            Utils.error("nothing to play");
            return;
        }
        else if (!Utils.supportedType(current_track.getType()))
        {
            Utils.error("Unsupported song type(" + current_track.getType() + ") " + current_track.getTitle());
            transport_stop();
        }
        try
        {
            // Paused just goes back to playing
            // Otherwise, load the current_track
            // into the media_player

            if (mp_state != MP_STATE_PAUSED)
            {
                media_player.reset();
                mp_state = MP_STATE_IDLE;
                String uri = current_track.getLocalUri();

                boolean transcoding = false;
                if (Utils.needsTranscoding(current_track.getType()))
                {
                    transcoding = true;
                    if (USE_COMPLEX_TRANSCODER)
                        uri = complexTranscoder.startTranscoder(uri);
                    else
                        uri = simpleTranscoder.transcode(uri);
                    if (uri.isEmpty()) return;
                }

                media_player.setDataSource(uri);
                mp_state = MP_STATE_INITIALIZED;
                media_player.prepare();
                mp_state = MP_STATE_PREPARED;

                if (transcoding)
                {
                    File file = new File(uri);
                    file.delete();
                }
            }

            media_player.start();
            mp_state = MP_STATE_STARTED;
            setRendererState(RENDERER_STATE_PLAYING);
            total_tracks_played++;
        }
        catch (Exception e)
        {
            Utils.error("Renderer::play() " + e);
        }
    }


    //-----------------------------------------------
    // Implementation
    //-----------------------------------------------


    public void noSongsMsg()
    {
        String msg = "No supported songs types found in playlist '" +
            artisan.getCurrentPlaylist().getName() + "'";
        Utils.error(msg);
        Toast.makeText(artisan.getApplicationContext(),msg,Toast.LENGTH_LONG).show();
    }


    private void setRendererState(String to_state)
    {
        renderer_state = to_state;
        Utils.log(0,0,renderer_state);
        artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_STATE_CHANGED,renderer_state);
    }


    private String getMediaPlayerState()
    // get the media player state as a string
    // PUBLIC FOR INFORMATIONAL PURPOSES ONLY!
    {
        if (mp_state == MP_STATE_ERROR) return "ERROR";
        if (mp_state == MP_STATE_NONE) return "NONE";
        if (mp_state == MP_STATE_IDLE) return "IDLE";
        if (mp_state == MP_STATE_INITIALIZED) return "INITIALIZED";
        if (mp_state == MP_STATE_PREPARING) return "PREPARING";
        if (mp_state == MP_STATE_PREPARED) return "PREPARED";
        if (mp_state == MP_STATE_STARTED) return "STARTED";
        if (mp_state == MP_STATE_PAUSED) return "PAUSED";
        if (mp_state == MP_STATE_STOPPED) return "STOPPED";
        if (mp_state == MP_STATE_COMPLETED) return "COMPLETED";
        return "UNKNOWN STATE!";
    }


    // mediaPlayer handlers

    @Override
    public void onCompletion(MediaPlayer mp)
        // if transcoding, we have to let the transcoder call complete
    {
        Utils.log(dbg_ren,0,"onCompletion()"); // complexTranscoder.running()=" + complexTranscoder.running());

        if (!USE_COMPLEX_TRANSCODER || !complexTranscoder.running())
        {
            mp_state = MP_STATE_COMPLETED;

            if (immediate_from_remote)
            {
                int seconds = Prefs.getInteger(Prefs.id.RESUME_PLAYLIST_AFTER_REMOTE_TRACK_SECONDS);
                if (seconds > 0)
                {
                    Utils.log(dbg_ren,0,"onCompletion() starting delayed playlist advancer");
                    transport_stop();

                    if (restart_playlist_delayer != null)
                        restart_playlist_delayer.stop(false);

                    Runnable run_it = new Runnable()
                    {
                        public void run()
                        {
                            current_track = null;
                            incAndPlay(1);
                        }
                    };

                    restart_playlist_delayer = new loopingRunnable(
                        "restart_playlist_delayer",run_it,seconds * 1000);
                    restart_playlist_delayer.start();
                }
            }
            else
            {
                song_position = 0;
                current_track = null;
                incAndPlay(1);
            }
        }
    }



    @Override
    public boolean onError(MediaPlayer mp,int what,int extra)
    {
        String msg = "";

        if (USE_COMPLEX_TRANSCODER)
            complexTranscoder.stopTranscoder();

        switch (what)
        {
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                msg = "unknown media playback error";
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                msg = "server connection died";
            default:
                msg = "generic audio playback error";
                break;
        }

        switch (extra)
        {
            case MediaPlayer.MEDIA_ERROR_IO:
                msg = msg + ": IO media error";
                break;
            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                msg = msg + ": media error, malformed";
                break;
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                msg = msg + ": unsupported media content";
                break;
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                msg = msg + ": media timeout error";
                break;
            default:
                msg = msg + ": unknown playback error(" + what + ")";
                break;
        }
        mp_state = MP_STATE_ERROR;

        // report the error
        // for some reason getting spurios unknown -38 errors
        // so I am downgrading them to warnings

        if (what == -38)
        {
            Utils.warning(dbg_ren+1,0,"Renderer.onError(" + msg + ")");
        }
        else
        {
            Utils.error("Renderer.onError(" + msg + ")");
        }

        // stop everything

        transport_stop();
        return true;
    }


    //----------------------------------------------------------
    // updateState
    //----------------------------------------------------------
    // updates and events the song position
    // TODO: need to implement stalled renderer detection
    // TODO: need to implement transfer of playing song to new Renderer


    public boolean continue_loop()
    {
        return true;
    }


    private class updateState implements Runnable
    {
        public void run()
        {
            if (mp_state == MP_STATE_PAUSED ||
                mp_state == MP_STATE_STARTED)
            {
                song_position = media_player.getCurrentPosition();
                // if (song_position < 0) song_position = 0;
            }
            else
                song_position = 0;

            // event any changes by second

            if (last_position != song_position / 100)
            {
                last_position = song_position / 100;
                artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_POSITION_CHANGED,new Integer(song_position));
            }

            // Hit the volume control, which will check if volumes
            // need to be re-gotten, etc.

            VolumeControl volume_control = artisan.getVolumeControl();
            if (volume_control != null)
                volume_control.checkVolumeChangesForRenderer();

        }   // updateState.run()
    }   // class updateState


}   // class LocalRenderer
