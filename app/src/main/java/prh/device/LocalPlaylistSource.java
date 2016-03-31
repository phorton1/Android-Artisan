package prh.device;


import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

import prh.artisan.Artisan;
import prh.artisan.EventHandler;
import prh.artisan.Playlist;
import prh.artisan.PlaylistSource;
import prh.artisan.Prefs;
import prh.server.SSDPServer;
import prh.types.stringList;
import prh.utils.Utils;


public class LocalPlaylistSource extends Device implements PlaylistSource
{
    private static int dbg_pls = 1;

    private HashMap<String,LocalPlaylist> playlists = null;
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

        Utils.log(dbg_pls + 1,1,"new LocalPlaylistSource()");

        device_urn = "schemas-artisan-home";
        // not used anyways
        // httpUtils.upnp_urn;
        friendlyName = deviceType.LocalPlaylistSource.toString();
        device_url = Utils.server_uri;
        icon_path = "/icons/artisan.png";
        device_status = deviceStatus.ONLINE;

    }


    @Override
    public boolean isLocal()
    {
        return true;
    }

    @Override
    public String getPlaylistSourceName()
    {
        return getFriendlyName();
    }


    //------------------------------------
    // PlaylistSource Interface
    //------------------------------------

    @Override
    public Playlist createEmptyPlaylist()
    {
        return new LocalPlaylist(artisan,playlist_db);
    }


    @Override
    public Playlist getPlaylist(String name)
    // this should be the only place that
    // new LocalPlaylist() is called
    {
        if (name.isEmpty())
            return new LocalPlaylist(artisan,playlist_db);
        return playlists.get(name);
    }



    @Override
    public stringList getPlaylistNames()
    {
        Playlist by_name[] = playlists.values().toArray(new Playlist[playlists.size()]);
        Arrays.sort(by_name,new Comparator<Playlist>() {
            public int compare(Playlist lhs,Playlist rhs)
            {
                int lhn = lhs.getPlaylistNum();
                int rhn = rhs.getPlaylistNum();
                int cmp = lhn-rhn;
                if (cmp != 0) return cmp;
                return lhs.getName().compareTo(rhs.getName());
            }});

        stringList names = new stringList();
        for (Playlist playlist : by_name)
            names.add(playlist.getName());
        return names;
    }


    @Override
    public void stopPlaylistSource(boolean wait_for_stop)
    {
        Utils.log(0,0,"LocalPlaylistSource.stop()");

        if (playlist_db != null)
            playlist_db.close();
        playlist_db = null;

        playlists = null;
    }


    @Override
    public boolean startPlaylistSource()
    // The local playlist source NEVER fails to start
    // It just starts up empty if there's no db or
    // playlist.db
    {
        Utils.log(0,0,"LocalPlaylistSource.start()");

        if (playlists == null)
        {
            playlists = new HashMap<String,LocalPlaylist>();

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
            String query = "SELECT * FROM playlists ORDER BY num,name";
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
                addLocalPlayList(cursor);
                while (cursor.moveToNext())
                    addLocalPlayList(cursor);
            }
        }

        return true;
    }


    private void addLocalPlayList(Cursor cursor)
    {
        LocalPlaylist playlist = new LocalPlaylist(artisan,playlist_db,cursor);
        playlists.put(playlist.getName(),playlist);
    }



    //------------------------------------
    // saveAs()
    //------------------------------------


    @Override
    public boolean saveAs(Playlist playlist, String name)
    {
        Utils.log(dbg_pls,0,"LocalPlaylistSource.saveAs(" + name + ") num_tracks" + playlist.getNumTracks());

        // find or create the playlist header

        LocalPlaylist exists = playlists.get(name);
        if (exists == null)
        {
            Utils.log(dbg_pls,1,"saveAs(" + name + ") is a new playlist");
        }
        LocalPlaylist new_pl = new LocalPlaylist(artisan,this,playlist_db,name,playlist);
        if (new_pl == null)
        {
            Utils.error("Could not create new playlist " + name);
            return false;
        }

        // save the stuff
        // and set the source playlist as also clean now

        if (!new_pl.saveAs(exists==null))
            return false;
        playlist.setDirty(false);

        // finished, notify artisan as needed

        playlists.put(name,new_pl);
        artisan.handleArtisanEvent(EventHandler.EVENT_PLAYLIST_SOURCE_CHANGED,this);

        Utils.log(dbg_pls,1,"saveAs(" + name + ") finished");
        return true;

    }   // LocalPlaylistSource.saveAs()


    public boolean deletePlaylist(String name)
    {
        LocalPlaylist exists = playlists.get(name);
        if (exists != null)
        {
            Utils.log(dbg_pls,0,"deletePlaylist(" + name + " deleting playlist");
            exists.stopPlaylist(false);
            playlists.remove(name);
            try
            {
                playlist_db.execSQL("DELETE FROM playlists WHERE name='" + name + "'");
            }
            catch (Exception e)
            {
                Utils.error("Could not delete playlist " + name + ":" + e);
                return false;
            }

            String db_name = Prefs.getString(Prefs.id.DATA_DIR) + "/playlists/" + name + ".db";
            File check = new File(db_name);
            if (check.exists())
            {
                Utils.log(dbg_pls,0,"deletePlaylist(" + name + " deleting tracks");
                if (!check.delete())
                {
                    Utils.error("Could not delete file db_name");
                    return false;
                }
            }
        }
        artisan.handleArtisanEvent(EventHandler.EVENT_PLAYLIST_SOURCE_CHANGED,this);
        return true;
    }


}   // class LocalPlaylistSource
