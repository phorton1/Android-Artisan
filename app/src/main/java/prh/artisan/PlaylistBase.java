package prh.artisan;

import prh.types.intTrackHash;
import prh.types.trackList;
import prh.utils.Base64;
import prh.utils.Utils;
import prh.utils.httpUtils;


public abstract class PlaylistBase implements Playlist,Comparable<PlaylistBase>
    // Base class of all Playlists
    //
    // Implements an in-memory list of items, with
    // insert, delete, etc, that sends Artisan Events
    // in case of changes.
{
    private int dbg_pl = 1;

    // state

    private Artisan artisan;

    private boolean dirty;
    public boolean isDirty()  { return dirty; }
    public void setDirty(boolean b)  { dirty = b; }

    // contents

    protected String name;
    protected int playlist_num;
    protected int num_tracks;
    protected int track_index;      // one based
    protected int my_shuffle;
    protected String pl_query;

    protected static int next_open_id = 1;
    protected trackList tracks_by_position;
    protected intTrackHash tracks_by_open_id;

    private int content_change_id = 0;

    // accessors

    @Override public String getPlaylistName() { return name; }
    @Override public int getPlaylistNum()     { return playlist_num; }
    @Override public int getNumTracks()       { return num_tracks; }
    @Override public int getCurrentIndex()    { return track_index; }
    @Override public Track getCurrentTrack()  { return getPlaylistTrack(track_index); }
    @Override public int getMyShuffle()       { return my_shuffle; }
    @Override public String getQuery()        { return pl_query; }
    @Override public int getContentChangeId() { return content_change_id; }

    // methods that do nothing in base class

    @Override public boolean isStarted() { return true; }
    @Override public void saveIndex(int index) {}


    @Override public void startPlaylist()
    {
    }

    @Override public void stopPlaylist(boolean wait_for_stop)
    {
    }

    @Override public int compareTo(PlaylistBase other)
    {
        int cmp = playlist_num - other.getPlaylistNum();
        if (cmp != 0)
            return cmp;
        return name.compareTo(other.getPlaylistName());
    }



    protected void clean_init_playlist_base()
    {
        name = "";
        dirty = false;
        my_shuffle = 0;
        num_tracks = 0;
        track_index = 0;
        playlist_num = 0;
        pl_query = "";
        tracks_by_position =  new trackList();
        tracks_by_open_id = new intTrackHash();
    }

    //------------------------------------------------
    // Basics
    //------------------------------------------------

    public PlaylistBase(Artisan ma)
    {
        artisan = ma;
        clean_init_playlist_base();
    }


    // PlaylistBase does not implement getPlaylistTrack()
    //    LocalPlaylist and CurrentPlaylist implement it.
    //    where LocalPlaylist gives out the open_ids as it
    //    reads records into memory.
    // However PlaylistBase does insertTrack(), which needs
    //    open_id, so the open_id is kept on this object.
    //
    // @Override public Track getPlaylistTrack(int index)
    //    get a track from the playlist
    //{
    //    if (index <= 0 || index > num_tracks)
    //        return null;
    //
    //    // get from sparse self
    //
    //    if (index - 1 > tracks_by_position.size())
    //    {
    //        Utils.error("wtf");
    //        return null;
    //    }
    //    Track track = tracks_by_position.get(index - 1);
    //    if (track != null)
    //    {
    //        track.setOpenId(next_open_id);
    //        tracks_by_position.set(index - 1,track);
    //        tracks_by_open_id.put(next_open_id,track);
    //        next_open_id++;
    //    }
    //
    //    return track;
    //}


    @Override public Track incGetTrack(int inc)
    // loop thru tracks till we find a playable
    // return null on errors or none found
    {
        int start_index = track_index;
        incIndex(inc);

        Track track = getPlaylistTrack(track_index);
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
            track = getPlaylistTrack(track_index);
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



    @Override public boolean removeTrack(Track track)
    // Assumes that track is in the playlist
    // Takes track and not integer position
    {
        int position = tracks_by_position.indexOf(track) + 1;
        Utils.log(dbg_pl,0,"removeTrack(" + track.getTitle() + ") from CurrentPlaylist(" + name + ") at position=" + position);

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

        dirty = true;
        content_change_id++;
        artisan.handleArtisanEvent(EventHandler.EVENT_PLAYLIST_CONTENT_CHANGED,this);
        return true;
    }



    @Override public Track insertTrack(int position, Track track)
        // ONE-BASED POSITION SIGNATURE
        // This IS the lowest level insertTrack method
        // PlaylistBase assigns next_open_id for insertTrack()
    {
        int new_id = next_open_id++;
        track.setOpenId(new_id);
        track.setPosition(position);
        int old_track_index = track_index;

        Utils.log(dbg_pl,0,"insertTrack(" + track.getTitle() + " to CurrentPlaylist(" + name + ") at position=" + position);
        tracks_by_open_id.put(new_id,track);
        tracks_by_position.add(position-1,track);
        num_tracks++;

        if (track_index > position)
            track_index++;
        else if (track_index == 0)
            track_index = 1;

        if (old_track_index != track_index)
            saveIndex(track_index);

        dirty = true;
        content_change_id++;
        artisan.handleArtisanEvent(EventHandler.EVENT_PLAYLIST_CONTENT_CHANGED,this);
        return track;
    }



    //------------------------------------------------------
    // OpenHome Support
    //------------------------------------------------------

    @Override public Track getByOpenId(int open_id)
    {
        return tracks_by_open_id.get(open_id);
    }


    @Override public Track insertTrack(int after_id, String uri, String metadata)
    {
        Track track = new Track(uri,metadata);
        return insertTrack(track,after_id);
    }


    @Override public Track seekByIndex(int index)
    {
        Track track = getPlaylistTrack(index);
        if (track != null)
        {
            track_index = index;
            saveIndex(track_index);
        }
        return track;
    }


    @Override public Track seekByOpenId(int open_id)
    {
        Track track = getByOpenId(open_id);
        if (track != null)
        {
            track_index = tracks_by_position.indexOf(track) + 1;
            saveIndex(track_index);
        }
        return track;
    }


    @Override public Track insertTrack(Track track, int after_id)
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



    @Override public boolean removeTrack(int open_id, boolean dummy_for_open_id)
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

    private static int dbg_ida = 0;


    @Override public int[] string_to_id_array(String id_string)
    // gets a string of space delimited ascii integers!
    {
        Utils.log(0,0,"string_to_id_array(" + id_string + ")");
        String parts[] = id_string.split("\\s");
        int ids[] = new int[parts.length];
        for (int i=0; i<parts.length; i++)
            ids[i] = Utils.parseInt(parts[i]);
        return ids;
    }


    @Override public String getIdArrayString(CurrentPlaylistExposer exposer)
    // return a base64 encoded array of integers
    // May be called with a CurrentPlaylistExposer or not
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
                getPlaylistTrack(index) :
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

        // java.util.Arrays.copyOf(data,num_exp);

        byte data2[] = new byte[num * 4];
        for (int i=0; i<num*4; i++)
            data2[i] = data[i];

        String retval = Base64.encode(data2);
        Utils.log(dbg_ida +1,1,"id_array Length=" + num);
        Utils.log(dbg_ida +1,1,"id_array='" + retval + "'");
        return retval;
    }




    // ReadList experimental constants
    // Took a long time to figure out that BubbleUP delivers (expects)
    // the whole thing to be xml_encoded, with no outer <didl> tag,
    // but an extra inner TrackList tag. And apparently BuP only works
    // if it gets ALL of the ids it asked for ... you cannot trim
    // the list here ...

    private static final int READLIST_LIMIT = 0;
    // only return this number of tracks
    // set to zero to send all
    private static final boolean READLIST_ENCODE = true;
    // if true, the entire tracklist returned by ReadList action
    // will be xml_encoded. This is what BubbleUp does.
    private static final boolean READLIST_DIDL = false;
    // if true, the whole tracklist will be wrapped in didl tags.
    // BubbleUp does not do this, though they do encode the whole thing.
    private static final boolean READLIST_INNER_TRACKLIST = true;
    // if true an extra level of of <Tracklist> tags
    // will be added to the Readlist tracklist reply.
    // For some reason BuP does this, and it's xml_encoded.
    private static final boolean READLIST_INNER_DIDL = true;
    // if true, even if the whole message is diddled,
    // an inner didl tag will be added to each metadata
    // I guess this is true by default.


    @Override public String id_array_to_tracklist(int ids[])
    // The response TrackList XML item has an
    // inner DIDL TrackList ... these details
    // determined empirically from BuP
    {
        boolean one_error = false;

        String rslt = "\n";
        if (READLIST_DIDL)
            rslt +=  httpUtils.start_didl() + "\n";
        if (READLIST_INNER_TRACKLIST)
            rslt += "<TrackList>\n";

        int use_len = READLIST_LIMIT > 0 ? READLIST_LIMIT : ids.length;

        for (int i=0; i<use_len; i++)
        {
            int id = ids[i];
            Track track = getByOpenId(id);
            if (track == null)
            {
                if (!one_error)
                    Utils.error("id_array_to_tracklist: index("+i+")  id("+id+") not found");
                one_error = true;
            }
            else
            {
                String metadata = !READLIST_DIDL || READLIST_INNER_DIDL ?
                    track.getDidl() :
                    track.getMetadata();

                rslt += "<Entry>\n" +
                    "<Id>" + id + "</Id>\n" +
                    "<Uri>" + track.getPublicUri() + "</Uri>\n" +
                    "<Metadata>" + metadata + "</Metadata>\n" +
                    "</Entry>\n";
            }
        }
        if (READLIST_INNER_TRACKLIST)
            rslt += "</TrackList>\n";
        if (READLIST_DIDL)
            rslt = httpUtils.end_didl() + "\n";
        if (READLIST_ENCODE)
            rslt = httpUtils.encode_xml(rslt);
        return rslt;
    }



}   // class Playlist

