package prh.base;

import prh.artisan.Track;
import prh.server.utils.playlistExposer;

public interface ServablePlaylistHelper
    // These methods are the set of Playlist methods
    // required by server.http.OpenPlaylist, that are
    // presented by the openHomeHelper class.
    //
    // A ServablePlaylist usually uses a pass-thru
    // openHomeHelper to provide the actual implementation
    // of these methods.
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
