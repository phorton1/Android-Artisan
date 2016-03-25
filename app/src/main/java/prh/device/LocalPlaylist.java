package prh.device;

// Constructing a LocalPlaylist is very quick, requiring
// only 1 hit to the playlist.db file.  The initial array
// of tracks is filled with nulls, indicating that the Track
// database records have not yet been read into memory.
//
// Any call to getTrack() will cause the track to get
// read in from the database if it is null in the list.
// Thereafter, it is in memory.
//
// The track.db file and the in-memory list are kept
// in synch with regards to insertions and deletions, etc,
// as the positions in the database are UPDATED to match
// the new positions in-memory.


import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.LinkedList;

import prh.artisan.Artisan;
import prh.artisan.Database;
import prh.artisan.EventHandler;
import prh.artisan.Playlist;
import prh.artisan.Prefs;
import prh.artisan.Track;
import prh.server.HTTPServer;
import prh.server.http.OpenPlaylist;
//import prh.server.utils.PlaylistExposer;
import prh.utils.Fetcher;
import prh.server.utils.PlaylistExposer;
import prh.types.recordList;
import prh.utils.Base64;
import prh.utils.Utils;
import prh.utils.httpUtils;


public class LocalPlaylist implements Playlist, Fetcher.FetcherUser
{
    private int dbg_pl = 1;
    private int dbg_open_pl = 0;

    public static int MAX_TRACKS = 1000;

    // playlist member variables
    // it is not necessary to call start on a new default
    // null,"" playlist, as all members are set here ...

    private Artisan artisan;
    private String name ="";
    private int num = 0;
    private int num_tracks = 0;
    private int track_index = 0;
    private int my_shuffle = 0;
    private int dirty = 0;
        // bitwise 1=playlist, 2=tracks
    private static int next_open_id = 1;

    // database and track lists

    private SQLiteDatabase track_db = null;
    private SQLiteDatabase playlist_db = null;
        // these are null for un-named default playlist
    private HashMap<Integer,Track> tracks_by_open_id = new HashMap<Integer,Track>();
    private LinkedList<Track> tracks_by_position =  new LinkedList<Track>();
        // list of in-memory tracks
        // the linked list is flushed out with null members during start()

    private boolean is_started = false;
    public boolean isStarted() { return is_started; }
    public boolean isLocal() { return true; }

    //----------------------------------------------------------------
    // Constructor
    //----------------------------------------------------------------

    public LocalPlaylist(Artisan ma)
        // default constructor creates an un-named playlist ""
        // with no associated playlist_db or tracklist_db handles
        // but still has to be done in context of an Artisan
    {
        artisan = ma;
    }


    public LocalPlaylist(Artisan ma, SQLiteDatabase list_db,String name)
    {
        artisan = ma;
        this.name = name;
        playlist_db = list_db;
        Utils.log(dbg_pl,0,"LocalPlaylist(" + name + ") ...");

        // allow for construction of default "" playlist
        // without any db

        if (playlist_db == null || name.equals(""))
        {
            Utils.warning(dbg_pl,0,"Should use default contructor");
            playlist_db = null;
            name = "";
        }

        // get the record (cursor) for this guy

        if (playlist_db != null)
        {
            Cursor cursor = null;
            String query = "SELECT * FROM playlists WHERE name='" + name + "'";
            try
            {
                cursor = playlist_db.rawQuery(query,new String[]{});
            }
            catch (Exception e)
            {
                Utils.error("Could not execute query: " + query + " exception=" + e.toString());
            }

            // setup member variables

            if (cursor != null && cursor.moveToFirst())
            {
                HashMap<String,Integer> fields = Database.get_fields("playlists",cursor);
                num_tracks = cursor.getInt(fields.get("num_tracks"));
                track_index = cursor.getInt(fields.get("track_index"));
                my_shuffle = cursor.getInt(fields.get("shuffle"));
                Utils.log(dbg_pl,1,"LocalPlaylist(" + name + ") num_tracks=" + num_tracks + " track_index=" + track_index + " my_shuffle=" + my_shuffle);

            }
        }

        // finished

        Utils.log(dbg_pl,1,"LocalPlaylist(" + name + ") finished");

    }   // ctor



