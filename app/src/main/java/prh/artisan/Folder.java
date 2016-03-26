package prh.artisan;

// prh - should have a "folder" image when the thing is not an album
// or deliver no uri ...


import android.database.Cursor;

import java.util.HashMap;

import prh.types.objectHash;
import prh.utils.httpUtils;
import prh.utils.Utils;

public class Folder extends Record
    // Although these can represent a database record,
    // at the current time, there is no API for writing them,
    // as the local database is considered read-only at this time.

{
    // Construction

    public Folder()  {};

    public Folder(Cursor cursor)
        // construct from a cursor pointing at a database record
    {
        super(cursor);
    }


    public Folder(Folder other)
        // Copy Constructor
    {
        this.clear();
        this.putAll(other);
    }


    public Folder(String uri, String metadata)
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
    public int getDuration()        { return getInt("duration"); }

    // My raw database accessors

    public int getFolderError()        { return getInt("folder_error"); }
    public int getHighestFolderError() { return getInt("highest_folder_error"); }
    public int getHighestTrackError()  { return getInt("highest_track_error"); }


    // setters

    public void setIsLocal            (boolean value){ putInt("is_local",value? 1 : 0); }
    public void setHasArt             (boolean value){ putInt("has_art",value ? 1 : 0); }
        // if is_local, art is folder.jpg at path/folder.jpg,
        // and this boolean indicates it's presence.
        // otherwise, the path IS the art_uri
    public void setPath               (String  value){ putString("path",value); }
        // this is the art_uri for external folders
        // if is_local, it is the mp3's relative path for the folder

    public void setId                 (String  value){ putString("id",value); }
    public void setParentId           (String  value){ putString("parent_id",value); }
    public void setType               (String  value){ putString("dirtype",value); }
        // root
        // folder
        // album
    public void setNumElements        (int     value){ putInt("num_elements",value); }
    public void incNumElements        ()             { putInt("num_elements",getNumElements() + 1); }

    public void setTitle              (String  value){ putString("title",value); }
    public void setArtist             (String  value){ putString("artist",value); }
    public void setGenre              (String  value){ putString("genre",value); }
    public void setYearString         (String  value){ putString("year_str",value); }
    public void setDuration           (int     value){ putInt("duration",value); }
    public void addDuration           (int     value){ putInt("duration",getDuration() + value); }

    public void setFolderError        (int     value){ putInt("folder_error",value); }
    public void setHighestFolderError (int     value){ putInt("highest_folder_error",value); }
    public void setHighestTrackError  (int     value){ putInt("highest_track_error",value); }


    // protect Record


    //------------------------------------------------------------
    // Implementation Independent Accessors
    //------------------------------------------------------------

    public String getLocalArtUri()
    {
        String rslt = "";
        if (isLocal() && hasArt())
            rslt = "file://" + Prefs.mp3s_dir() + "/" + getPath() + "/folder.jpg";
        else   // return the actual member
            rslt = privateGetPublicArtUri();
        return rslt;
    }

    public String getPublicArtUri()
    {
        String rslt = "";
        if (isLocal() && hasArt())
            rslt = Utils.server_uri + "/ContentDirectory/" + getId() + "/folder.jpg";
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
        String rslt = getMetadata();
        if (httpUtils.ENCODE_DIDL)
            rslt = httpUtils.encode_xml(rslt);

        return
            httpUtils.start_didl() +
            rslt +
            httpUtils.end_didl();
    }

    public String getMetadata()
    // returns the metadata chunk without <didl> wrapper
    {
        String container = getType().equals("album") ?
            "object.container.album.musicAlbum" :
            "object.container";

        return "<container " +
            "id=\"" + getId() + "\" " +
            "parentID=\"" + getParentId() + "\" " +
            "searchable=\"1\" " +
            "restricted=\"1\" " +
            "childCount=\"" + getNumElements() + "\">" +
            "<dc:title>" +  httpUtils.encode_value(getTitle()) + "</dc:title>" +
            "<upnp:class>" + container + "</upnp:class>" +
            "<upnp:artist>" +  httpUtils.encode_value(getArtist()) + "</upnp:artist>" +
            "<upnp:albumArtist>" +  httpUtils.encode_value(getArtist()) + "</upnp:albumArtist>" +
            "<upnp:genre>" +  httpUtils.encode_value(getGenre()) + "</upnp:genre>" +
            "<dc:date>" +  httpUtils.encode_value(getYearString()) + "</dc:date>" +
            "<upnp:albumArtURI>" +  httpUtils.encode_value(getPublicArtUri()) + "</upnp:albumArtURI>" +
            "</container>";
    }


}   // class Folder
