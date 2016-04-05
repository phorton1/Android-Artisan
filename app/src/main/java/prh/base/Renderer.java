package prh.base;


import prh.artisan.Track;

public interface Renderer
    // This is the base class for Renderers, that can play
    // music, have transport controls, etc.  These are
    // PROVIDERS of functionality that other code in this
    // program can hook up to.
    //
    // Derived classes include the prh.devices MediaRenderer
    // and OpenHomeRenderer, as well as the LocalRenderer.
    //
    // The LocalRenderer is also the provider for our own http
    // dlna MediaRenderer and OpenHome services.
{
    String RENDERER_STATE_NONE            = "";
    String RENDERER_STATE_STOPPED         = "STOPPED";
    String RENDERER_STATE_PLAYING         = "PLAYING";
    String RENDERER_STATE_TRANSITIONING   = "TRANSITIONING";
    String RENDERER_STATE_PAUSED          = "PAUSED_PLAYBACK";

    boolean startRenderer();
    void stopRenderer(boolean wait_for_stop);

    Volume getVolume();

    String getRendererName();
    String getRendererState();
    String getRendererStatus();
    int getTotalTracksPlayed();

    boolean getShuffle();
    boolean getRepeat();
    void setRepeat(boolean value);
    void setShuffle(boolean value);

    void transport_pause();
    void transport_play();
    void transport_stop();
    void incAndPlay(int offset);

    int getPosition();
    void seekTo(int progress);

    // A renderer may play a track in upto one
    // of three modes.  By default, renderers
    // play the INTERNAL current_playlist.
    //
    // They can locally be told to play an
    // IMMEDIATE track on on top of any existing
    // playlist.
    //
    // Furthermore, a remote renderer may be
    // playing an EXTERNAL track, that came
    // from the remote.


    enum how_playing
    {
        INTERNAL,
        IMMEDIATE,
        EXTERNAL
    }

    Track getRendererTrack();
    how_playing getHowPlaying();

    // A remote Renderer may discover that there
    // is an external playlist on the remote, based
    // on the NumberOfTracks>1 it returns.
    //
    // If so, there is a USE_EXTERNAL_PLAYLIST mode
    // available, which if set, causes the remote
    // Renderer to act differently, NOT auto-advancing
    // on STOP, and issuing Next, Previous, and Play
    // commands directly to the remote.
    //
    // There is a Pref for how to set the mode
    // immediately on detection of NumberOfTracks>1

    boolean hasExternalPlaylist();
    boolean usingExternalPlaylist();
    void setUseExternalPlaylist(boolean b);

    // These methods return the TrackNumber, and
    // Number of Tracks, based on the getHowPlaying()
    // and usingExternal() playlist values.
    //
    // In IMMEDIATE mode both return 0
    // In INTERNAL mode these return the values from the
    //     internal current_playlist
    // In EXTERNAL_MODE these return the
    //     number of tracks and track number from
    //     the remote.

    int getRendererTrackNum();
    int getRendererNumTracks();

    // Play a Track in IMMEDIATE MODE
    //
    // from_remote is set if the track is requested
    // via the http.AVTransport server. Otherwise it
    // was locally initiated.
    //
    // The local renderer makes decisions on whether
    // or not to resume an internal playlist after
    // the IMMEDIATE track based on this value.
    // It is otherwise, never true.

    void setRendererTrack(Track track, boolean from_remote);


}   // base class Renderer



