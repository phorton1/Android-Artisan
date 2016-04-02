package prh.base;


import prh.types.stringList;

public interface PlaylistSource
{
    String getPlaylistSourceName();

    boolean startPlaylistSource();
    void stopPlaylistSource(boolean wait_for_stop);

    stringList getPlaylistNames();
    Playlist getPlaylist(String name);
        // by convention "" creates a new empty playlist
    Playlist createEmptyPlaylist();
        // Creates a new, empty, started Playlist

    boolean saveAs(Playlist playlist, String name);
    boolean deletePlaylist(String name);

}
