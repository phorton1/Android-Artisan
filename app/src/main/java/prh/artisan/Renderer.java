package prh.artisan;


public abstract class Renderer
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
    public static String RENDERER_STATE_NONE            = "";
    public static String RENDERER_STATE_STOPPED         = "STOPPED";
    public static String RENDERER_STATE_PLAYING         = "PLAYING";
    public static String RENDERER_STATE_TRANSITIONING   = "TRANSITIONING";
    public static String RENDERER_STATE_PAUSED          = "PAUSED_PLAYBACK";

    public abstract void startRenderer();
    public abstract void stopRenderer();

    public abstract Volume getVolume();

    public abstract String getName();
    public abstract String getRendererState();
    public abstract String getRendererStatus();
    public abstract String getPlayMode();
    public abstract String getPlaySpeed();
    public abstract int getTotalTracksPlayed();

    public abstract boolean getShuffle();
    public abstract boolean getRepeat();
    public abstract void setRepeat(boolean value);
    public abstract void setShuffle(boolean value);

    public abstract void pause();
    public abstract void play();
    public abstract void stop();
    public abstract void incAndPlay(int offset);

    public abstract int getPosition();
    public abstract void seekTo(int progress);

    public abstract Track getTrack();
    public abstract void setTrack(Track track, boolean interrupt_playlist);

    public Playlist getPlaylist() { return null; }
        // clients should test the return value before using it
    public abstract void setPlaylist(Playlist playlist);

    public PlaylistSource getPlaylistSource() { return null; }
    public abstract void setPlaylistSource(PlaylistSource playlistsource);

}   // base class Renderer