    //-------------------------------------------------------------
    // accessors (Playlist interface)
    //-------------------------------------------------------------

    @Override public String getName()         { return name; }
    @Override public int getNum()             { return num; }
    @Override public int getNumTracks()       { return num_tracks; }
    @Override public int getCurrentIndex()    { return track_index; }
    @Override public int getMyShuffle()       { return my_shuffle; }

    //------------------------------
    // Fetcher scheme
    //------------------------------

    private static int FETCH_INITIAL = 20;
    private static int NUM_PER_FETCH = 200;
    private Fetcher fetcher = null;


    @Override public boolean getFetchItems(int start, int count, Fetcher fetcher)
    {
        int num_fetched = 0;
        recordList records = fetcher.getRecords();

        int num_already = records.size();
        for (int i = num_already; i < num_tracks && num_fetched < NUM_PER_FETCH; i++)
        {
            records.add(getTrack(i));
            num_fetched++;
        }

        if (num_fetched > 0)
            artisan.handleArtisanEvent(EventHandler.EVENT_PLAYLIST_CONTENT_CHANGED,this);
        return true;
    }


    @Override public recordList getAvailableTracks(boolean with_album_breaks)
        // Called by the Playlist to start, and on events
        // HAS to work off of in memory records which can be
        // slowish if they have not been loaded from the database yet.
        // So, even though it's local, it uses a Fetcher ...
        //
        // Have to wait for it to stop to bump/dec num_tracks
        // on insertions - BUG WAITING TO HAPPEN

    {
        if (fetcher == null)
        {
            recordList records = new recordList();
            for (int i = 0; i < num_tracks && i < FETCH_INITIAL; i++)
                records.add(getTrack(i + 1));  // ONE BASED

            fetcher = new Fetcher(
                artisan,
                this,
                records,
                num_tracks,
                NUM_PER_FETCH,
                "Playlist_" + getName());

            if (num_tracks > FETCH_INITIAL)
            {
                fetcher.start();
            }
        }

        return fetcher.getRecords();
    }



    @Override public void start()
    {
        tracks_by_open_id = new HashMap<Integer,Track>();
        tracks_by_position = new LinkedList<Track>();

        if (!name.equals(""))
        {
            String db_name = Prefs.getString(Prefs.id.DATA_DIR) + "/playlists/" + name + ".db";
            try
            {
                track_db = SQLiteDatabase.openDatabase(db_name,null,0); // SQLiteDatabase.OPEN_READONLY);
            }
            catch (Exception e)
            {
                Utils.error("Could not open database " + db_name);
                return;
            }

            for (int i=0; i<num_tracks; i++)
            {
                // Utils.log(0,6,"setting initial null track(" + i +") length=" + tracks_by_position.size());
                tracks_by_position.add(i,null);
            }
        }

        // EXPOSE THE CURRENT TRACK

        if (track_index > 0)
        {
            HTTPServer http_server = artisan.getHTTPServer();
            OpenPlaylist open_playlist = http_server == null ? null :
                (OpenPlaylist) http_server.getHandler("Playlist");
            if (open_playlist != null)
                open_playlist.exposeCurrentTrack(this);
        }

        is_started = true;


    }   // LocalPlaylist.start()


    @Override public void stop()
    {
        is_started = false;

        // unexpose any tracks

        HTTPServer http_server = artisan.getHTTPServer();
        OpenPlaylist open_playlist = http_server == null ? null :
            (OpenPlaylist) http_server.getHandler("Playlist");
        if (open_playlist != null)
            open_playlist.clearAllExposers(this);

        if (track_db != null)
        {
            if (dirty != 0)
                save(true);

            track_db.close();
            track_db = null;
        }
        tracks_by_open_id.clear();
        tracks_by_open_id = null;
        tracks_by_position.clear();
        tracks_by_position = null;
    }


    @Override public Track getCurrentTrack()
        // could return null
    {
        return getTrack(track_index);
    }


