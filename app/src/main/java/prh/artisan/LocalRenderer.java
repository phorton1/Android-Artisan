package prh.artisan;


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
import android.os.Handler;
import android.widget.Toast;

import prh.artisan.Artisan;
import prh.artisan.Playlist;
import prh.artisan.Renderer;
import prh.artisan.Track;
import prh.device.Device;
import prh.utils.Utils;

public class LocalRenderer extends Renderer implements
    MediaPlayer.OnErrorListener,
    MediaPlayer.OnCompletionListener
{
    private static int dbg_ren = 0;

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

    // RENDERER STATES map to DLNA states
    // These states correspond to the allowable DLNA states,
    // as well as those being used for the local control UI.
    //
    // Clients use the string versions of these.
    //
    // The UI only cares about certain things, like
    // if there is a station selected, and whether to
    // enable the slider and various transport controls.
    // It has higher level expectations, for example,
    // STOP should turn off the current station, as
    // well as stopping the playback, if appropriate.


    //------------------------------------------
    // VARIABLES
    //------------------------------------------

    private Artisan artisan;
    private MediaPlayer media_player;
    private LocalVolume local_volume;

    private int mp_state = MP_STATE_NONE;
    private String renderer_state = RENDERER_STATE_NONE;
    private int song_position = 0;
        // the current position within the song
        // as returned by the media player, reset
        // on transitions, and updated in updateState()

    private Track current_track = null;
    private Playlist current_playlist = new LocalPlaylist(null,"");
    private PlaylistSource current_playlist_source = null;
        // what's playing, or playable

    private boolean repeat = true;
    private boolean shuffle = false;
        // the openHome spec only calls for OFF and ON i
        // for shuffle and repeat, though I have a tri-state
        // shuffle in my playlist definition.

    private int total_tracks_played = 0;
        // count of total tracks played

    // Refresh Loop

    private Handler refresh_handler = new Handler();
    private updateState updater = new updateState();


    //--------------------------------------------
    // construct, start, and stop
    //--------------------------------------------

    public LocalRenderer(Artisan a)
    {
        artisan = a;
        Utils.log(dbg_ren+1,1,"new LocalRenderer()");
    }


    public void startRenderer()
    {
        Utils.log(dbg_ren,0,"LocalRenderer.startRenderer()");

        local_volume = new LocalVolume(artisan);
        local_volume.start();
        artisan.handleEvent(EventHandler.EVENT_VOLUME_CONFIG_CHANGED,local_volume);

        media_player = new MediaPlayer();
        media_player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        media_player.setOnErrorListener(this);
        media_player.setOnCompletionListener(this);
        mp_state = MP_STATE_IDLE;

        current_playlist_source = new LocalPlaylistSource();
        current_playlist_source.start();

        setRendererState(RENDERER_STATE_STOPPED);

        refresh_handler.postDelayed(updater,REFRESH_INTERVAL);
        Utils.log(dbg_ren,0,"LocalRenderer started");

    }


    public void stopRenderer()
        // parent is responsible for sending out RENDERER_CHANGED event
    {
        Utils.log(dbg_ren,1,"LocalRenderer.stopRenderer()");

        // stop the refresh handler

        if (refresh_handler != null)
        {
            refresh_handler.removeCallbacks(updater);
            refresh_handler = null;
        }
        updater = null;

        // get rid of any references to
        // extranal objects

        if (current_playlist_source != null)
            current_playlist_source.stop();
        current_playlist_source = null;
        current_playlist = null;
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

        // We are responsible for sending out the
        // null VOLUME_CONFIG_CHANGED message

        artisan.handleEvent(EventHandler.EVENT_VOLUME_CONFIG_CHANGED,local_volume);

        Utils.log(0,0,"LocalRenderer stopped");
    }


    //----------------------------------------
    // Renderer API
    //----------------------------------------

    public String getName()                   { return Device.DEVICE_LOCAL_RENDERER; };
    public Volume getVolume()                 { return local_volume; }
    public String getRendererStatus()         { return "OK"; }
    public String getPlayMode()               { return ""; }
    public String getPlaySpeed()              { return "1"; }
    public boolean getShuffle()               { return shuffle; }
    public boolean getRepeat()                { return repeat; }
    public int getTotalTracksPlayed()         { return total_tracks_played; }
    public String getRendererState()          { return renderer_state; }
    public int getPosition()                  { return song_position; }
    public void setRepeat(boolean value)      { repeat = value; }
    public void setShuffle(boolean value)     { shuffle = value; };
    public Playlist getPlaylist()             { return current_playlist; }
    public PlaylistSource getPlaylistSource() { return current_playlist_source; }
    public void setPlaylistSource(PlaylistSource source) { current_playlist_source = source; }
        // hmmm .. can set a remote playlist source into the local renderer?
        // otherwise, this method probably used only in the far flung future


    public Track getTrack()
        // all DLNA stuff and accessors to the
        // current track playing in the renderer
        // are thru this method.
    {
        if (current_track != null)
            return current_track;
        //if (current_playlist != null)
            return current_playlist.getCurrentTrack();
        //return null;
    }


    public void setTrack(Track track, boolean interrupt_playlist)
        // start playing the given track, possibly
        // interrupting the current playlist if from DLNA
        // call with null does nothing
    {
        if (track != null)
        {
            Utils.log(dbg_ren,0,"setTrack(" + track.getTitle() + ") interrupt=" + interrupt_playlist);
            // stop();
            if (interrupt_playlist)
                current_playlist = new LocalPlaylist(null,"");
            current_track = track;
            artisan.handleEvent(EventHandler.EVENT_TRACK_CHANGED,track);
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
            new LocalPlaylist(null,"");

        // if (current_playlist != null)
            current_playlist.start();
        artisan.handleEvent(EventHandler.EVENT_PLAYLIST_CHANGED,playlist);
        // if (current_playlist != null)
        {
            Utils.log(1,1,"setPlaylist() calling incAndPlay(0)");
            incAndPlay(0);
        }
    }



    public void incAndPlay(int inc)
        // does nothing if no playlist
    {
        Utils.log(dbg_ren,0,"incAndPlay(" + inc + ")");

        song_position = 0;

        Track track = null;
        // if (current_playlist != null)
        {
            current_track = null;
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
                play();
            }
        }
        // else
        //    Utils.log(dbg_ren,1,"incAndPlay(" + inc + ") no playlist - stopping");

        if (track == null)
            stop();

        artisan.handleEvent(EventHandler.EVENT_TRACK_CHANGED,track);
    }


    public void seekTo(int position)
    {
        if (mp_state != MP_STATE_ERROR &&
            mp_state != MP_STATE_IDLE &&
            mp_state != MP_STATE_STOPPED &&
            mp_state != MP_STATE_INITIALIZED)
        {
            artisan.handleEvent(EventHandler.EVENT_POSITION_CHANGED,new Integer(position));
            media_player.seekTo(position);
        }
    }


    public void stop()
    {
        Utils.log(dbg_ren,0,"stop() mp=" + getMediaPlayerState() + " rend=" + getRendererState());
        media_player.reset();
        mp_state = MP_STATE_IDLE;
        setRendererState(RENDERER_STATE_STOPPED);
        song_position = 0;

    }


    public void pause()
    {
        Utils.log(dbg_ren,0,"pause() mp=" + getMediaPlayerState() + " rend=" + getRendererState());
        if (mp_state == MP_STATE_STARTED)
        {
            media_player.pause();
            mp_state = MP_STATE_PAUSED;
            setRendererState(RENDERER_STATE_PAUSED);
        }
    }


    public void play()
    {
        Utils.log(dbg_ren,0,"play() mp=" + getMediaPlayerState() + " rend=" + getRendererState());
        Track track = current_track;
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
            stop();
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

    private void setRendererState(String to_state)
    // any time the renderer state changes
    // the OpenPlayList and other clients
    // must be evented
    {
        renderer_state = to_state;
        Utils.log(0,0,renderer_state);
        artisan.handleEvent(EventHandler.EVENT_STATE_CHANGED,renderer_state);
    }


    //------------------------------------------
    // local renderer
    //------------------------------------------


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




    //----------------------------------------------------
    // mediaPlayer handlers
    //----------------------------------------------------
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

        stop();
        return true;
    }


    //--------------------------------------------------
    // API Routines
    //--------------------------------------------------


    public void noSongsMsg()
    {
        String msg = "No supported songs types found in playlist '" + current_playlist.getName() + "'";
        Utils.error(msg);
        Toast.makeText(artisan.getApplicationContext(),msg,Toast.LENGTH_LONG).show();
    }





    //----------------------------------------------------------
    // refresh thread
    //----------------------------------------------------------
    // updates and events the song position
    // prh - stalled renderer detection

    int last_position = 0;

    private class updateState implements Runnable
    {
        public void run()
        {
            if (mp_state != MP_STATE_NONE)
            {
                if (mp_state != MP_STATE_ERROR)
                {
                    song_position = media_player.getCurrentPosition();
                    if (song_position < 0) song_position = 0;

                    // set the duration from media player into the track
                    // if (false)
                    // {
                    //     if (mp_state != MP_STATE_IDLE &&
                    //         mp_state != MP_STATE_INITIALIZED &&
                    //         current_playlist.getCurrentTrack() != null)
                    //         current_playlist.getCurrentTrack().setDuration(media_player.getDuration());
                    // }
                }
            }

            // event any changes by second

            if (last_position != song_position / 100)
            {
                last_position = song_position / 100;
                artisan.handleEvent(EventHandler.EVENT_POSITION_CHANGED,new Integer(song_position));
            }

            // dispatch any open home events
            // and re-call ourselves ourselves

            artisan.handleEvent(EventHandler.EVENT_IDLE,null);
            refresh_handler.postDelayed(this,REFRESH_INTERVAL);

        }   // updateState.run()
    }   // class updateState


}   // class LocalRenderer
