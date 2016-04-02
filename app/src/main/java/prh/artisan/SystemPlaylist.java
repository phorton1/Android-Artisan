package prh.artisan;

import prh.artisan.interfaces.EventHandler;
import prh.artisan.interfaces.FetchablePlaylist;
import prh.artisan.interfaces.Playlist;
import prh.artisan.utils.Fetcher;
import prh.artisan.utils.PlaylistFetcher;
import prh.device.LocalPlaylist;
import prh.server.HTTPServer;
import prh.server.http.OpenPlaylist;
import prh.server.utils.playlistExposer;
import prh.types.intTrackHash;
import prh.types.trackList;
import prh.utils.Base64;
import prh.utils.Utils;
import prh.utils.httpUtils;


// The SystemPlaylist is one of two FetchablePlaylists in the system
// that can be presented to the aPlaying UI.  The other is the
// device.service.OpenPlaylist, which can also be plugged directly
// into aPlaying.



public class SystemPlaylist implements FetchablePlaylist
{
    private static int dbg_cp = 1;     // basics
    private static int dbg_fetch = 0;  // fetcher
    private static int dbg_ida = 0;    // id arrays

    // state

    private Artisan artisan;
    private Playlist associated_playlist;

    private boolean is_dirty;
    private int next_open_id;
    private int playlist_count_id;
    private int content_change_id;
    private int recs_changed_count_id;


    // contents

    private  String name;
    private  int playlist_num;
    private  int num_tracks;
    private  int track_index;      // one based
    private  int my_shuffle;
    private  String pl_query;

    private  trackList tracks_by_position;
    private  intTrackHash tracks_by_open_id;

    // Basic Playlist Interface, except getTrack()

    @Override public String getName()         { return name; }
    @Override public int getPlaylistNum()     { return playlist_num; }
    @Override public int getNumTracks()       { return num_tracks; }
    @Override public int getCurrentIndex()    { return track_index; }
    @Override public Track getCurrentTrack()  { return getTrack(track_index); }
    @Override public int getMyShuffle()       { return my_shuffle; }
    @Override public String getQuery()        { return pl_query; }

    @Override public void saveIndex(int index) {}
    @Override public boolean isDirty() { return is_dirty; }
    @Override public void setDirty(boolean b) { is_dirty = b; }

    // FetchablePlaylist Interface

    @Override public int getPlaylistCountId() { return playlist_count_id; }
    @Override public int getContentChangeId() { return content_change_id; }


    // Utilities

    public OpenPlaylist getHttpOpenPlaylist()
        // OpenHome Server Support
        // TODO this might just return null if
        // artisan.getRenderer() != artisan.getLocalRenderer(),
        // as we should only be serving stuff when the LocalRenderer
        // is active. See other notes in this file.
    {
        HTTPServer http_server = artisan.getHTTPServer();
        OpenPlaylist open_playlist = http_server == null ? null :
            (OpenPlaylist) http_server.getHandler("Playlist");
        return open_playlist;
    }


    //----------------------------------------------------------------
    // Constructor
    //----------------------------------------------------------------

    public SystemPlaylist(Artisan ma)
        // default constructor creates an un-named playlist ""
        // with no associated parent playListSource
    {
        artisan = ma;
        playlist_count_id = 0;      // identity changed
        content_change_id = 0;      // any contents changed
        recs_changed_count_id = 0;  // records before eol changed
        next_open_id = 1;
        clean_init();
    }


    private void clean_init()
    {
        is_dirty = false;

        name = "";
        my_shuffle = 0;
        num_tracks = 0;
        track_index = 0;
        playlist_num = 0;
        pl_query = "";
        tracks_by_position =  new trackList();
        tracks_by_open_id = new intTrackHash();

        playlist_count_id++;
        content_change_id++;
        recs_changed_count_id++;

    }


    // These start and stop the whole thing

    @Override public void startPlaylist()
    {
    }

    @Override public void stopPlaylist(boolean wait_for_stop)
    {
        associated_playlist = null;
    }



    //----------------------------------------------------------------
    // associated_playlist
    //----------------------------------------------------------------

