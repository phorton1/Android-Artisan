package prh.device;

// Constructing a LocalPlaylist is very quick, requiring
// only 1 hit to the playlist.db file.
//
// The array of tracks is built on start() and torn down
// on stop().
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
import prh.base.Playlist;
import prh.base.PlaylistSource;
import prh.artisan.Prefs;
import prh.artisan.Track;
import prh.server.HTTPServer;
import prh.server.http.OpenPlaylist;
import prh.types.trackList;
import prh.utils.Utils;


public class LocalPlaylist implements Playlist // EditablePlaylist
    // A Playlist associated with a local database (PlaylistSource),
{
    private int dbg_lp = 0;

    // member variables

    private Artisan artisan;
    private LocalPlaylistSource source;
    private boolean is_started;
    private boolean is_dirty;

    private String name;
    private int playlist_num;
    private int num_tracks;
    private int my_shuffle;
    private String pl_query;
    private int track_index;      // one based

    private trackList tracks_by_position;

    // accessors

    @Override public PlaylistSource getSource() { return source; }

    @Override public String getName()         { return name; }
    @Override public int getPlaylistNum()     { return playlist_num; }
    @Override public int getNumTracks()       { return num_tracks; }
    @Override public int getCurrentIndex()    { return track_index; }
    @Override public Track getCurrentTrack()  { return getTrack(track_index); }
    @Override public int getMyShuffle()       { return my_shuffle; }
    @Override public String getQuery()        { return pl_query; }

    @Override public boolean isDirty() { return is_dirty; }
    @Override public void setDirty(boolean b) { is_dirty = b; }

    @Override public int getNumAvailableTracks()
    {
        return tracks_by_position.size();
    }

    @Override public Track getTrack(int index)
    {
        if (index <= 0 || index > num_tracks)
            return null;
        Track track = tracks_by_position.get(index - 1);
        if (track == null)
            Utils.warning(0,0,"No Track at 1-based position " + index);
        return track;
    }


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


    // when playlist changes
    // prh.server.http.OpenPlaylist open_playlist =
    //        getHttpOpenPlaylist();
    //        if (open_playlist != null)
    //             open_playlist.clearAllExposers();

    // Exposer Support - expose the first track
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



    //---------------------------------------------------------------
    // EditablePlaylist Interface
    //---------------------------------------------------------------
    /*
    @Override public int getContentChangeId() { return 0; }

    // FetcherSource Interface

    @Override public int getRecChangedCountId()  { return 0; }
    @Override public trackList getFetchedTrackList()    { return tracks_by_position; }
    @Override public Fetcher.fetchResult getFetcherPlaylistRecords(int start, int num)
        { return Fetcher.fetchResult.FETCH_DONE; }
    */

    //----------------------------------------------------------------
    // Constructor
    //----------------------------------------------------------------


    private void clean_init()
    {
        name = "";
        my_shuffle = 0;
        num_tracks = 0;
        track_index = 0;
        playlist_num = 0;
        pl_query = "";
        is_started = false;
        tracks_by_position =  new trackList();
    }


    public LocalPlaylist(Artisan ma,
                         LocalPlaylistSource src)
        // default constructor creates an un-named playlist ""
        // that is already started
    {
        artisan = ma;
        source = src;
        clean_init();
        is_started = true;
    }


    public LocalPlaylist(Artisan ma,
                         LocalPlaylistSource src,
                         String name,
                         Playlist playlist)
        // copy constructor that changes name on the fly
        // sets isDirty, and since it got every track,
        // is considered started
    {
        artisan = ma;
        source = src;
        clean_init();
        is_started = true;

        this.name = name;
        this.num_tracks = playlist.getNumTracks();
        this.my_shuffle = playlist.getMyShuffle();
        this.track_index = playlist.getCurrentIndex();

        // we assign the position in case it was mucked up
        // and give the items an entire new set of open_ids;

        for (int i=0; i<playlist.getNumTracks(); i++)
        {
            Track track = playlist.getTrack(i + 1);
            track.setPosition(i + 1);
            tracks_by_position.add(track);
        }
    }


    public LocalPlaylist(Artisan ma, LocalPlaylistSource src, Cursor cursor)
        // create from the database file
        // does not call clean_init
        // clears is_new, is not started
    {
        artisan = ma;
        clean_init();
        source = src;

        HashMap<String,Integer> fields = Database.get_fields("playlists",cursor);
        name = cursor.getString(fields.get("name"));
        num_tracks = cursor.getInt(fields.get("num_tracks"));
        track_index = cursor.getInt(fields.get("track_index"));
        my_shuffle = cursor.getInt(fields.get("shuffle"));
        playlist_num = cursor.getInt(fields.get("num"));
        pl_query = cursor.getString(fields.get("query"));
        Utils.log(dbg_lp+1,1,"LocalPlaylist(" + name + ") num_tracks=" + num_tracks + " track_index=" + track_index + " my_shuffle=" + my_shuffle);

    }   // ctor


    // saveIndex

    @Override public void saveIndex(int index)
    {
        track_index = index;

        SQLiteDatabase playlist_db = source.openPlaylistDb();

        if (playlist_db != null)
        {
            try
            {
                playlist_db.execSQL("UPDATE playlists SET track_index=" +
                    track_index + " WHERE name ='" + name + "'");
                playlist_db.close();
            }
            catch (Exception e)
            {
                Utils.error("Could not saveIndex(" + name + "," + track_index + ")");
            }
        }
    }


    //-----------------------------------------------------------
    // startPlaylist() and stopPlaylist()
    //-----------------------------------------------------------

    private SQLiteDatabase openTrackDb(boolean create_if_not_found)
    {
        String db_name = Prefs.getString(Prefs.id.DATA_DIR) + "/playlists/" + name + ".db";

        File check = new File(db_name);
        SQLiteDatabase track_db = null;
        if (check.exists()) // open existing database
        {
            try
            {
                Utils.log(dbg_lp,0,"Open existing track_db file " + db_name);
                track_db = SQLiteDatabase.openDatabase(db_name,null,SQLiteDatabase.OPEN_READWRITE);
            }
            catch (Exception e)
            {
                if (create_if_not_found)
                    Utils.warning(0,0,"Could not open database " + db_name + ". Will try creating ...");
                else
                {
                    Utils.warning(0,0,"Could not open database " + db_name + ")");
                    return null;
                }
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
                    return null;
                }
                Utils.log(dbg_lp,0,"playlist track_db " + db_name + " created");
            }
            catch (Exception e)
            {
                Utils.error("Could not create database " + db_name);
                return null;
            }
        }

        return track_db;
    }



    @Override public boolean startPlaylist()
    {
        Utils.log(dbg_lp,0,"startPlaylist(" + name + ") is_already_started="+is_started);
        if (!is_started)
        {
            is_started = true;
            tracks_by_position = new trackList();

            if (!name.equals(""))
            {
                SQLiteDatabase track_db = openTrackDb(false);
                if (track_db == null)
                {
                    clean_init();
                    return false;
                }
                else
                {
                    Cursor cursor = null;
                    String query = "SELECT * FROM tracks ORDER BY position";
                    try
                    {
                        cursor = track_db.rawQuery(query,null);
                    }
                    catch (Exception e)
                    {
                        Utils.error("Could not execute query: " + query + ":" + e);
                        clean_init();
                        return false;
                    }

                    // construct and add the tracks

                    if (cursor != null && cursor.moveToFirst())
                    {
                        Utils.log(dbg_lp,1,"adding " + cursor.getCount() + " tracks ...");
                        tracks_by_position.add(new Track(cursor));
                        while (cursor.moveToNext())
                            tracks_by_position.add(new Track(cursor));
                    }
                }
            }
        }
        Utils.log(dbg_lp,0,"startPlaylist(" + name + ") returning true");
        return true;
    }




    @Override public void stopPlaylist(boolean wait_for_stop)
        // Keeps the playlist_db
        // Clears the base class track hashes
        // The hashes are built on start
        // No-one can call getByOpenId() on a freshly
        // started playlist until AFTER getTrack for
        // the track has been called.
    {
        Utils.log(dbg_lp,0,"stopPlaylist(" + name + ") called");
        is_started = false;
        if (tracks_by_position != null)
            tracks_by_position.clear();
        tracks_by_position.clear();
        Utils.log(dbg_lp,0,"stopPlaylist(" + name + ") finished");
    }




    //-------------------------------------------------------------------------
    // SaveAs
    //-------------------------------------------------------------------------
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

        SQLiteDatabase playlist_db = source.openPlaylistDb();
        if (playlist_db == null)
            return false;

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
                playlist_db.close();
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
                playlist_db.close();
                return false;
            }
        }


        //-----------------------------------------
        // Save the tracks
        //-----------------------------------------

        Utils.log(dbg_lp,0,"saveAs(" + name + ") saving " + num_tracks + " tracks");

        SQLiteDatabase track_db = openTrackDb(true);
        if (track_db == null)
        {
            playlist_db.endTransaction();
            playlist_db.close();
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
                playlist_db.close();
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
                playlist_db.close();
                return false;
            }
        }

        playlist_db.setTransactionSuccessful();
        playlist_db.endTransaction();
        playlist_db.close();

        Utils.log(dbg_lp,0,"localPlaylist.saveTracks(" + name + ") finished");
        return true;

    }   // LocalPlaylistSource.saveTracks()




}   // class LocalPlaylist

