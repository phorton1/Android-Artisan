package prh.base;

import prh.artisan.Track;
import prh.server.utils.playlistExposer;

public interface ServablePlaylistHelper
    // These methods are the subset of the playlist methods
    // required by server.http.OpenPlaylist that are presented
    // by the openHomeHelper class. The rest of the methods
    // required by the http server are presented in the derived
    // ServablePlaylist Interface. ServablePlaylists usually
    // provide these methods by instantiating an openHomeHelper.
    //
    // If not for server.http.OpenPlaylist, none of these
    // methods would otherwise show up on a Playlist
{
    Track getByOpenId(int open_id);
    Track insertTrack(Track track, int after_id);
    Track insertTrack(int after_id, String uri, String metadata);
    boolean removeTrack(int open_id);

    Track seekByOpenId(int open_id);

    String getIdArrayString(playlistExposer exposer);
    String id_array_to_tracklist(int ids[]);
    int[] string_to_id_array(String id_string);
}
