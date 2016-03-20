package prh.artisan;


public interface PlaylistSource
{
    String getName();

    boolean start();
    void stop();

    String[] getPlaylistNames();
    Playlist getPlaylist(String name);
        // by convention "" creates a new empty playlist

}
