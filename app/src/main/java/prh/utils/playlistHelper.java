package prh.utils;

import prh.base.Playlist;
import prh.artisan.Track;

public class playlistHelper
{
    public static Track incGetTrack(Playlist playlist, int inc)
    // loop thru tracks till we find a playable
    // return null on errors or none found
    {
        int num_tracks = playlist.getNumTracks();
        int start_index = playlist.getCurrentIndex();
        int track_index = incIndex(num_tracks,start_index,inc);

        Track track = playlist.getTrack(track_index);
        if (track == null)
            return null;

        while (!Utils.supportedType(track.getType()))
        {
            if (inc != 0 && track_index == start_index)
            {
                Utils.error("No playable tracks found");
                track_index = 0;
                playlist.saveIndex(track_index);
                return null;
            }
            if (inc == 0) inc = 1;
            track_index = incIndex(num_tracks,track_index,inc);
            track = playlist.getTrack(track_index);
            if (track == null)
                return null;
        }

        playlist.saveIndex(track_index);
        return track;
    }


    private static int incIndex(int num_tracks, int track_index, int inc)
    {
        track_index = track_index + inc;    // one based
        if (track_index > num_tracks)
            track_index = 1;
        if (track_index <= 0)
            track_index = num_tracks;
        if (track_index > num_tracks)
            track_index = num_tracks;
        return track_index;
    }

}
