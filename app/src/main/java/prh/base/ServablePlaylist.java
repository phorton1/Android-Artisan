package prh.base;

import prh.artisan.Track;

public interface ServablePlaylist extends ServablePlaylistHelper
    // This class represents a Playlist that can be
    // served by server.http.OpenPlaylist.
    //
    // The methods listed here are not present on
    // openPlaylistHelper, and must be actually implemented
    // by the given Playlist, which they normally do anyways.
{
    // these already exists on Playlist

    int getCurrentIndex();
    Track getTrack(int position);

    // these already exist on EditablePlaylist

    Track seekByIndex(int position);

}