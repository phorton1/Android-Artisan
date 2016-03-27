package prh.artisan;


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

}