    public void setAssociatedPlaylist(Playlist other)
    {
        // clean_init() is the equivalent of start()
        // for the SystemPlaylist

        this.clean_init();

        // notify the http playlist if one is active
        // Open playlist shall work directly thru LocalPlaylist
        // and all the ID stuff here moves there ..

        // prh.server.http.OpenPlaylist open_playlist =
        //     getHttpOpenPlaylist();
        // if (open_playlist != null)
        //     open_playlist.clearAllExposers();

        // stop the old playlist

        if (associated_playlist != null)
            associated_playlist.stopPlaylist(false);
        associated_playlist = other;

        // start the new one

        if (associated_playlist != null)
        {
            associated_playlist.startPlaylist();
            name = associated_playlist.getName();
            num_tracks = associated_playlist.getNumTracks();
            track_index = associated_playlist.getCurrentIndex();
            my_shuffle = associated_playlist.getMyShuffle();

            trackList other_tracks = ((LocalPlaylist)other).getTracksByPositionRef();
            for (int i=0; i<num_tracks; i++)
            {
                Track track = other_tracks.get(i);
                track.setPosition(i + 1);   // in case it was mucked up
                track.setOpenId(next_open_id);
                tracks_by_position.add(track);
                tracks_by_open_id.put(next_open_id++,track);
            }


            // build the sparse array
            //
            // for (int i = 0; i < num_tracks; i++)
            //     tracks_by_position.add(i,null);

            // Exposer Support - expose the first track
            //
            // This assumes that getTrack() will work on the
            // associated_playlist, which is true for LocalPlaylists.
            //
            // THIS object should be asleep whenever device.service.OpenPlaylist
            // (which is part of the OpenHomeRenderer) is active, and in general,
            // all the http Renderer Servers (AVTransport, RenderingControl)
            // should only be used when the LocalRenderer is the current renderer.
            // In fact, attempts to use these servers should FORCE the current
            // renderer to the LocalRenderer.
            //
            // Also this should only be for BubbleUp.
            // And it is disabled at this time for testing.
            // but not if open_playlist_device

            // if (num_tracks > 0 && open_playlist != null)
            // {
            //     Track track = getCurrentTrack();
            //     if (track != null)
            //         open_playlist.exposeTrack(track,true);
            // }
        }
    }


    @Override
    public Track getTrack(int index)
        // Just happens to be nearly the same as
        // the implementation in LocalPlaylist, and
        // assumes that associated_playlist.getTrack()
        // will work for all valid indices.
    {
        // try to get it in memory

        if (index <= 0 || index > num_tracks)
            return null;

        // get from in-memory cache

        if (index - 1 > tracks_by_position.size())
        {
            Utils.error("Attempt to getTrack(" + index + ") when there are only " + tracks_by_position.size() + " slots available");
            return null;
        }
        Track track = tracks_by_position.get(index - 1);

        // however, if the track is not found,
        // defer to the associated_playlist

        if (track == null)
        {
            if (associated_playlist == null)
                Utils.error("No associated playlist for null track in SystemPlaylist.getTrck(" + index + ")");
            else
            {
                track = associated_playlist.getTrack(index);
                track.setPosition(index);   // in case it was mucked up
                track.setOpenId(next_open_id);
                tracks_by_position.set(index - 1,track);
                tracks_by_open_id.put(next_open_id++,track);
            }
        }

        return track;
    }



    //-----------------------------------------------------
    // Playlist Manipulations (Current Playlist Only)
    //-----------------------------------------------------


    public Track insertTrack(int position, Track track)
        // ONE-BASED POSITION SIGNATURE
        // This IS the lowest level insertTrack method
        // PlaylistBase assigns next_open_id for insertTrack()
    {
        is_dirty = true;
        content_change_id++;
        if (position < tracks_by_position.size())
            recs_changed_count_id++;

        int new_id = next_open_id++;
        track.setOpenId(new_id);
        track.setPosition(position);
        int old_track_index = track_index;

        Utils.log(dbg_cp,0,"insertTrack(" + track.getTitle() + " to SystemPlaylist(" + name + ") at position=" + position);
        tracks_by_open_id.put(new_id,track);
        tracks_by_position.add(position-1,track);
        num_tracks++;

        if (track_index > position)
            track_index++;
        else if (track_index == 0)
            track_index = 1;

        if (old_track_index != track_index)
            saveIndex(track_index);

        // Tell server.http.OpenPlaylist to expose this tracl

        prh.server.http.OpenPlaylist open_playlist = getHttpOpenPlaylist();
        if (open_playlist != null)
            open_playlist.exposeTrack(track,true);

        artisan.handleArtisanEvent(EventHandler.EVENT_PLAYLIST_CONTENT_CHANGED,this);
        return track;
    }


