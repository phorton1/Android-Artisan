package prh.artisan.interfaces;


import prh.artisan.Track;

public interface Playlist
    // Generic Interface for Playlists.
    //
    // These are the most abstract playlists.
    //
    // For example, they are generically passed to PlaylistSource
    // saveAs(), which can write a new one, or overwrite an existing
    // one, based on this interface on this interface.
    //
    // There are special purpose derived Playlists which are
    // not, at some level, interchangeable. For example, the
    // LocalPlaylist is the only playlist that can be accessed
    // by our server.http.OpenPlaylist (which, interestingly is
    // NOT a Playlist), as it is the only one that contains
    // the outgoing open_home id_array routines, and like the
    // LocalLibrary, the only thing we serve to remote clients
    // (we don't do any pass thru serving).
    //
    // Likewise, the SystemPlaylist, which itself is a Playlist,
    // can copy itself from one these generic guys, and then
    // present it to the aPLaying UI as the current FetchablePlaylist.
    // The device.service.OpenPlaylist is the other FetchablePlaylist
    // in the system
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

