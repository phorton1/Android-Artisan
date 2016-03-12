package prh.artisan;


import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import prh.utils.DlnaUtils;
import prh.utils.Utils;


public class Track extends Record
    // The do-it-all Track class
    // Can be constructed from a (local) id, a Cursor, or a URI and metadata
    // Can deliver a publicUri and didl representation of it's metadata for DLNA clients
    // Can deliver a localUri that may be different than the publicUri if its isLocal
    // Can be assigned a position and/or an openID in a playlist
    // Can write (insert or update) itself into a Playlist
{
    private static int dbg_cvt = 0;
    private static int dbg_track = 0;


    // constants for bitwise track's has_art

    public static final int HAS_FOLDER_ART = 1;
    public static final int HAS_TRACK_ART = 2;

    // options

    public static boolean CONVERT_TO_LOCAL = true;
        // if the url matches us, convert the thing to a local path


   // Construction

    protected Track()  {};
        // protected default constructor

    public Track(Cursor cursor)
        // construct from a cursor pointing at a database record
    {
        super(cursor);
    }


    public Track(HashMap<String,Object> hash)
        // construct from a a hash of the correct
        // fields and types .. all blanks, integers
        // etc, must be provided.
    {
        this.put("dirty",1);
        for (String key:hash.keySet())
            this.put(key,hash.get(key));
    }


    public Track(String uri, String didl)
        // construct from a URI and some didl encoded METADATA from a dlna client
    {
        boolean isLocal = false;
        this.put("dirty",1);
        didl = DlnaUtils.decode_xml(didl);
        String id = extract_value("\\sid=\"(.+?)\"",didl);

        // convert to local
        // not if there's no local database

        if (CONVERT_TO_LOCAL)
        {
            Library local_library = LocalLibrary.getLocalLibrary();
            if (local_library != null)
            {
                String got_id = "";
                String re = "http:\\/\\/" + Utils.server_ip + ":" + Utils.server_port + "\\/ContentDirectory\\/media\\/(.*)\\.mp3$";
                Pattern pattern = Pattern.compile(re);
                Matcher matcher = pattern.matcher(uri);
                if (matcher.find()) got_id = matcher.group(1);
                if (got_id == null) got_id = "";
                if (!got_id.equals(""))
                {
                    // got an url that looks like ours
                    // so we assume it is
                    // give a warning if the didl had a different id

                    Utils.log(dbg_cvt,0,"Converting(" + got_id + ") to isLocal");
                    if (!got_id.equals(id))
                        Utils.warning(dbg_cvt,1,"... does not match didl ID=" + id);

                    isLocal = true;

                    // re-read meta data

                    Track track = local_library.getTrack(got_id);
                    if (track == null)
                        isLocal = false;
                    else
                    {
                        for (String key:track.keySet())
                            this.put(key,track.get(key));
                        this.put("dirty",0);
                        return;

                    }   // got the track
                }   // got an id from the uri
            }   // local database exists
        }   // CONVERT_TO_LOCAL


        // from didl, local or not

        String type = extractType(didl);
        if (!isLocal) this.put("path",uri);
        this.put("is_local",isLocal?1:0);
        this.put("id",id);
        this.put("parent_id", extract_value("parentID=\"(.*?)\"",didl));
        // has_art
        // path
        this.put("art_uri",extract_value("<upnp:albumArtURI>(.*?)<\\/upnp:albumArtURI>",didl));
        String dur_str = Utils.extract_re("duration=\"(.*?)\"",didl);;
        this.put("duration",Utils.stringToDuration(dur_str));
        this.put("size",Utils.parseInt(Utils.extract_re("size =\"(\\d+)\"",didl)));
        this.put("type",type);
        this.put("title",extract_value("<dc:title>(.*?)<\\/dc:title>",didl));
        this.put("artist",extract_value("<upnp:artist>(.*?)<\\/upnp:artist>",didl));
        this.put("album_title",extract_value("<upnp:album>(.*?)<\\/upnp:album>",didl));
        this.put("album_artist",extract_value("<upnp:albumArtist>(.*?)<\\/upnp:albumArtist>",didl));
        this.put("tracknum",extract_value("<upnp:originalTrackNumber>(.*?)<\\/upnp:originalTrackNumber>",didl));
        this.put("genre",extract_value("<upnp:genre>(.*?)<\\/upnp:genre",didl));
        this.put("year_str",Utils.extract_re("<dc:date>(.*?)<\\/dc:date>",didl));

    }   // Track() from uri and didl



    private static String extract_value(String re, String didl)
    {
        String rslt = Utils.extract_re(re,didl);
        rslt = DlnaUtils.decode_value(rslt);
        return rslt;
    }


    private static String extractType(String didl)
    {
        String type = "mp3";
        if (!Utils.extract_re("(audio/x-ms-wma)",didl).equals("")) type = "wma";
        if (!Utils.extract_re("(audio/x-wav)",didl).equals("")) type = "wav";
        if (!Utils.extract_re("(audio/x-m4a)",didl).equals("")) type = "m4a";
        return type;
    }

    private static String typeFromMimeType(String mime_type)
    {
        String type = "mp3";
        if (mime_type.equals("(audio/x-ms-wma")) type = "wma";
        if (mime_type.equals("(audio/x-wav)")) type = "wav";
        if (mime_type.equals("(audio/x-m4a)")) type = "m4a";
        return type;
    }



    //----------------------------------------------------------
    // Implementation
    //----------------------------------------------------------
    // base fields needed for minimum dlna representation

    public boolean isLocal()                { return getInt("is_local") > 0; }
    private String getPath()                { return getString("path"); }
    public int getHasArt()                  { return getInt("has_art"); }
    private String privateGetPublicArtUri() { return getString("art_uri"); }

    // dlna representation

    public String getId()           { return getString("id"); }
    public String getParentId()     { return getString("parent_id"); }
    public int getDuration()        { return getInt("duration"); }
    public String getType()         { return getString("type"); }
    public int getSize()            { return getInt("size"); }
    public String getTitle()        { return getString("title"); }
    public String getArtist()       { return getString("artist"); }
    public String getAlbumTitle()   { return getString("album_title"); }
    public String getAlbumArtist()  { return getString("album_artist"); }
    public String getTrackNum()     { return getString("tracknum"); }
    public String getGenre()        { return getString("genre"); }
    public String getYearString()   { return getString("year_str"); }

    // extra main database fields

    public int getTimeStamp()       { return getInt("timestamp"); }
    public String getFileMd5()      { return getString("file_md5"); }
    public String getErrorCodes()   { return getString("error_codes"); }
    public int getHighestError()    { return getInt("highest_error"); }
    public int getPosition()        { return getInt("position"); }

    // overload the highest error field to the open_id
    // and support for creating new playlist items

    public int getOpenId()          { return getInt("highest_error"); }
    public void putPosition(Integer value)  { putInt("position",value); }
    public void putOpenId(Integer value)    { putInt("highest_error",value); }


    //------------------------------------------------------------
    // Implementation Independent Accessors
    //------------------------------------------------------------
    // prh - I either have to disable mime-checking in bubbleUp
    // or return return MP3 extensions for everything or it barfs
    // on m4a's ...

    public String getPublicUri()
    {
        String rslt = "";
        if (isLocal())
            rslt = Utils.server_uri + "/ContentDirectory/media/" + getId() + "." + "mp3";
            // getType().toLowerCase();
        else
            rslt = getPath();
        return rslt;
    }

    public String getLocalUri()
    {
        String rslt = "";
        if (isLocal())
            rslt = "file://" + Prefs.mp3s_dir() + "/" + getPath();
        else
            rslt = getPath();
        return rslt;
    }

    public String getLocalArtUri()
    {
        String rslt = "";
        if (isLocal())
            if (hasFolderArt())
                rslt = "file://" + Prefs.mp3s_dir() + "/" + Utils.pathOf(getPath()) + "/folder.jpg";
            else
                rslt = "no_image.png";
        else   // return the actual member
            rslt = privateGetPublicArtUri();
        return rslt;
    }

    public String getPublicArtUri()
    {
        String rslt = "";
        if (isLocal())
            if (hasFolderArt())
                rslt = Utils.server_uri + "/ContentDirectory/" + getParentId() + "/folder.jpg";
            else
                rslt = Utils.server_uri + "/icons/no_image.png";
        else   // return the actual member
            rslt = privateGetPublicArtUri();
        return rslt;
    }


    public boolean hasFolderArt()
    {
        return (getHasArt() & HAS_FOLDER_ART) > 0 ? true : false;
    }

    public int getYear()
    {
        return Utils.parseInt(getYearString());
    }

    public String getDurationString(boolean precise)
    {
        return Utils.durationToString(getDuration(),precise);
    }

    public String getMimeType()
    {
        String type = getType();
        String mime_type = "";
        if (type.equalsIgnoreCase("mp3")) mime_type = "audio/mpeg";
        if (type.equalsIgnoreCase("wma")) mime_type = "audio/x-ms-wma";
        if (type.equalsIgnoreCase("wav")) mime_type = "audio/x-wav)";
        if (type.equalsIgnoreCase("m4a")) mime_type = "audio/x-m4a)";
        return mime_type;
    }


    public String getDidl()
    // return the remote client didl representation
    // of this item (which always uses public artURI)
    {
        return
            DlnaUtils.start_didl() +
            DlnaUtils.encode_xml(getMetadata()) +
            DlnaUtils.end_didl();
    }

    public String getMetadata()
    // returns the metadata chunk without <didl> wrapper
    {
        return "<item id=\"" + getId() + "\" parentID=\"" + getParentId() + "\" restricted=\"1\">" +
            "<dc:title>" +  DlnaUtils.encode_value(getTitle()) + "</dc:title>" +
            "<upnp:class>object.item.audioItem</upnp:class>" +
            "<upnp:genre>" +  DlnaUtils.encode_value(getGenre()) + "</upnp:genre>" +
            "<upnp:artist>" +  DlnaUtils.encode_value(getArtist()) + "</upnp:artist>" +
            "<upnp:album>" +  DlnaUtils.encode_value(getAlbumTitle()) + "</upnp:album>" +
            "<upnp:originalTrackNumber>" +  DlnaUtils.encode_value(getTrackNum()) + "</upnp:originalTrackNumber>" +
            "<dc:date>" +  DlnaUtils.encode_value(getYearString()) + "</dc:date>" +
            "<upnp:albumArtURI>" +  DlnaUtils.encode_value(getPublicArtUri()) + "</upnp:albumArtURI>" +
            "<upnp:albumArtist>" +  DlnaUtils.encode_value(getAlbumArtist()) + "</upnp:albumArtist>" +
            "<res " +
            "size=\"" + getSize() + "\" " +
            "duration=\"" + getDurationString(true) + "\" " +
            "protocolInfo=\"http-get:*:" + getMimeType() + ":" +
            DlnaUtils.get_dlna_stuff(getType()) + "\">" +
            getPublicUri() +
            "</res>" +
            "</item>";
    }


    //----------------------------------------------------------
    // openHomeSupport
    //----------------------------------------------------------

    public void setExposed(boolean b)
    {
        // Utils.log(0,5,"setExposed(" + b + ") " + getTitle());
        putInt("exposed",b ? 1 : 0);
    }

    public boolean getExposed()
    {
        boolean b = getInt("exposed") > 0;
        // Utils.log(0,5,"getExposed(" + b + ") " + getTitle());
        return b;
    }


    public boolean insert(SQLiteDatabase track_db)
    {
        return true;
    }


}   // class Track
