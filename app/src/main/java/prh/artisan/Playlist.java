package prh.artisan;


public interface Playlist
    // Interface for all Playlists
{
    // my playlist api

    public void startPlaylist();
    public void stopPlaylist(boolean wait_for_stop);
    public boolean isStarted();

    public boolean isDirty();
    public void setDirty(boolean b);

    // my playlist api - from database

    public String getPlaylistName();
    public int getPlaylistNum();
    public int getNumTracks();
    public int getCurrentIndex();
    public Track getCurrentTrack();
    public int getMyShuffle();
    public String getQuery();

    // my playlist api continued

    public int getContentChangeId();
    public void saveIndex(int index);

    public Track getPlaylistTrack(int index);
    public Track incGetTrack(int inc);
    public boolean removeTrack(Track track);
    public Track insertTrack(int position, Track track);

    // OpenHome Support

    public Track getByOpenId(int open_id);
    public Track insertTrack(int after_id, String uri, String metadata);
    public Track seekByIndex(int index);
    public Track seekByOpenId(int open_id);
    public Track insertTrack(Track track, int after_id);
    public boolean removeTrack(int open_id, boolean dummy_for_open_id);
;
    public String getIdArrayString(CurrentPlaylistExposer exposer);
    public int[] string_to_id_array(String id_string);
    public String id_array_to_tracklist(int ids[]);


}   // interface Playlist

