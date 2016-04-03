package prh.base;

import prh.artisan.Track;
import prh.types.trackList;

public interface HelpablePlaylist
    // Playlist routines needed by openHomeHelper.
    // ServablePlaylists generally implement this Interface
    // so that they can use openHomeHelper to provide
    // the basic methods required by http.server.OpenPlaylist
{
    // this one is special

    trackList getTrackListRef();

    // these already on Playlist

    String getName();
    Track getTrack(int position);
    void saveIndex(int position);

    // these already on EditablePlaylist

    Track insertTrack(int position,Track track);
    boolean removeTrack(Track track);
}
