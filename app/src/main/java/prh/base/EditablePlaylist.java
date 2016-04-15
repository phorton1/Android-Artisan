package prh.base;

import prh.artisan.PlaylistFetcher;
import prh.artisan.Track;

public interface EditablePlaylist extends
    Playlist,
    PlaylistFetcher.PlaylistFetcherSource
    // An Editable Playlist is one that can be associated with
    // aPlaylist and can be advanced by Renderers.
    //
    // It not only presents editing functions like insertTrack() and
    // removeTrack(), but it also implements the PlaylistFetcherSource
    // interface.
{
    // Change detection for aPlaylist

    int getPlaylistCountId();
        // Bump this if the essential identity of the playlist
        // changes out from under aPlaylist, for example, if a
        // remote playlist clears itself, or changes it's name.
        // aPlaylist will re-initialize itself to the new identity,

    int getContentChangeId();
        // Bump this if only the contents of the playlist change,
        // aPlaylist will try to retain the selection and the
        // cursor position.

    // Playlist Manipulators required by aPlaylist

    public void setName(String new_name);
    Track insertTrack(int position, Track track);
    boolean removeTrack(Track track);
    public Track seekByIndex(int position);

    // called by Renderers

    public Track incGetTrack(int inc);

    // called by aLibrary when inserting records

    boolean suspendingEvents();
    void suspendEvents(boolean b);


}