    @Override public Track getTrack(int index)
        // get a track from the playlist
        // return null on error
    {
        if (index <= 0 || index > num_tracks)
            return null;

        // get from in-memory cache

        if (index - 1 > tracks_by_position.size())
        {
            Utils.error("wtf");
            return null;
        }
        Track track = tracks_by_position.get(index - 1);

        // if not in-memory, then try to get it from the database

        if (track == null && track_db != null)
        {
            Cursor cursor = null;
            String query = "SELECT * FROM tracks WHERE position=" + index;
            try
            {
                cursor = track_db.rawQuery(query,new String[]{});
            }
            catch (Exception e)
            {
                Utils.error("Could not execute query: " + query + " exception=" + e.toString());
                cursor = null;
            }
            if (cursor == null || !cursor.moveToFirst())
            {
                Utils.error("No Track found for position(" + index + ")");
            }
            else
            {
                track = new Track(cursor);
                // Utils.log(0,5,"got db_track(" + next_open_id + ") " + track.getTitle());

                track.putOpenId(next_open_id);
                tracks_by_position.set(index - 1,track);
                tracks_by_open_id.put(next_open_id,track);
                next_open_id++;
            }
        }

        return track;
    }


    @Override public Track incGetTrack(int inc)
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
                save_index();
                return null;
            }
            if (inc == 0) inc = 1;
            incIndex(inc);
            track = getTrack(track_index);
            if (track == null)
                return null;
        }

        save_index();
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


    private void save_index()
    {
        Utils.log(dbg_pl,1,"save_index() track_index=" + track_index);
        if (playlist_db != null)    // == null for name=""
        {
            String query = "UPDATE playlists SET track_index=" +
                track_index + " WHERE name='" + name + "'";
            try
            {
                playlist_db.execSQL(query);
            }
            catch (Exception e)
            {
                Utils.error("Could not update track_index: " + e.toString());
            }
        }
    }


    //------------------------------------------------------
    // OpenHome Support
    //------------------------------------------------------
    //  Track getTrackLow(int index)
    //  Track getByOpenId(int open_id);
    //  Track seekByIndex(int index);
    //  Track seekByOpenId(int open_id);
    //  Track insertTrack(int after_id, String uri, String metadata);
    //  boolean removeTrack(int open_id);
    //
    //  String getIdArrayString(PlaylistExposer exposer);
    //  int[] string_to_id_array(String id_string);
    //  String id_array_to_tracklist(int ids[]);


    public Track getTrackLow(int index)
    // specifically for use by PlaylistExposer
    // may return null for non-exposed
    // as-yet otherwise unused records
    {
        return tracks_by_position.get(index-1);
    }


    public Track getByOpenId(int open_id)
    {
        return tracks_by_open_id.get(open_id);
    }


    public Track insertTrack(int after_id, String uri, String metadata)
    {
        Track track = new Track(uri,metadata);
        return insertTrack(after_id,track);
    }


    public Track seekByIndex(int index)
    {
        Track track = getTrack(index);
        if (track != null)
        {
            track_index = index;
            dirty |= 1;
            save(false);
        }
        return track;
    }


    public Track seekByOpenId(int open_id)
    {
        Track track = getByOpenId(open_id);
        if (track != null)
        {
            track_index = tracks_by_position.indexOf(track) + 1;
            dirty |= 1;
            save(false);
        }
        return track;
    }


    public Track insertTrack(int after_id, Track track)
        // insert the item after the given open id
        // where 0 means front of the list.
        // returns the item on success
    {
        if (num_tracks == MAX_TRACKS)
        {
            Utils.error("MAX_TRACKS reached");
            return null;    // should result in 801 error
        }

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

        int new_id = next_open_id++;
        int position = insert_idx + 1;
        track.putPosition(position);
        track.putOpenId(new_id);
        Utils.log(dbg_open_pl,0,"addding  " + track.getTitle() + " to Playlist(" + name + ") at position=" + position);


        if (track_db != null)
        {
            if (!track.insert(track_db))
            {
                Utils.error("Could not insert track in playlist db");
                return null;
            }

            String query = "UPDATE tracks SET position=position+1 WHERE position>=" + position;

            try
            {
                playlist_db.rawQuery(query,new String[]{});
            }
            catch (Exception e)
            {
                Utils.error("Could not update track positions(" + track.getPosition() + ")=" + track.getTitle() + " from playlist(" + name + "): " + e.toString());
                return null;
            }
        }

        tracks_by_open_id.put(new_id,track);
        tracks_by_position.add(insert_idx,track);
        num_tracks++;

        int old_track_index = track_index;
        if (track_index == 0) track_index = 1;

        dirty |= 2;
        if (old_track_index != track_index)
            dirty |= 1;
        save(false);

        return track;
    }


    public boolean removeTrack(int open_id)
    {
        Track track = tracks_by_open_id.get(open_id);
        if (track == null)
        {
            Utils.error("Could not remove(" + open_id + ") from playlist(" + name + ")");
            return false;
        }
        int position = tracks_by_position.indexOf(track) + 1;

        if (track_db != null)
        {
            String query1 = "DELETE FROM playlists WHERE position=" + position;
            String query2 = "UPDATE tracks SET position=position-1 WHERE position>" + position;
            try
            {
                playlist_db.rawQuery(query1,new String[]{});
                playlist_db.rawQuery(query2,new String[]{});
            }
            catch (Exception e)
            {
                Utils.error("Could not remove track(" + track.getPosition() + ")=" + track.getTitle() + " from playlist(" + name + "): " + e.toString());
                return false;
            }
        }

        HTTPServer http_server = artisan.getHTTPServer();
        OpenPlaylist open_playlist = http_server == null ? null :
            (OpenPlaylist) http_server.getHandler("Playlist");
        if (open_playlist != null)
            open_playlist.exposeTrack(track,false);


        num_tracks--;
        tracks_by_open_id.remove(track);
        tracks_by_position.remove(track);

        int old_track_index = track_index;
        if (track_index > num_tracks)
            track_index = num_tracks;

        dirty |= 2;
        if (old_track_index != track_index)
            dirty |= 1;

        save(false);
        return true;
    }


    //---------------------------------------------
    // openHome idArray and ReadList
    //---------------------------------------------

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


    public String getIdArrayString(PlaylistExposer exposer)
        // return a base64 encoded array of integers
        // May be called with a PlaylistExposer or not
    {
        int num = 0;
        byte data[] = new byte[num_tracks * 4];
        Utils.log(dbg_open_pl,0,"getIdArrayString() num_tracks="+num_tracks);

        // show debugging for the exposer, if any

        if (exposer != null)
             Utils.log(dbg_open_pl,1,"exposer(" + exposer.getMask() + ") num_exposed=" + exposer.getNumExposed());

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
                 track.getExposed(exposer.getMask())))
            {
                int id = track.getOpenId();
                data[num * 4 + 0] = (byte) ((id >> 24) & 0xFF);
                data[num * 4 + 1] = (byte) ((id >> 16) & 0xFF);
                data[num * 4 + 2] = (byte) ((id >> 8) & 0xFF);
                data[num * 4 + 3] = (byte) (id & 0xFF);

                // Utils.log(dbg_play,2,"id(" + num_exp + ") id=" + id + " ==> " +
                //     String.format("0x%02x 0x%02x 0x%02x 0x%02x",
                //         data[num_exp*4+0],
                //         data[num_exp*4+1],
                //         data[num_exp*4+2],
                //         data[num_exp*4+3]));

                num++;
            }
        }

        // java.util.Arrays.copyOf(data,num_exp);

        byte data2[] = new byte[num * 4];
        for (int i=0; i<num*4; i++)
            data2[i] = data[i];

        String retval = Base64.encode(data2);
        Utils.log(dbg_open_pl+1,1,"id_array Length=" + num);
        Utils.log(dbg_open_pl+1,1,"id_array='" + retval + "'");
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


    public String id_array_to_tracklist(int ids[])
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




    //-------------------------------------------------------------
    //-------------------------------------------------------------
    // SAVE
    //-------------------------------------------------------------
    //-------------------------------------------------------------

    private void save(boolean release)
    // save any dirty playlists
    // does nothing for the default songlist.
    // called with release=true before de-activating in the Renderer,
    // which cleares the "exposed" bit on all the items so when we
    // switch back in Bup, the songs will be exposed incrementally again.
    {
        Utils.log(dbg_open_pl,0,"::: SAVE(release="+release+")");
        if (playlist_db != null && (dirty & 1) == 1)
        {
            //  update the record in the database
        }
        if (track_db != null && (dirty & 1) == 1)
        {
            // loop thru and save any dirty tracks
            // created in memory from didl
        }
    }



}   // class LocalPlaylist

