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
    //
    // All Renderers present the same API to the rest of the
    // system. The API includes capabilities (i.e. playlists)
    // that not every implementation may provide.
    //
    // A renderer is ALWAYS SUPPOSED TO HAVE A PLAYLIST
    // and THEY GET ASSOCIATED TO THE OpenHomeServer() on
    // any http calls to OpenPlaylist.

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

    // A Renderer May have Track, TrackNumber, and NumberTracks
    // separate from any Playlist. See Prefs.PreferRemoteRendererNumTracks
    // for more info

    int getRendererTrackNum();
    int getRendererNumTracks();

    Track getRendererTrack();
    void setRendererTrack(Track track, boolean interrupt_playlist);


}   // base class Renderer



