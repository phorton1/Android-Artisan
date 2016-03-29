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


import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.HashMap;

import prh.artisan.Artisan;
import prh.artisan.Database;
import prh.artisan.Playlist;
import prh.artisan.PlaylistSource;
import prh.artisan.Prefs;
import prh.artisan.Track;
import prh.types.intTrackHash;
import prh.types.trackList;
import prh.utils.Utils;


public class LocalPlaylist extends Playlist
    // A Playlist associated with a local database (PlaylistSource),
    // which hides the fact that not all of it's records are actually
    // in memory until getTrack() is called ....
{
    private int dbg_lp = 0;

    // playlist member variables

    protected Artisan artisan;
    protected SQLiteDatabase playlist_db;
    private SQLiteDatabase track_db = null;
    protected boolean is_started;

    // accessors

    @Override public boolean isStarted() { return is_started; }


    //----------------------------------------------------------------
    // Constructor
    //----------------------------------------------------------------

    public LocalPlaylist(Artisan ma, SQLiteDatabase list_db)
        // default constructor creates an un-named playlist ""
        // that is already started
    {
        super(ma);
        artisan = ma;
        playlist_db = list_db;
        is_started = true;
    }


    public LocalPlaylist(Artisan ma,
                         LocalPlaylistSource source,
                         SQLiteDatabase list_db,
                         String name,
                         Playlist playlist)
        // copy constructor that changes name on the fly
        // sets isDirty, and since it got every track,
        // is considered started
    {
        super(ma);
        artisan = ma;
        playlist_db = list_db;
        is_started = true;

        this.name = name;
        this.num_tracks = playlist.getNumTracks();
        this.my_shuffle = playlist.getMyShuffle();
        this.track_index = playlist.getCurrentIndex();

        for (int i=0; i<playlist.getNumTracks(); i++)
        {
            Track track = playlist.getTrack(i+1);
            track.setPosition(i + 1);
            track.setOpenId(next_open_id);
            tracks_by_position.add(track);
            tracks_by_open_id.put(next_open_id,track);
            next_open_id++;
        }

        setDirty(true);
    }


    public LocalPlaylist(Artisan ma, SQLiteDatabase list_db, Cursor cursor)
        // create from the database file
        // does not call clean_init
        // clears is_new, is not started
    {
        super(ma);
        artisan = ma;
        playlist_db = list_db;
        is_started = false;

        HashMap<String,Integer> fields = Database.get_fields("playlists",cursor);
        name = cursor.getString(fields.get("name"));
        num_tracks = cursor.getInt(fields.get("num_tracks"));
        track_index = cursor.getInt(fields.get("track_index"));
        my_shuffle = cursor.getInt(fields.get("shuffle"));
        pl_query = cursor.getString(fields.get("query"));
        Utils.log(dbg_lp+1,1,"LocalPlaylist(" + name + ") num_tracks=" + num_tracks + " track_index=" + track_index + " my_shuffle=" + my_shuffle);

    }   // ctor



    private boolean openTrackDb()
    {
        if (track_db == null)
        {
            String db_name = Prefs.getString(Prefs.id.DATA_DIR) + "/playlists/" + name + ".db";
            File check = new File(db_name);

            if (check.exists()) // open existing database
            {
                try
                {
                    track_db = SQLiteDatabase.openDatabase(db_name,null,0);
                }
                catch (Exception e)
                {
                    Utils.warning(0,0,"Could not open database " + db_name + ". Will try creating ...");
                    return false;
                }
            }
            else // create new database
            {
                try
                {
                    Utils.log(dbg_lp,0,"Creating new playlist track_db file " + db_name);
                    track_db = SQLiteDatabase.openOrCreateDatabase(db_name,null);
                    // weird if above succeeds but below fails
                    if (!Database.createTable(track_db,"tracks"))
                    {
                        track_db = null;
                        return false;
                    }
                    Utils.log(dbg_lp,0,"playlist track_db " + db_name + " created");
                }
                catch (Exception e)
                {
                    Utils.error("Could not create database " + db_name);
                    return false;
                }
            }
        }

        return true;
    }



    //-------------------------------------------------------------
    // Overridden Methods
    //-------------------------------------------------------------

    @Override public void start()
    {
        if (!is_started)
        {
            super.start();  // does nothing

            is_started = true;
            tracks_by_position = new trackList();
            tracks_by_open_id = new intTrackHash();

            if (!name.equals(""))
            {
                if (!openTrackDb())
                {
                    num_tracks = 0;
                    track_index = 0;
                }
                else
                {
                    for (int i = 0; i < num_tracks; i++)
                    {
                        tracks_by_position.add(i,null);
                    }
                }
            }
        }
    }


    @Override public void stop()
        // Keeps the playlist_db
        // Clears the base class track hashes
        // The hashes are built on start
        // No-one can call getByOpenId() on a freshly
        // started playlist until AFTER getTrack for
        // the track has been called.
    {
        is_started = false;
        if (track_db != null)
        {
            track_db.close();
            track_db = null;
        }
        tracks_by_open_id.clear();
        tracks_by_open_id = null;

        tracks_by_position.clear();
        tracks_by_position.clear();

        super.stop();   // does nothing
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
                track.setOpenId(next_open_id);
                tracks_by_position.set(index - 1,track);
                tracks_by_open_id.put(next_open_id,track);
                next_open_id++;
            }
        }

        return track;
    }


    //-----------------------------------------
    // SaveAs
    //-----------------------------------------
    // Save THIS playlist to whatever name is passed in,
    // including all the tracks and the header.
    // Clears the dirty and is_new bits.
    //
    // Implemented as an atomic database operation.

    public boolean saveAs(boolean new_playlist)
    {
        if (name.isEmpty())
        {
            Utils.error("Cannot save un-named Playlist");
            return false;
        }

        //-----------------------------------
        // update or insert the header
        //-----------------------------------
        // start transaction

        playlist_db.beginTransaction();

        ContentValues header_values = new ContentValues();
        header_values.put("name",name);
        header_values.put("num",playlist_num);
        header_values.put("num_tracks",num_tracks);
        header_values.put("track_index",track_index);
        header_values.put("shuffle",my_shuffle);
        header_values.put("query",pl_query);

        if (new_playlist)
        {
            try
            {
                Utils.log(dbg_lp,1,"saveAs(" + name + ") inserting new playlist");
                playlist_db.insertOrThrow("playlists",null,header_values);
            }
            catch (Exception e)
            {
                Utils.error("Could not insert playlist '" + name + "' :" + e);
                playlist_db.endTransaction();
                return false;
            }
        }
        else
        {
            Utils.log(dbg_lp,1,"saveAs(" + name + ") updating existing new playlist");
            int rslt = playlist_db.update("playlists",header_values,"name='" + name + "'",null);
            if (rslt == -1)
            {
                Utils.error("Could not update playlist '" + name + "'");
                playlist_db.endTransaction();
                return false;
            }
        }


        //-----------------------------------------
        // Save the tracks
        //-----------------------------------------

        Utils.log(dbg_lp,0,"saveAs(" + name + ") saving " + num_tracks + " tracks");

        if (!openTrackDb())
        {
            playlist_db.endTransaction();
            return false;
        }

        // empty the current database

        if (!new_playlist)
        {
            try
            {
                track_db.execSQL("DELETE FROM tracks");
            }
            catch (Exception e)
            {
                Utils.error("Could not clear playlist(" + name + "): " + e.toString());
                playlist_db.endTransaction();
                return false;
            }
        }

        // have to loop thru all the tracks
        // to make sure they're in memory

        for (int i = 0; i < num_tracks; i++)
        {
            Track track = getTrack(i + 1);
            track.setPosition(i + 1);
            ContentValues values = Database.getContentValues("tracks",track);
            try
            {
                track_db.insertOrThrow("tracks",null,values);
            }
            catch (Exception e)
            {
                Utils.error("Could not insert track " + track.getTitle() + " into playlist " + name + ":" + e);
                playlist_db.endTransaction();
                return false;
            }
        }

        playlist_db.setTransactionSuccessful();
        playlist_db.endTransaction();
        Utils.log(dbg_lp,0,"localPlaylist.saveTracks(" + name + ") finished");
        return true;

    }   // LocalPlaylistSource.saveTracks()



}   // class LocalPlaylist

