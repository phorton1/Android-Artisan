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

import prh.artisan.Artisan;
import prh.artisan.SystemPlaylist;
import prh.artisan.interfaces.EventHandler;
import prh.artisan.interfaces.Renderer;
import prh.artisan.Track;
import prh.artisan.interfaces.Volume;
import prh.artisan.VolumeControl;
import prh.utils.loopingRunnable;
import prh.server.SSDPServer;
import prh.utils.Utils;
import prh.utils.httpUtils;

public class LocalRenderer extends Device implements
    Renderer,
    loopingRunnable.handler,
    MediaPlayer.OnErrorListener,
    MediaPlayer.OnCompletionListener
{
    private static int dbg_ren = 0;

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

    private int mp_state = MP_STATE_NONE;
    private String renderer_state = RENDERER_STATE_NONE;
    private int song_position = 0;
        // the current position within the song
        // as returned by the media player, reset
        // on transitions, and updated in updateState()
    private Track current_track = null;
        // Renderers are always playling the SystemPlaylist,
        // but a Track can be pushed on top of it.
    private boolean repeat = true;
    private boolean shuffle = false;
        // the openHome spec only calls for OFF and ON i
        // for shuffle and repeat, though I have a tri-state
        // shuffle in my playlist definition.
    private int total_tracks_played = 0;
        // count of total tracks played
    int last_position = 0;
        // last position for change detection


    @Override public boolean isLocal()
    {
        return true;
    }


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


    @Override public Track getRendererTrack()
        // all DLNA stuff and accessors to the
        // current track playing in the renderer
        // are thru this method.
    {
        if (current_track != null)
            return current_track;
        //if (current_playlist != null)
            return artisan.getCurrentPlaylist().getCurrentTrack();
        //return null;
    }


    @Override public void setRendererTrack(Track track, boolean interrupt_playlist)
        // start playing the given track, possibly
        // interrupting the current playlist if from DLNA
        // call with null does nothing
    {
        if (track != null)
        {
            Utils.log(dbg_ren,0,"setTrack(" + track.getTitle() + ") interrupt=" + interrupt_playlist);
            // stop();
            if (interrupt_playlist)
                artisan.setPlaylist("",true);
            current_track = track;
            artisan.handleArtisanEvent(EventHandler.EVENT_TRACK_CHANGED,track);
            transport_play();
        }
    }


    @Override public void incAndPlay(int inc)
        // does nothing if no playlist
    {
        Utils.log(dbg_ren,0,"incAndPlay(" + inc + ")");

        song_position = 0;

        Track track = null;
         current_track = null;

        SystemPlaylist current_playlist = artisan.getCurrentPlaylist();
        current_playlist.incGetTrack(inc);
        track = current_playlist.getCurrentTrack();
        if (track == null)
        {
            if (!current_playlist.getName().equals(""))
                noSongsMsg();
        }
        else
        {
            Utils.log(dbg_ren,1,"incAndPlay(" + inc + ") got " + track.getPosition() + ":" + track.getTitle());
            transport_play();
        }

        if (track == null)
            transport_stop();
        artisan.handleArtisanEvent(EventHandler.EVENT_TRACK_CHANGED,track);
    }


    @Override public void seekTo(int position)
    {
        if (mp_state != MP_STATE_ERROR &&
            mp_state != MP_STATE_IDLE &&
            mp_state != MP_STATE_STOPPED &&
            mp_state != MP_STATE_INITIALIZED)
        {
            artisan.handleArtisanEvent(EventHandler.EVENT_POSITION_CHANGED,new Integer(position));
            media_player.seekTo(position);
        }
    }


    @Override public void transport_stop()
    {
        Utils.log(dbg_ren,0,"stop() mp=" + getMediaPlayerState() + " rend=" + getRendererState());
        media_player.reset();
        mp_state = MP_STATE_IDLE;
        setRendererState(RENDERER_STATE_STOPPED);
        song_position = 0;

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
        Track track = current_track;
        SystemPlaylist current_playlist = artisan.getCurrentPlaylist();
        if (current_track==null && current_playlist != null)
            track = current_playlist.getCurrentTrack();

        if (track == null)
        {
            Utils.error("nothing to play");
            return;
        }
        else if (!Utils.supportedType(track.getType()))
        {
            Utils.error("Unsupported song type(" + track.getType() + ") " + track.getTitle());
            transport_stop();
        }
        try
        {
            if (mp_state != MP_STATE_PAUSED)
            {
                media_player.reset();
                mp_state = MP_STATE_IDLE;
                String uri = track.getLocalUri();

                // ANDROID EMULATOR CANT PLAY WMA FILES !!!

                if (uri.startsWith("file://"))
                {
                    uri = uri.replace("file://","");
                    media_player.setDataSource(uri);
                }
                else // play a remote datafile by url
                {
                    Uri android_uri = Uri.parse(uri);
                    media_player.setDataSource(artisan,android_uri);
                }
                mp_state = MP_STATE_INITIALIZED;
                media_player.prepare();
                mp_state = MP_STATE_PREPARED;
            }
            media_player.start();
            mp_state = MP_STATE_STARTED;
            setRendererState(RENDERER_STATE_PLAYING);
            total_tracks_played++;

            // artisan.handleRendererEvent(EventHandler.EVENT_TRACK_CHANGED,track);
            // doesn't happen in play, which does not change the track

        }
        catch (Exception e)
        {
            Utils.error("Renderer::play() " + e);
        }
    }



    //-----------------------------------------------
    // Implementation
    //-----------------------------------------------

    @Override public void notifyPlaylistChanged()
        // called directly as needed
   {
        if (artisan.getCurrentPlaylist().getNumTracks()>0)
            incAndPlay(0);
    }

    public void noSongsMsg()
    {
        String msg = "No supported songs types found in playlist '" +
            artisan.getCurrentPlaylist().getName() + "'";
        Utils.error(msg);
        Toast.makeText(artisan.getApplicationContext(),msg,Toast.LENGTH_LONG).show();
    }


    private void setRendererState(String to_state)
    // any time the renderer state changes
    // the OpenPlayList and other clients
    // must be evented
    {
        renderer_state = to_state;
        Utils.log(0,0,renderer_state);
        artisan.handleArtisanEvent(EventHandler.EVENT_STATE_CHANGED,renderer_state);
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
    // have to be public, but they're not called by me

    @Override
    public void onCompletion(MediaPlayer mp)
    {
        Utils.log(dbg_ren,0,"onCompletion()");
        mp_state = MP_STATE_COMPLETED;
        song_position = 0;
        current_track = null;
        incAndPlay(1);
    }

    @Override
    public boolean onError(MediaPlayer mp,int what,int extra)
    {
        String msg = "";

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
    // prh - stalled renderer detection


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
                artisan.handleArtisanEvent(EventHandler.EVENT_POSITION_CHANGED,new Integer(song_position));
            }

            // Hit the volume control, which will check if volumes
            // need to be re-gotten, etc.

            VolumeControl volume_control = artisan.getVolumeControl();
            if (volume_control != null)
                volume_control.checkVolumeChangesForRenderer();

            // Currently acts as the main idle loop for Artisan

            artisan.handleArtisanEvent(EventHandler.EVENT_IDLE,null);

        }   // updateState.run()
    }   // class updateState


}   // class LocalRenderer
