package prh.artisan;


import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashMap;

import prh.artisan.LocalPlaylist;
import prh.artisan.Playlist;
import prh.artisan.PlaylistSource;
import prh.artisan.Prefs;
import prh.utils.Utils;


public class LocalPlaylistSource extends PlaylistSource
{
    private static int dbg_pls = 1;
    public final static String LOCAL_PLAYLIST_SOURCE_NAME = "Local Playlist Source";

    private ArrayList<LocalPlaylist> playlists = null;
    private HashMap<String,LocalPlaylist> playlists_by_name = null;
    private SQLiteDatabase playlist_db = null;


    @Override
    public String[] getPlaylistNames()
    {
        ArrayList<String> names = new ArrayList<String>();
        for (LocalPlaylist playlist : playlists)
            names.add(playlist.getName());
        return names.toArray(new String[names.size()]);
    }

    @Override
    public Playlist getPlaylist(String name)
    {
        return playlists_by_name.get(name);
    }



    @Override
    public void stop()
    {
        Utils.log(0,0,"LocalPlaylistSource.stop()");

        if (playlist_db != null)
            playlist_db.close();
        playlist_db = null;

        playlists = null;
        playlists_by_name = null;
    }


    @Override
    public void start()
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
                return;
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
                return;
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
    }


    private void addLocalPlayList(String name)
    {
        LocalPlaylist playlist = new LocalPlaylist(playlist_db,name);
        playlists.add(playlist);
        playlists_by_name.put(name,playlist);
    }

}   // class LocalPlaylistSource
