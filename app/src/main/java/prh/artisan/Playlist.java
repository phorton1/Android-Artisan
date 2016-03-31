package prh.artisan;


public interface Playlist
    // Interface for Playlists given out by PlaylistSources
    //
    // The CurrentPlaylist can be populated from one of these.
    // Only the CurrentPlaylist and PlaylistSource know about these.
    // The rest of the system uses the CurrentPlaylist, which just
    // happens to present the same API.
{
    public void startPlaylist();
    public void stopPlaylist(boolean wait_for_stop);

    public String getName();
    public int getPlaylistNum();
    public int getMyShuffle();
    public String getQuery();
    public int getNumTracks();
    public int getCurrentIndex();
    public Track getCurrentTrack();

    public boolean isDirty();
    public void setDirty(boolean b);

    public void saveIndex(int index);
        // On PlaylistSource?

    public Track getTrack(int index);


}   // interface Playlist

