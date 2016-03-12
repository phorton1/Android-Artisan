package prh.artisan;

// The positions in the database file are used to
// create the initial in-memory list. Thereafter
// the position is the position of the track in
// the list after it has been de-cached.
//
// The positions in the database are updated as
// tracks are inserted and deleted.


import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.LinkedList;
import prh.utils.Base64;
import prh.utils.Utils;


public class LocalPlaylist extends Playlist
{
    private int dbg_pl = 0;
    private int dbg_open_pl = -1;

    public static int MAX_TRACKS = 1000;

    // member variables
    
    private String name ="";
    private int num = 0;
    private int num_tracks = 0;
    private int track_index = 0;
    private int my_shuffle = 0;


    private int dirty = 0;
        // bitwise 1=playlist, 2=tracks
    private static int next_open_id = 1;

    // it is not necessary to call start on a new default null,"" playlist
    // all members are set here ...

    private HashMap<Integer,Track> tracks_by_open_id = new HashMap<Integer,Track>();
    private LinkedList<Track> tracks_by_position =  new LinkedList<Track>();
        // list of in-memory tracks
        // the linked list is flushed out with null members during start()

    private SQLiteDatabase track_db = null;
    private SQLiteDatabase playlist_db = null;
        // these are null for un-named default playlist

    //-------------------------------------------------------------
    // accessors (Playlist interface)
    //-------------------------------------------------------------

    public String getName()         { return name; }
    public int getNum()             { return num; }
    public int getNumTracks()       { return num_tracks; }
    public int getCurrentIndex()    { return track_index; }
    public int getMyShuffle()       { return my_shuffle; }


    //----------------------------------------------------------------
    // Constructor
    //----------------------------------------------------------------

    public LocalPlaylist(SQLiteDatabase list_db,String name)
    {
        this.name = name;
        playlist_db = list_db;
        Utils.log(dbg_pl,0,"LocalPlaylist(" + name + ") ...");

        // allow for construction of default "" playlist
        // without any db

        if (playlist_db == null || name.equals(""))
        {
            this.name = "";
            playlist_db = null;
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


    public void start()
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
        expose_more();
    }


    public void stop()
    {
        if (open_home_server != null)
        {
            for (int i = 0; i < num_tracks; i++)
            {
                Track track = tracks_by_position.get(i);
                if (track != null)
                    track.setExposed(false);
            }
            num_exposed = 0;
            open_home_server = null;
        }

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


    public Track getCurrentTrack()
        // could return null
    {
        return getTrack(track_index);
    }


    public Track getTrack(int index)
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

    public Track getByOpenId(int open_id)
    {
        return tracks_by_open_id.get(open_id);
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
            String query2 = "UPDATE SET position=position-1 FROM tracks WHERE position>" + position;
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

            String query = "UPDATE SET position=position+1 FROM tracks WHERE position>=" + position;

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

        if (track != null)
            expose(position);

        return track;
    }


    public String getIdArrayString()
    // return a base64 encoded array of integers
    //
    // In order to facilitate BuP responsiveness,
    // assuming only one Bup client using our openRenderer,
    // We expose the playlist 50 items at a time ...
    // first exposing the current track and those items
    // following it, then those slighly before it,
    // expanding the selection outwards,
    //
    // The "loop" is formed by having the OpenPlaylist ReadList
    // call the expose_more() method, and if it returns true,
    // generating another (pending) PlayList event, feeding
    // some more id's to Bup.
    {
        int num_exp = 0;
        byte data[] = new byte[num_tracks * 4];
        Utils.log(dbg_open_pl,0,"getIdArrayString() num_tracks="+num_tracks +" num_exposed=" + num_exposed);

        for (int index=1; index<=num_tracks; index++)
        {
            Track track = tracks_by_position.get(index-1);
                // not exposed if null

            if (open_home_server == null || track != null && track.getExposed())
            {
                int id = track.getOpenId();
                data[num_exp * 4 + 0] = (byte) ((id >> 24) & 0xFF);
                data[num_exp * 4 + 1] = (byte) ((id >> 16) & 0xFF);
                data[num_exp * 4 + 2] = (byte) ((id >> 8) & 0xFF);
                data[num_exp * 4 + 3] = (byte) (id & 0xFF);

                // Utils.log(dbg_play,2,"id(" + num_exp + ") id=" + id + " ==> " +
                //     String.format("0x%02x 0x%02x 0x%02x 0x%02x",
                //         data[num_exp*4+0],
                //         data[num_exp*4+1],
                //         data[num_exp*4+2],
                //         data[num_exp*4+3]));

                num_exp++;
            }
        }

        // java.util.Arrays.copyOf(data,num_exp);

        byte data2[] = new byte[num_exp * 4];
        for (int i=0; i<num_exp*4; i++)
            data2[i] = data[i];

        String retval = Base64.encode(data2);
        Utils.log(dbg_open_pl+1,1,"id_array num_exp=" + num_exp);
        Utils.log(dbg_open_pl+1,1,"id_array='" + retval + "'");
        return retval;
    }





    //-------------------------------------------------------------
    //-------------------------------------------------------------
    // SAVE
    //-------------------------------------------------------------
    //-------------------------------------------------------------

    public void save(boolean release)
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

