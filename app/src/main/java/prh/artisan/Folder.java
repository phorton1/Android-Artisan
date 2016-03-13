package prh.artisan;

// prh - should have a "folder" image when the thing is not an album
// or deliver no uri ...


import android.database.Cursor;

import java.util.HashMap;

import prh.utils.httpUtils;
import prh.utils.Utils;

public class Folder extends Record
    // Although these can represent a database record,
    // at the current time, there is no API for writing them,
    // as the local database is considered read-only at this time.

{
    // Construction

    protected Folder()  {};
        // protected default constructor

    public Folder(Cursor cursor)
        // construct from a cursor pointing at a database record
    {
        super(cursor);
    }


    /*********************************
        Don't like this ctor
        Since it cannot fail with null

        public Folder(String id)
            // construct from an id in the local database
        {
            SQLiteDatabase db = Database.getDB();
            Cursor cursor = null;
            String query = "SELECT * FROM folders WHERE id='" + id + "'";
            try
            {
                cursor = db.rawQuery(query,new String[]{});
            }
            catch (Exception e)
            {
                Utils.error("Could not execute query: " + query + " exception=" + e.toString());
            }
            if (cursor == null || !cursor.moveToFirst())
            {
                Utils.error("No cursor returned for track(" + id + ")");
            }
        }

    **************************/


    public Folder(HashMap<String,Object> hash)
        // construct from a a hash of the correct
        // fields and types .. all blanks, integers
        // etc, must be provided.
    {
        for (String key:hash.keySet())
        {
            this.put(key,hash.get(key));
        }
    }


    public Folder(Boolean dummy, String metadata)
        // construct from didl METADATA from a dlna client
        // the dummy boolean is used to give this a different
        // signature than Folder(id)
    {
        // TBD
    }


    //------------------------------------------
    // accessors
    //------------------------------------------
    // don't need to overload the folder_error to open_id
    // as these never show up in an openHome Renderer

    public boolean isLocal()                { return getInt("is_local") > 0; }
    private String getPath()                { return getString("path"); }
    public boolean hasArt()                 { return getInt("has_art") > 0; }
    private String privateGetPublicArtUri() { return getString("art_uri"); }

    // dlna representation

    public String getId()           { return getString("id"); }
    public String getParentId()     { return getString("parent_id"); }
    public String getType()         { return getString("dirtype"); }
    public int getNumElements()     { return getInt("num_elements"); }
    public String getTitle()        { return getString("title"); }
    public String getArtist()       { return getString("artist"); }
    public String getGenre()        { return getString("genre"); }
    public String getYearString()   { return getString("year_str"); }

    // local only raw database accessors

    public int getFolderError()        { return getInt("folder_error"); }
    public int getHighestFolderError() { return getInt("highest_folder_error"); }
    public int getHighestTrackError()  { return getInt("highest_track_error"); }


    //------------------------------------------------------------
    // Implementation Independent Accessors
    //------------------------------------------------------------

    public String getLocalArtUri()
    {
        String rslt = "";
        if (!getType().equals("album")) return "";
            // let the client show what it wants for folders

        if (isLocal())
            if (hasArt())
                rslt = "file://" + Prefs.mp3s_dir() + "/" + getPath() + "/folder.jpg";
            else
                rslt = "no_image.png";
        else   // return the actual member
            rslt = privateGetPublicArtUri();
        return rslt;
    }

    public String getPublicArtUri()
    {
        String rslt = "";
        if (!getType().equals("album")) return "";
            // let the client show what it wants for folders

        if (isLocal())
            if (hasArt())
                rslt = Utils.server_uri + "/ContentDirectory/" + getId() + "/folder.jpg";
            else
                rslt = Utils.server_uri + "/icons/no_image.png";
        else   // return the actual member
            rslt = privateGetPublicArtUri();
        return rslt;
    }


    public int getYear()
    {
        return Utils.parseInt(getYearString());
    }

    public String getDidl()
    // return the remote client didl representation
    // of this item (which always uses public artURI)
    {
        return
            httpUtils.start_didl() +
                httpUtils.encode_xml(getMetadata()) +
                httpUtils.end_didl();
    }

    public String getMetadata()
    // returns the metadata chunk without <didl> wrapper
    {
        return "<container " +
            "id=\"" + getId() + "\" " +
            "parentID=\"" + getParentId() + "\" " +
            "searchable=\"1\" " +
            "restricted=\"1\" " +
            "childCount=\"" + getNumElements() + "\">" +
            "<dc:title>" +  httpUtils.encode_value(getTitle()) + "</dc:title>" +
            "<upnp:class>object.container</upnp:class>" +
            "<upnp:artist>" +  httpUtils.encode_value(getArtist()) + "</upnp:artist>" +
            "<upnp:albumArtist>" +  httpUtils.encode_value(getArtist()) + "</upnp:albumArtist>" +
            "<upnp:genre>" +  httpUtils.encode_value(getGenre()) + "</upnp:genre>" +
            "<dc:date>" +  httpUtils.encode_value(getYearString()) + "</dc:date>" +
            "<upnp:albumArtURI>" +  httpUtils.encode_value(getPublicArtUri()) + "</upnp:albumArtURI>" +
            "</container>";
    }


}   // class Folder
