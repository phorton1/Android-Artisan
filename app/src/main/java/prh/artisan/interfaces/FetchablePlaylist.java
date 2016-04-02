package prh.artisan.interfaces;

import prh.artisan.utils.Fetcher;
import prh.artisan.utils.PlaylistFetcher;

public interface FetchablePlaylist extends
    Playlist,
    PlaylistFetcher.PlaylistFetcherSource
{
    void setAssociatedPlaylist(Playlist new_playlist);
        // aPlaying calls this to inform the FetchablePlaylist that
        // it should take on the identity of the given playlist.
        // This is called, for example, when a (local) playlist is
        // selected via the Radio buttons, or when the user does a
        // saveAs.
        //
        // The playlist shall not be null, shall be started,
        // and may be empty.  Though tempting to use null to
        // indicate disassociation, prior to the system switching
        // FetchablePlaylists, the proper methodology is to call
        // stopPlaylist() on the FetchablePlaylist, and let it set
        // the associated playlist to null itself.
        //
        // The method needs to checks for an actual change in
        // the playlist, and may do nothing if it did not actually
        // change.  As with getPlaylistChangeId(), the selection
        // and scroll position will be reset by aPlaying.

    public int getPlaylistCountId();
        // Bump this if the essential identity of the playlist
        // changes out from under aPlaying, for example, if a
        // remote playlist clears itself, or changes it's name.
        // aPlaying will re-initialize itself to the new identity,

    public int getContentChangeId();
        // Bump this if only the contents of the playlist change,
        // aPlaying will try to retain the selection and the
        // cursor position, but will re-initialize the fetcher



}