    public boolean removeTrack(Track track)
        // Assumes that track is in the playlist
        // Takes track and not integer position
    {
        is_dirty = true;
        content_change_id++;
        recs_changed_count_id++;

        int position = tracks_by_position.indexOf(track) + 1;
        Utils.log(dbg_cp,0,"removeTrack(" + track.getTitle() + ") from SystemPlaylist(" + name + ") at position=" + position);

        num_tracks--;
        tracks_by_open_id.remove(track);
        tracks_by_position.remove(track);

        int old_track_index = track_index;
        if (position < track_index)
            track_index--;
        else if (track_index > num_tracks)
            track_index = num_tracks;

        if (old_track_index != track_index)
            saveIndex(track_index);

        artisan.handleArtisanEvent(EventHandler.EVENT_PLAYLIST_CONTENT_CHANGED,this);
        return true;
    }


    public Track incGetTrack(int inc)
        // loop thru tracks till we find a playable
        // return null on errors or none found
    {
        int start_index = track_index;
        incIndex(inc);

        Track track = getTrack(track_index);
        if (track == null)
            return null;

        while (!Utils.supportedType(track.getType()))
        {
            if (inc != 0 && track_index == start_index)
            {
                Utils.error("No playable tracks found");
                track_index = 0;
                saveIndex(track_index);
                return null;
            }
            if (inc == 0) inc = 1;
            incIndex(inc);
            track = getTrack(track_index);
            if (track == null)
                return null;
        }

        saveIndex(track_index);
        return track;
    }


    private void incIndex(int inc)
    {
        track_index = track_index + inc;    // one based
        if (track_index > num_tracks)
            track_index = 1;
        if (track_index <= 0)
            track_index = num_tracks;
        if (track_index > num_tracks)
            track_index = num_tracks;
    }


    // support for schemes

    public Track getTrackLow(int position)
        // One based access to the sparse array
        // ONLY called by playlistExposer
    {
        Track track = null;
        if (position > 0 &&
            position <= tracks_by_position.size())
            track = tracks_by_position.get(position - 1);
        return track;
    }


    public void setName(String new_name)
        // Called by the PlaylistSource in saveAs()
    {
        if (!name.equals(new_name))
        {
            name = new_name;
            artisan.handleArtisanEvent(EventHandler.EVENT_PLAYLIST_CHANGED,this);
        }
    }


    //------------------------------------------------------
    // OpenHome Support
    //------------------------------------------------------

    public Track getByOpenId(int open_id)
    {
        return tracks_by_open_id.get(open_id);
    }


    public Track insertTrack(int after_id, String uri, String metadata)
    {
        Track track = new Track(uri,metadata);
        return insertTrack(track,after_id);
    }


    public Track seekByIndex(int index)
    {
        Track track = getTrack(index);
        if (track != null)
        {
            track_index = index;
            saveIndex(track_index);
        }
        return track;
    }


    public Track seekByOpenId(int open_id)
    {
        Track track = getByOpenId(open_id);
        if (track != null)
        {
            track_index = tracks_by_position.indexOf(track) + 1;
            saveIndex(track_index);
        }
        return track;
    }


    public Track insertTrack(Track track, int after_id)
        // OPEN HOME SIGNATURE
        // insert the item after the given open id
        // where 0 means front of the list.
        // returns the item on success
    {
        int insert_idx = 0; // zero base
        if (after_id > 0)
        {
            Track after_track = tracks_by_open_id.get(after_id);
            if (track == null)
            {
                Utils.error("Could not find item(after_id=" + after_id + ") for insertion");
                return null;    // should result in 800 error
            }
            insert_idx = tracks_by_position.indexOf(after_track) + 1;
            // insert_pos is zero based
        }

        int position = insert_idx + 1;
        return insertTrack(position,track);
    }



    public boolean removeTrack(int open_id, boolean dummy_for_open_id)
        // OPEN_ID SIGNATURE
    {
        Track track = tracks_by_open_id.get(open_id);
        if (track == null)
        {
            Utils.error("Could not remove(" + open_id + ") from playlist(" + name + ")");
            return false;
        }
        return removeTrack(track);
    }


    //------------------------------------------------------------------
    // IdArrays
    //------------------------------------------------------------------


    public int[] string_to_id_array(String id_string)
        // gets a string of space delimited ascii integers!
    {
        Utils.log(0,0,"string_to_id_array(" + id_string + ")");
        String parts[] = id_string.split("\\s");
        int ids[] = new int[parts.length];
        for (int i=0; i<parts.length; i++)
            ids[i] = Utils.parseInt(parts[i]);
        return ids;
    }


