package prh.artisan;


import java.util.List;

import prh.server.utils.PlaylistExposer;
import prh.server.utils.UpnpEventManager;
import prh.types.recordList;
import prh.utils.Fetcher;
import prh.utils.Utils;
import prh.utils.httpUtils;


public interface Playlist extends Fetcher.FetcherSource
    // The interface to a Playlist as used by the UI
    // The open home OpenPlaylist only makes use of LocalPlaylists,
    // so all support for open home on playlists is found there
{
    boolean isLocal();

    String getName();
    int getNum();
    int getMyShuffle();
    int getCurrentIndex();
    int getNumTracks();

    void stop();
    void start();

    Track getCurrentTrack();
    Track getTrack(int index);
    Track incGetTrack(int inc);

    enum fetchHow
        // set by the client on a fetcher, available to the
        // the (Loca)Playlist in the call to getFetchRecords()
    {
        DEFAULT,
            // default fetch mode == fetch all Tracks
        WITH_ALBUMS,
            // returned recordList includes virtual Folders
            // created on-the-fly from the tracks in the playlist
        FOR_EXPOSE,
            // works in conjunction with the http.OpenPlaylist
            // who calls the fetcher explicitly, instead of in a loop.
            // return a recordList that selectively exposes
            // records, always including the current selected track_index,
            // and the records around it, until the whole list is
            // fetched.
    }
}
