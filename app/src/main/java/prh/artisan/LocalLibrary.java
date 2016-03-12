package prh.artisan;


import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import prh.artisan.Folder;
import prh.artisan.Library;
import prh.artisan.Record;
import prh.artisan.Track;
import prh.utils.Utils;


public class LocalLibrary extends Library
{
    public static int dbg_lib = -1;

    Artisan artisan;
    private SQLiteDatabase db = null;
    public static LocalLibrary local_library = null;
    public static LocalLibrary getLocalLibrary() { return local_library; }


    public LocalLibrary(Artisan ma)
        // assumes the database is already started
    {
        artisan = ma;
        db = Database.getDB();
        local_library = this;
    }

    public void start()
    {
    }


    public void stop( )
    {
        db = null;
        local_library = null;
    }


    public Track getTrack(String id)
        // returns null on error or track not found
    {
        // virtual "select_playlist"  "tracks"

        if (id.startsWith("select_playlist_"))
        {
            String name = id.replace("select_playlist_","");
            Utils.log(dbg_lib,0,"VIRTUAL get_track(" + id + ")");

            Renderer local_renderer = artisan.getLocalRenderer();
            PlaylistSource playlist_source = local_renderer == null ? null :
                local_renderer.getPlaylistSource();
            Playlist playlist = playlist_source.getPlaylist(name);
            if (playlist == null)
            {
                Utils.error("Could not get playlist for " + id);
                return null;
            }

            HashMap<String, Object> hash = new HashMap<String, Object>();
            hash.put("id","select_playlist_" + name);
            hash.put("parent_id", "select_playlist");
            hash.put("title", name + "(" + (playlist == null ? "0" : Integer.toString(playlist.getNumTracks())) + ")");
            return new Track(hash);
        }

        // return the first record found by the query

        Cursor cursor = null;
        try
        {
            cursor = db.rawQuery("SELECT * FROM tracks WHERE ID=\"" + id + "\"",new String[]{});
        }
        catch (Exception e)
        {
            Utils.error("SQL Error: " + e);
            cursor = null;
        }
        if (cursor != null)
        {
            if (cursor.moveToFirst())
            {
                return new Track(cursor);
            }
        }

        return null;

    }   // getTrack()




    public Folder getFolder(String id)
    {
        // if 0, return a fake root record
        // that represents the the mp3s directory
        // that is probably in the datbase with an id of ""

        if (id.equals("0"))
        {
            HashMap<String, Object> hash = new HashMap<String, Object>();
            hash.put("id","0");
            hash.put("parent_id", "");
            hash.put("title", "All Artisan Folders");
            hash.put("dirtype", "root");
            hash.put("num_elements", 1);
            hash.put("artist", "");
            hash.put("genre", "");
            hash.put("year_str", "");
            return new Folder(hash);
        }

        // a virtual folder that contains playlist items
        // a child of "1" == the mp3s directory

        else if (id.equals("select_playlist"))
        {
            Renderer local_renderer = artisan.getLocalRenderer();
            PlaylistSource playlist_source = local_renderer == null ? null :
                local_renderer.getPlaylistSource();
            if (playlist_source == null)
            {
                Utils.error("Could not get playlist_source");
                return null;
            }

            HashMap<String, Object> hash = new HashMap<String, Object>();
            hash.put("id","select_playlist");
            hash.put("parent_ID","1");
            hash.put("title","Select Playlist");
            hash.put("dirtype", "album");
            hash.put("num_elements",playlist_source.getPlaylistNames().length);
            hash.put("artist", "");
            hash.put("genre", "");
            hash.put("year_str", "");
            return new Folder(hash);
        }

        // return the first record found by the query

        String[] args = new String[]{};
        Cursor cursor = null;
        try
        {
            cursor = db.rawQuery("SELECT * FROM folders WHERE id=\"" + id + "\"", args);
        }
        catch (Exception e)
        {
            Utils.error("SQL Error: " + e);
        }
        if (cursor != null)
        {
            if (cursor.moveToFirst())
            {
                return new Folder(cursor);
            }
        }

        return null;

    }   // getFolder()




    public List<Record> getSubItems(String table,String id,int start,int count)
    {
        if (count == 0) count = 999999;

        int location = 0;
        List<Record> retval = new LinkedList<Record>();
        Utils.log(dbg_lib,0,"getSubItems(" + table + "," + id + "," + start + "," + count + ")");

        // request for select_playlists
        // returns items that are of the form /dlna_renderer/select_playlist_blah.jpg
        // when the dlnaServer recieves a request for this special jpg file, it will
        // change the current playlist, and, apart from backing out of the folder
        // the openHomeRenderer (bubbleUp) should up and show the new station.

        if (id.equals("select_playlist"))
        {
            Renderer local_renderer = artisan.getLocalRenderer();
            PlaylistSource playlist_source = local_renderer == null ? null :
                local_renderer.getPlaylistSource();
            if (playlist_source == null)
            {
                Utils.error("Could not get playlist_source");
                return retval;
            }

            int position = start;
            location = start;
            String lists[] = playlist_source.getPlaylistNames();
            while (position < lists.length && retval.size() < count)
            {
                String name = lists[position++];
                Record vtrack = getTrack("select_playlist_" + name);
                location = addItem(retval,start,location,count,vtrack);
            }
        }

        // normal request for database items

        else
        {
            String sort_clause = table.equals("folders") ? "dirtype DESC," : "";
            String query = "SELECT * FROM " + table + " " +
                "WHERE parent_id=\"" + id + "\" " +
                "ORDER BY " + sort_clause + "path";

            Utils.log(dbg_lib + 2,1,"query=" + query);
            String[] args = new String[]{};
            Cursor cursor = null;
            try
            {
                cursor = db.rawQuery(query,args);
            }
            catch (Exception e)
            {
                Utils.error("SQL Error: " + e);
            }
            Utils.log(dbg_lib + 2,1,"cursor created");


            // add the select_playlist virtual folder first
            // it contains virtual ids that link back to
            // changing the current playlist in the renderer

            if (id.equals("0"))      // the "mp3s" solitary single non-root node
            {
                Utils.log(dbg_lib,2,"adding virtual folder: select_playlist");
                location = addItem(retval,start,location,count,
                    getFolder("select_playlist"));
            }

            if (cursor != null && retval.size() < count)
            {
                Utils.log(dbg_lib,1,"found " + cursor.getCount() + " " + table + " records");
                if (cursor.moveToFirst())
                {
                    if (retval.size() < count)
                        location = addItemFromCursor(retval,start,location,count,table,cursor);
                    while (retval.size() < count && cursor.moveToNext())
                        location = addItemFromCursor(retval,start,location,count,table,cursor);
                }
            }
        }

        Utils.log(dbg_lib,1,"returning " + retval.size() + " subitems");
        return retval;
    }


    private static int addItem(List<Record> list,int start,int location, int count,Record rec)
    {
        if (start <= location && list.size() < count)
        {
            Utils.log(dbg_lib+1,2,rec.get("id") + "  " + rec.get("title"));
            list.add(location-start,rec);
        }
        location++;
        return location;
    }

    private static int addItemFromCursor(List<Record> list,int start,int location, int count, String table, Cursor cursor)
    {
        if (start <= location && list.size() < count)
        {
            Record rec;
            if (table.equals("folders"))
                rec = new Folder(cursor);
            else
                rec = new Track(cursor);
            return addItem(list,start,location,count,rec);
        }
        location++;
        return location;
    }



}
