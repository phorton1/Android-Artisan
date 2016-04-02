package prh.device;


import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import prh.artisan.Artisan;
import prh.artisan.Database;
import prh.artisan.Folder;
import prh.artisan.interfaces.Library;
import prh.artisan.interfaces.Playlist;
import prh.artisan.interfaces.PlaylistSource;
import prh.artisan.Record;
import prh.artisan.Track;
import prh.server.SSDPServer;
import prh.types.libraryBrowseResult;
import prh.types.stringList;
import prh.utils.Utils;
import prh.utils.httpUtils;


public class LocalLibrary extends Device implements Library
{
    public static int dbg_lib = 0;

    public final static boolean SHOW_PLAYLISTS = true;

    private SQLiteDatabase db = null;
    public static LocalLibrary local_library = null;
    public static LocalLibrary getLocalLibrary() { return local_library; }
        // for Artisan-less access by Track and Folder
        // for convertToLocal()

    private Folder root_folder = null;

    //----------------------------------------
    // Device Interface
    //----------------------------------------

    public LocalLibrary(Artisan a)
    {
        super(a);
        db = Database.getDB();

        Utils.log(dbg_lib+1,0,"new LocalLibrary()");

        device_type = deviceType.LocalLibrary;
        device_group = deviceGroup.DEVICE_GROUP_LIBRARY;
        device_uuid = SSDPServer.dlna_uuid[SSDPServer.IDX_DLNA_SERVER];
        device_urn = httpUtils.upnp_urn;
        friendlyName = deviceType.LocalLibrary.toString();
        device_url = Utils.server_uri;
        icon_path = "/icons/artisan.png";
        device_status = deviceStatus.ONLINE;
    }


    @Override public boolean isLocal()
    {
        return true;
    }


    //----------------------------------------
    // Library interface
    //----------------------------------------


    @Override public Folder getRootFolder()
    {
        return getLibraryFolder("0");
    }


    @Override public boolean startLibrary()
    {
        Utils.log(0,0,"LocalLibrary started");
        if (db != null)
        {
            root_folder = getLibraryFolder("0");
            if (root_folder == null)
                Utils.error("No root folder in LocalLibrary.startLibary()");
            else
                return true;
        }
        else
            Utils.error("No db in LocalLibrary.startLibary()");
        return false;
    }


    @Override public void stopLibrary(boolean wait)
    {
        Utils.log(0,0,"LocalLibary stopped");
        // db = null;
        // local_library = null;
    }

    @Override public String getLibraryName()
    {
        return getFriendlyName();
    }


    //---------------------------------------
    // LocalLibrary specific API
    //---------------------------------------

