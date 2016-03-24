package prh.artisan;


import java.util.List;

import prh.server.utils.PlaylistExposer;
import prh.server.utils.UpnpEventManager;
import prh.types.recordList;
import prh.utils.Utils;
import prh.utils.httpUtils;


public interface Playlist
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

    // pre-fetch scheme

    recordList getAvailableTracks();

}
