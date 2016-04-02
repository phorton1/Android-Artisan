package prh.base;

// An Editable Playlist is one that can be associated with aPlaying.
//
// It not only presents editing functions like insertTrack() and
// removeTrack(), but it also implements the PlaylistFetcherSource
// interface.

import prh.artisan.PlaylistFetcher;
import prh.artisan.Track;

public interface EditablePlaylist extends
    Playlist,
    PlaylistFetcher.PlaylistFetcherSource
{

    int getPlaylistCountId();
        // Bump this if the essential identity of the playlist
        // changes out from under aPlaying, for example, if a
        // remote playlist clears itself, or changes it's name.
        // aPlaying will re-initialize itself to the new identity,

    int getContentChangeId();
        // Bump this if only the contents of the playlist change,
        // aPlaying will try to retain the selection and the
        // cursor position.

    Track insertTrack(int position, Track track);
        // ONE-BASED POSITION SIGNATURE
    boolean removeTrack(Track track);

    public Track seekByIndex(int position);
        // ONE-BASED POSITION SIGNATURE

    public void setName(String new_name);

    public Track incGetTrack(int inc);

}
