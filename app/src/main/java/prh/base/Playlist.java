package prh.base;


import prh.artisan.Track;

public interface Playlist
    // Generic Interface for Playlists.
    //
    // These is the most abstract Interface for a
    // playlist in Artisan.
    //
    // These get passed as parameters to a variety of
    // methods on a variety of objects.
    //
    // At this time there is no class that strictly
    // implements this class.  Even the LocalPlaylist
    // has class-specficic methods on it.
{
    public PlaylistSource getSource();

    public boolean startPlaylist();
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

    public int getNumAvailableTracks();
    public Track getTrack(int index);
        // Not all track are required to be available to
        // clients. Partially constructed Playlists can
        // return getNumAvailableTracks() < getNumTracks(),
        // at at NO TIME shall a client call getTrack() on
        // an unavailable index.
        //
        // Operations that act on a whole playlist, like saveAs(),
        // or that modify a playlist, like insertTrack() or removeTrack()
        // may only function when all tracks are available, and give
        // user level error messages when they are not.

    public void saveIndex(int index);
        // At a minimum this method shall set the track_index
        // returned by getCurrentIndex.  Where possible the
        // number shall be written persistently to the Playlist.

}   // interface Playlist

