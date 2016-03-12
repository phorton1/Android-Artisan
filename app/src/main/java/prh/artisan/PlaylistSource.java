package prh.artisan;


public abstract class PlaylistSource
{
    public abstract void start();
    public abstract void stop();

    public abstract String[] getPlaylistNames();
    public abstract Playlist getPlaylist(String name);

}