    public Track getLibraryTrack(String id)
        // returns null on error or track not found
    {
        // virtual "select_playlist"  "tracks"

        if (id.startsWith("select_playlist_"))
        {
            String name = id.replace("select_playlist_","");
            Utils.log(dbg_lib+1,0,"VIRTUAL get_track(" + id + ")");

            PlaylistSource playlist_source = artisan.getPlaylistSource();
                // NEVER NULL
            Playlist playlist = playlist_source.getPlaylist(name);
            if (playlist == null)
            {
                Utils.error("Could not get playlist for " + id);
                return null;
            }

            Track track = new Track();
            track.setId("select_playlist_" + name);
            track.setParentId("select_playlist");
            track.setTitle(name + "(" + (playlist == null ? "0" : Integer.toString(playlist.getNumTracks())) + ")");
            return track;
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




    public Folder getLibraryFolder(String id)
    {
        // if 0, return a fake root record
        // that represents the the mp3s directory
        // that is probably in the datbase with an id of ""

        if (id.equals("0"))
        {
            int num_elements = SHOW_PLAYLISTS ? 1 : 0;

            Cursor cursor = null;
            try
            {
                cursor = db.rawQuery("SELECT id FROM folders WHERE parent_id='0'",null);
            }
            catch (Exception e)
            {
                Utils.error("SQL Error: " + e);
                return null;
            }
            num_elements += cursor.getCount();

            Folder folder = new Folder();

            folder.setId("0");
            folder.setTitle("All Artisan Folders");
            folder.setNumElements(num_elements);
            folder.setType("root");
            //folder.setParentId("");
            //folder.setArtist("");
            //folder.setGenre("");
            //folder.setYearString("");
            return folder;
        }

        // a virtual folder that contains playlist items
        // a child of "1" == the mp3s directory

        else if (id.equals("select_playlist"))
        {
            PlaylistSource playlist_source = artisan.getPlaylistSource();
                // Never Null

            Folder folder = new Folder();
            folder.setId("select_playlist");
            folder.setParentId("1");
            folder.setTitle("Select Playlist");
            folder.setType("folder");
            folder.setNumElements(playlist_source.getPlaylistNames().size());
            return folder;
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




    public libraryBrowseResult getSubItems(String id,int start,int count, boolean unsupported_meta_data)
    {
        if (count == 0) count = 999999;

        int location = 0;
        int total_found = 0;
        libraryBrowseResult retval = new libraryBrowseResult();
        Utils.log(dbg_lib,0,"getSubItems(" + id + "," + start + "," + count + ")");

        if (unsupported_meta_data)
        {
            Utils.error("LocalLibrary does not support BrowseMetaData");
            return retval;
        }

        // request for select_playlists
        // returns items that are of the form /dlna_renderer/select_playlist_blah.jpg
        // when the dlnaServer recieves a request for this special jpg file, it will
        // change the current playlist, and, apart from backing out of the folder
        // the openHomeRenderer (bubbleUp) should up and show the new station.

        if (id.equals("select_playlist"))
        {
            PlaylistSource playlist_source = artisan.getPlaylistSource();
                // NEVER NULL

            int position = start;
            location = start;
            stringList lists = playlist_source.getPlaylistNames();
            total_found += lists.size();
            while (position < lists.size() && retval.size() < count)
            {
                String name = lists.get(position++);
                Record vtrack = getLibraryTrack("select_playlist_" + name);
                location = addItem(retval,start,location,count,vtrack);
            }
        }

        // normal request for database items
        // currently do not support calls for meta_data,
        // or sub-items of tracks ...

        else
        {
            Folder folder = getLibraryFolder(id);
            boolean is_album = folder.getType().equals("album");
            String table = is_album ? "tracks" : "folders";
            String sort_clause = is_album ? "" : "dirtype DESC,";
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

            if (SHOW_PLAYLISTS && id.equals("0"))      // the "mp3s" solitary single non-root node
            {
                Utils.log(dbg_lib,2,"adding virtual folder: select_playlist");
                total_found ++;
                location = addItem(retval,start,location,count,
                    getLibraryFolder("select_playlist"));
            }

            if (cursor != null)
                total_found += cursor.getCount();

            if (cursor != null && retval.size() < count)
            {
                Utils.log(dbg_lib +1,1,"found " + cursor.getCount() + " " + table + " records");
                if (cursor.moveToFirst())
                {
                    if (retval.size() < count)
                        location = addItemFromCursor(retval,start,location,count,table,cursor);
                    while (retval.size() < count && cursor.moveToNext())
                        location = addItemFromCursor(retval,start,location,count,table,cursor);
                }
            }
        }

        retval.setTotalFound(total_found);
        Utils.log(dbg_lib,1,"returning " + retval.size() + " of " + total_found + " subitems");
        return retval;
    }


    private static int addItem(libraryBrowseResult list,int start,int location, int count,Record rec)
    {
        if (start <= location && list.size() < count)
        {
            Utils.log(dbg_lib+1,2,rec.get("id") + "  " + rec.get("title"));
            list.add(location-start,rec);
            list.setNumReturned(list.size());
        }
        location++;
        return location;
    }

    private static int addItemFromCursor(libraryBrowseResult list,int start,int location, int count, String table, Cursor cursor)
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


}   // class LocalLibrary
