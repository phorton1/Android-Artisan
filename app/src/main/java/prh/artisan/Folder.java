package prh.artisan;


import android.database.Cursor;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import prh.device.LocalLibrary;
import prh.types.libraryBrowseResult;
import prh.types.recordList;
import prh.utils.Utils;
import prh.utils.httpUtils;

public class Folder extends Record implements Fetcher.FetcherSource
    // Although these can represent a database record,
    // at the current time, there is no API for writing them,
    // as the local database is considered read-only at this time.
{
    private static int dbg_folder = 0;

    // Construction

    public Folder()
    {
    }

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

    public Folder(Node didl_node)
    {
        Element node_ele = (Element) didl_node;

        setTitle(Utils.getTagValue(node_ele,"dc:title"));
        setId(Utils.getNodeAttr(didl_node,"id"));
        setParentId(Utils.getNodeAttr(didl_node,"parentID"));
        setArtUri(Utils.getTagValue(node_ele,"upnp:albumArtURI"));    // special knowledge
        setGenre(Utils.getTagValue(node_ele,"upnp:genre"));
        setYearString(Utils.getTagValue(node_ele,"dc:date"));

        String artist = Utils.getTagValue(node_ele,"upnp:artist");
        if (artist.isEmpty())
            artist = Utils.getTagValue(node_ele,"upnp:albumArtist");
        setArtist(artist);
    }



    //------------------------------------------
    // accessors
    //------------------------------------------
    // in memory only

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
    public void setPath               (String  value){ putString("path",value); }
        // if is_local, art is folder.jpg at path/folder.jpg,
        // and art_uri is blank
    public void setArtUri             (String  value){ putString("art_uri",value); }
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


    //------------------------------------------------------------
    // Implementation Accessors
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
        // return the metadata, wrapped in Didl tags, and xml_encoded
    {
        return httpUtils.encode_xml(
            httpUtils.start_didl() +
            getMetadata() +
            httpUtils.end_didl());
    }


    public String getMetadata()
        // returns the metadata without <didl> wrapper or xml_encoding
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



    //--------------------------------------------------------
    // FetcherSource Interface
    //--------------------------------------------------------
    // Every Folder is a Fetcher Source for aLibrary.
    //
    // The base class works against the LocalLibrary.
    // Overridden by MediaServer.FolderPlus.
    //
    // Base class does not have assoicated fetchers

    public Fetcher getFetcher() { return null; }


    public Fetcher.fetchResult getFetchRecords(Fetcher fetcher, boolean initial_call, int num)
        // A Folder form the LocalLibrary does not have a persistent
        // fetcher, and all the records are gotten at once (num is ignored
        // and 9999999 is passed to getSubItems()
        //
        // They are created as the library is traversed in aLibrary.
        // Folders from remote sources (i.e. device.MediaServer) associate
        // a fetcher with the folder persistently, as long as the Library
        // is selected.
        //
        // It should never be the case that a persistent_fetcher is not passed
        // back in .. if they are different, it constitutes an error.
    {
        if (this.getFetcher() != null && this.getFetcher() != fetcher)
        {
            Utils.error("TWO DIFFERENT FETCHERS ON THE SAME FOLDER: " + this.getFetcher().getTitle() + " new=" + fetcher.getTitle());
            return Fetcher.fetchResult.FETCH_ERROR;
        }

        recordList records = fetcher.getRecordsRef();
        if (records.size() >= getNumElements())
            return Fetcher.fetchResult.FETCH_DONE;
        if (records.size() > 0)
        {
            Utils.error("Unexpected call with a partially populated LocalFolder fetcher");
            return Fetcher.fetchResult.FETCH_ERROR;
        }

        LocalLibrary local_library = fetcher.getArtisan().getLocalLibrary();
        if (local_library == null)
        {
            Utils.error("Attempt to fetch from a LocalFolder with local_library==NULL");
            return Fetcher.fetchResult.FETCH_ERROR;
        }

        // Overwrite local variable "records" with a new list
        // and set it into the fetcher.

        libraryBrowseResult result = local_library.getSubItems(this.getId(),0,999999,false);
        fetcher.setRecords(result);
        return Fetcher.fetchResult.FETCH_DONE;
    }


}   // class Folder
