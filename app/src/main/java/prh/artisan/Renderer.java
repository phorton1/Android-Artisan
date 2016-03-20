package prh.artisan;


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
    public String RENDERER_STATE_NONE            = "";
    public String RENDERER_STATE_STOPPED         = "STOPPED";
    public String RENDERER_STATE_PLAYING         = "PLAYING";
    public String RENDERER_STATE_TRANSITIONING   = "TRANSITIONING";
    public String RENDERER_STATE_PAUSED          = "PAUSED_PLAYBACK";

    public boolean startRenderer();
    public void stopRenderer();

    public Volume getVolume();

    public String getName();
    public String getRendererState();
    public String getRendererStatus();
    public String getPlayMode();
    public String getPlaySpeed();
    public int getTotalTracksPlayed();

    public boolean getShuffle();
    public boolean getRepeat();
    public void setRepeat(boolean value);
    public void setShuffle(boolean value);

    public void pause();
    public void play();
    public void stop();
    public void incAndPlay(int offset);

    public int getPosition();
    public void seekTo(int progress);

    public Track getTrack();
    public void setTrack(Track track, boolean interrupt_playlist);

    public Playlist getPlaylist();
    public void setPlaylist(Playlist playlist);

}   // base class Renderer