    public String getIdArrayString(playlistExposer exposer)
        // return a base64 encoded array of integers
        // May be called with a playlistExposer or not
    {
        int num = 0;
        int num_tracks = getNumTracks();
        byte data[] = new byte[num_tracks * 4];
        Utils.log(dbg_ida,0,"getIdArrayString() num_tracks="+num_tracks);

        // show debugging for the exposer, if any

        if (exposer != null)
            Utils.log(dbg_ida,1,"exposer(" + exposer.getUserAgent() + ") num_exposed=" + exposer.getNumExposed());

        for (int index=1; index<=num_tracks; index++)
        {
            // If there is an exposer, then a null record
            // indicates it has not yet been exposed.
            // For requests from non-exposer control points,
            // we get the Track
            //
            // Since this could take a long time if a non-exposer
            // tried to get all the tracks on a single go, it might
            // be better to just read all the records into memory from
            // a single cursor in Start() if there are any non-exposers

            Track track = (exposer == null) ?
                getTrack(index) :
                tracks_by_position.get(index - 1);

            if (track != null &&
                (exposer == null ||
                    exposer.isExposed(track)))
            {
                int id = track.getOpenId();
                data[num * 4 + 0] = (byte) ((id >> 24) & 0xFF);
                data[num * 4 + 1] = (byte) ((id >> 16) & 0xFF);
                data[num * 4 + 2] = (byte) ((id >> 8) & 0xFF);
                data[num * 4 + 3] = (byte) (id & 0xFF);
                num++;
            }
        }

        byte data2[] = new byte[num * 4];
        for (int i=0; i<num*4; i++)
            data2[i] = data[i];

        String retval = Base64.encode(data2);
        Utils.log(dbg_ida +1,1,"id_array Length=" + num);
        Utils.log(dbg_ida +1,1,"id_array='" + retval + "'");
        return retval;
    }


    public String id_array_to_tracklist(int ids[])
    {
        String rslt = "";
        for (int i=0; i<ids.length; i++)
        {
            int id = ids[i];
            Track track = getByOpenId(id);
            if (track == null)
            {
                Utils.error("id_array_to_tracklist: index("+i+")  id("+id+") not found");
                return  httpUtils.encode_xml("<TrackList></TrackList>");
            }

            rslt += "<Entry>" + // \n" +
                "<Id>" + id + "</Id>" + // "\n" +
                "<Uri>" + track.getPublicUri() + "</Uri>" + // "\n" +
                "<Metadata>" + track.getDidl() + "</Metadata>" + // "\n" +
                "</Entry>"; // \n";
        }

        // took a while to figure this ...
        //
        // This is an INNER <TrackList> that is Didl encoded.
        // It is the VALUE of a regular XML <Tracklist> element
        // in the ReadList result.

        rslt = "<TrackList>" + rslt + "</TrackList>";
        rslt = httpUtils.encode_xml(rslt);
        return rslt;
    }



    //------------------------------
    // FetcherSource Interface
   //------------------------------


    @Override public int getRecChangedCountId()
    {
        return recs_changed_count_id;
    }

    @Override public trackList getFetchedTrackList()
    {
        return tracks_by_position;
    }


    public Fetcher.fetchResult getFetcherPlaylistRecords(int start, int num)
    {
        Fetcher.fetchResult result = Fetcher.fetchResult.FETCH_DONE;
        if (true) return Fetcher.fetchResult.FETCH_DONE;


        // short ending if all records gotten
        // else determine tracks to add

        if (start >= num_tracks)
            return result;

        // we're gonna need an associated playlist

        if (associated_playlist == null)
        {
            Utils.log(dbg_fetch,1,"no associated playlist in getFetcherPlaylistRecords()");
            return Fetcher.fetchResult.FETCH_ERROR;
        }

        // determine number of tracks to ADD
        // we already have start..tracks_by_position.size()-1

        if (start < tracks_by_position.size())
        {
            start = tracks_by_position.size();
            num -= tracks_by_position.size();
        }

        // and we can't be asked to get more than the playlist has
        // and we return FETCH_RECORDS if we don't get them all

        if (start + num > num_tracks)
            num = num_tracks - start;
        else
            result = Fetcher.fetchResult.FETCH_RECS;

        // add the tracks to tracks_by_position

        for (int i=start; i<start + num - 1; i++)
        {
            Track track = associated_playlist.getTrack(i+1);
            track.setOpenId(next_open_id);
            tracks_by_open_id.put(next_open_id,track);
            tracks_by_position.add(track);
            next_open_id++;
        }

        // let aPlaylist know we added some

        content_change_id++;
        return result;
    }



}   // class SystemPlaylist

