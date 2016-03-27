package prh.server.utils;

import java.util.HashMap;

import prh.artisan.Artisan;
import prh.artisan.EventHandler;
import prh.device.LocalPlaylist;
import prh.artisan.Track;
import prh.server.http.OpenPlaylist;
import prh.utils.Utils;

public class PlaylistExposer
    // A class to incrementally expose a Playlist in our
    // OpenHome renderer service, for BubbleUp responsiveness.
    //
    // BubbleUpNp will "hang" if it is given an initial OpenHome
    // renderer Event with a large playlist id-array, as it then
    // turns around and calls ReadList to get the metadata, and images
    // for all the items, as a single synchronous action in it's UI.
    //
    // So, we incrementally expose the Playlist 20 records at a time,
    // and BubbleUp is then responsive after it calls ReadList for the
    // first 20 records. On the call to ReadList, we expose 20 more
    // records, and on the next Event, Bup will receive the bigger
    // id-array, and in turn, call ReadList again for the next 20
    // records.
    //
    // Bup behavior is still "chunky" when initializing large
    // playlists from an OpenHome renderer (OpenPlaylist), but this
    // helps.
{
    private static int dbg_expose = 0;

    private static int NUM_TO_EXPOSE = 20;
    // number that we expose in ExposeMore
    // set to the number BubbleUp normally asks for / 2
    private static int EXPOSE_MORE_SLEEP_TIMES = 6;
    private static int EXPOSE_MORE_SLEEP_MILLIS = 200;
        // how long ThreadedExposeMore sleeps before it
        // calls exposeMore()

    // types

    private class intBoolHash extends HashMap<Integer,Boolean> {}

    // instance variables

    private Artisan artisan;
    private String user_agent;
    private OpenPlaylist playlist;
    private int num_exposed = 0;
    private intBoolHash exposed;


    public PlaylistExposer(OpenPlaylist pl, Artisan ma, String ua)
    {
        playlist = pl;
        artisan = ma;
        user_agent = ua;
        exposed = new intBoolHash();
    }

    public String getUserAgent()
    {
        return user_agent;
    }

    public int getNumExposed()
    {
        return num_exposed;
    }

    public void clearExposedBits(LocalPlaylist playlist)
    {
        num_exposed = 0;
        exposed.clear();
    }

    public boolean isExposed(Track track)
    {
        return exposed.get(track.getOpenId()) != null;
    }

    public boolean exposeTrack(Track track,boolean set_it)
    {
        int id = track.getOpenId();
        if (set_it && exposed.get(id) == null)
        {
            num_exposed++;
            exposed.put(id,true);
            return true;
        }
        else if (!set_it && exposed.get(id) != null)
        {
            num_exposed--;
            exposed.remove(exposed.get(id));
            return true;
        }
        return false;
    }


    public boolean exposeMore(LocalPlaylist playlist)
        // think I should get the previous ones upto NUM_TO_EXPOSE/2 before the current track first
        // then to the end of the playlist, then from the beginning ... Bup playlist doesn't keep
        // the currentTrack in view if you stick things before it ...
    {
        Utils.log(dbg_expose,0,"expose_more() track_index=" + playlist.getCurrentIndex() + " num_exposed=" + num_exposed + " num_tracks=" + playlist.getNumTracks());
        int num = playlist.getNumTracks();
        int idx = playlist.getCurrentIndex();

        if (idx > 0 && num_exposed < num)
        {
            int offset = 0;
            int count_exposed = 0;
            int direction = 1;

            while (
                playlist.isStarted() &&
                num_exposed < num &&
                count_exposed < NUM_TO_EXPOSE - 1)
            {
                int try_index = (idx - 1) + offset * direction;
                if (try_index > 0 && try_index <= num)
                {
                    Track track = playlist.getTrack(try_index);
                    if (track != null && exposeTrack(track,true))
                        count_exposed++;
                }
                if (offset == 0)
                    offset = 1;
                else if (direction == 1)
                    direction = -1;
                else
                {
                    direction = 1;
                    offset++;
                }
            }

            Utils.log(dbg_expose,1,"expose_more() exposed " + count_exposed + " new tracks");

           if (count_exposed > 0)
           {
               Utils.log(dbg_expose,1,"expose_more() sending EVENT_PLAYLIST_TRACKS_EXPOSED");
               artisan.handleArtisanEvent(EventHandler.EVENT_PLAYLIST_TRACKS_EXPOSED,null);
           }

            return count_exposed > 0;
        }
        return false;
    }


    public void ThreadedExposeMore(LocalPlaylist playlist)
    {
        Utils.log(dbg_expose,0,"ThreadedExposeMore() num_exposed=" + num_exposed + " num_tracks=" + playlist.getNumTracks());
        Thread thread = new Thread(new expose_more_thread(playlist));
        thread.start();
    }


    public class expose_more_thread implements Runnable
    {
        LocalPlaylist local_playlist;
        public expose_more_thread(LocalPlaylist playlist)
        {
            local_playlist = playlist;
        }

        public void run()
        {
            Utils.log(dbg_expose,0,"expose_more_thread sleeping ...");
            int count = 0;
            while (local_playlist.isStarted() && count++ < EXPOSE_MORE_SLEEP_TIMES)
                Utils.sleep(EXPOSE_MORE_SLEEP_MILLIS);
            if (local_playlist.isStarted())
            {
                Utils.log(0,0,"expose_more_thread back from sleep, calling exposeMore()");
                exposeMore(local_playlist);
            }
            else
            {
                Utils.warning(0,0,"expose_more_thread EXITING with !local_playlist.isStarted() after " + count + " tries");
            }
        }
    }

}   // class PlaylistExposer
