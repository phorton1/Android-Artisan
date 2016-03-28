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

import prh.artisan.Artisan;
import prh.artisan.Database;
import prh.artisan.Playlist;
import prh.artisan.PlaylistSource;
import prh.artisan.Prefs;
import prh.artisan.Track;
import prh.utils.Utils;


public class LocalPlaylist extends Playlist
    // A Playlist associated with a local database (PlaylistSource),
    // which hides the fact that not all of it's records are actually
    // in memory until getTrack() is called ....
{
    private int dbg_lp = 0;

    // playlist member variables

    protected Artisan artisan;
    protected LocalPlaylistSource parent;
    private SQLiteDatabase track_db;
    protected SQLiteDatabase playlist_db;
    protected boolean is_started;

    // accessors

    public PlaylistSource getParent() { return parent; }
    @Override public boolean isStarted() { return is_started; }


    //----------------------------------------------------------------
    // Constructor
    //----------------------------------------------------------------

    protected void clean_init_local_playlist()
    {
        parent = null;
        track_db = null;
        playlist_db = null;
        is_started = false;
    }


    public LocalPlaylist(Artisan ma, LocalPlaylistSource source)
        // default constructor creates an un-named playlist ""
    {
        super(ma);
        artisan = ma;
        clean_init_local_playlist();
        parent = source;
    }


    public LocalPlaylist(Artisan ma, LocalPlaylistSource source, SQLiteDatabase list_db, String name)
        // create from the database file
    {
        super(ma);
        artisan = ma;
        playlist_db = list_db;
        this.name = name;
        Utils.log(dbg_lp+1,0,"LocalPlaylist(" + name + ") ...");

        // allow for construction of default "" playlist
        // without any db

        if (playlist_db == null || name.equals(""))
        {
            playlist_db = null;
            this.name = "";
            return;
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
                Utils.log(dbg_lp+1,1,"LocalPlaylist(" + name + ") num_tracks=" + num_tracks + " track_index=" + track_index + " my_shuffle=" + my_shuffle);

            }
        }

        // finished

       Utils.log(dbg_lp+1,1,"LocalPlaylist(" + name + ") finished");

    }   // ctor



    private boolean openTrackDb()
    {
        if (track_db == null)
        {
            String db_name = Prefs.getString(Prefs.id.DATA_DIR) + "/playlists/" + name + ".db";
            try
            {
                track_db = SQLiteDatabase.openDatabase(db_name,null,0); // SQLiteDatabase.OPEN_READONLY);
            }
            catch (Exception e)
            {
                Utils.error("Could not open database " + db_name);
                return false;
            }
        }
        return true;
    }



    //-------------------------------------------------------------
    // Overridden Methods
    //-------------------------------------------------------------

    @Override public void start()
    {
        super.start();
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
        is_started = true;
    }


    @Override public void stop()
        // saving done by closeOK()
    {
        is_started = false;

        if (track_db != null)
        {
            track_db.close();
            track_db = null;
        }
        playlist_db = null;
        super.stop();
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
            openTrackDb();
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
                // Utils.log(dbg_pl+2,5,"got db_track(" + next_open_id + ") " + track.getTitle());

                track.setOpenId(next_open_id);
                tracks_by_position.set(index - 1,track);
                tracks_by_open_id.put(next_open_id,track);
                next_open_id++;
            }
        }

        return track;
    }


    //-----------------------------------------
    // Save
    //-----------------------------------------


    @Override
    public boolean saveAs(String name)
    {
        this.name = name;
        return save();
    }


    @Override public boolean save()
    {
        Utils.log(dbg_lp,0,"::: SAVE(");

        if (name.isEmpty())
        {
            Utils.error("Cannot save un-named Playlist");
            return false;
        }

        if (!isDirty())
        {
            Utils.log(0,0,"save(" + name + ") called on clean playlist");
            return true;
        }

        if (!openTrackDb())
            return false;


        try
        {
            Cursor cursor = null;
            String query = "DELETE FROM tracks";
            cursor = track_db.rawQuery(query,new String[]{});
        }
        catch (Exception e)
        {
            Utils.error("Could not clear playlist(" + name + "): " + e.toString());
            return false;
        }

        for (int i=0; i<num_tracks; i++)
        {
            Track track = getTrack(i + 1);
            track.setPosition(i + 1);
            if (!track.insert(track_db))
                return false;
        }

        setDirty(false);
        return true;
    }



}   // class LocalPlaylist

