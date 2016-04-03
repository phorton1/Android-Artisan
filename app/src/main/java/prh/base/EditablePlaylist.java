package prh.base;

import prh.artisan.PlaylistFetcher;
import prh.artisan.Track;

public interface EditablePlaylist extends
    Playlist,
    PlaylistFetcher.PlaylistFetcherSource
    // An Editable Playlist is one that can be associated with
    // aPlaying and can be advanced by Renderers.
    //
    // It not only presents editing functions like insertTrack() and
    // removeTrack(), but it also implements the PlaylistFetcherSource
    // interface.
{
    // Change detection for aPlaying

    int getPlaylistCountId();
        // Bump this if the essential identity of the playlist
        // changes out from under aPlaying, for example, if a
        // remote playlist clears itself, or changes it's name.
        // aPlaying will re-initialize itself to the new identity,

    int getContentChangeId();
        // Bump this if only the contents of the playlist change,
        // aPlaying will try to retain the selection and the
        // cursor position.

    // Playlist Manipulators required by aPlaylist

    public void setName(String new_name);
    Track insertTrack(int position, Track track);
    boolean removeTrack(Track track);
    public Track seekByIndex(int position);

    // called by Renderers

    public Track incGetTrack(int inc);

}
