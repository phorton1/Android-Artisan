package prh.device;


import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashMap;

import prh.artisan.Artisan;
import prh.artisan.CurrentPlaylist;
import prh.artisan.Database;
import prh.artisan.Playlist;
import prh.artisan.PlaylistSource;
import prh.artisan.Prefs;
import prh.server.SSDPServer;
import prh.utils.Utils;
import prh.utils.httpUtils;


public class LocalPlaylistSource extends Device implements PlaylistSource
{
    private static int dbg_pls = 1;

    private ArrayList<LocalPlaylist> playlists = null;
    private HashMap<String,LocalPlaylist> playlists_by_name = null;
    private SQLiteDatabase playlist_db = null;


    //------------------------------------
    // Extend Device
    //------------------------------------

    public LocalPlaylistSource(Artisan a)
    {
        super(a);
        device_type = deviceType.LocalPlaylistSource;
        device_group = deviceGroup.DEVICE_GROUP_PLAYLIST_SOURCE;
        device_uuid = SSDPServer.dlna_uuid[SSDPServer.IDX_OPEN_HOME];
            // overuse of http_server openHome uuid as the
            // uuid of this LocalPlaylistSource

        device_urn = "schemas-artisan-home";
            // not used anyways
            // httpUtils.upnp_urn;
        friendlyName = deviceType.LocalPlaylistSource.toString();
        device_url = Utils.server_uri;
        icon_path = "/icons/artisan.png";
        Utils.log(dbg_pls+1,1,"new LocalPlaylistSource()");
    }


    @Override public boolean isLocal()
    {
        return true;
    }

    @Override public String getName()
    {
        return getFriendlyName();
    }


    //------------------------------------
    // PlaylistSource Interface
    //------------------------------------

    @Override public Playlist createEmptyPlaylist()
    {
        return new LocalPlaylist(artisan,this);
    }


    @Override public Playlist getPlaylist(String name)
        // this should be the only place that
        // new LocalPlaylist() is called
    {
        if (name.isEmpty())
            return new LocalPlaylist(artisan,this);
        return playlists_by_name.get(name);
    }


    @Override public String[] getPlaylistNames()
    {
        ArrayList<String> names = new ArrayList<String>();
        for (LocalPlaylist playlist : playlists)
            names.add(playlist.getName());
        return names.toArray(new String[names.size()]);
    }



    @Override public void stop()
    {
        Utils.log(0,0,"LocalPlaylistSource.stop()");

        if (playlist_db != null)
            playlist_db.close();
        playlist_db = null;

        playlists = null;
        playlists_by_name = null;
    }


    @Override public boolean start()
        // The local playlist source NEVER fails to start
        // It just starts up empty if there's no db or
        // playlist.db
    {
        Utils.log(0,0,"LocalPlaylistSource.start()");

        if (playlists == null)
        {
            playlists = new ArrayList<LocalPlaylist>();
            playlists_by_name = new HashMap<String,LocalPlaylist>();

            // open the database file

            String db_name = Prefs.getString(Prefs.id.DATA_DIR) + "/playlists.db";

            try
            {
                playlist_db = SQLiteDatabase.openDatabase(db_name,null,0);  // SQLiteDatabase.OPEN_READONLY);
            }
            catch (Exception e)
            {
                Utils.error("Could not open database " + db_name);
                return true; // false;
            }

            // get the names from it

            Utils.log(dbg_pls,1,"getting playlist.db records ...");
            String[] args = new String[]{};
            String query = "SELECT name FROM playlists ORDER BY num,name";
            Cursor cursor = null;
            try
            {
                cursor = playlist_db.rawQuery(query,args);
            }
            catch (Exception e)
            {
                Utils.error("Could not execute query: " + query);
                return true; // false;
            }

            // construct the playlists
            // the name is 0th field in the cursor

            if (cursor != null && cursor.moveToFirst())
            {
                Utils.log(dbg_pls,1,"adding " + cursor.getCount() + " playlists ...");
                addLocalPlayList(cursor.getString(0));
                while (cursor.moveToNext())
                    addLocalPlayList(cursor.getString(0));
            }
        }

        return true;
    }


    private void addLocalPlayList(String name)
    {
        LocalPlaylist playlist = new LocalPlaylist(artisan,this,playlist_db,name);
        playlists.add(playlist);
        playlists_by_name.put(name,playlist);
    }


    //------------------------------------
    // saveAs()
    //------------------------------------

    @Override public boolean saveAs(CurrentPlaylist current_playlist, String name)
    {
        if (playlists_by_name.get(name) != null)
        {
            Utils.error("Playlist(" + name + ") already exists in LocalPlaylistSource");
            return false;
        }

        return current_playlist.saveAs(name);
    }





}   // class LocalPlaylistSource
