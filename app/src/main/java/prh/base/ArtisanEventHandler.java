package prh.base;


public interface ArtisanEventHandler
    // handles events from a renderer
    // to update the Now Playing and other UI
{
    // general message dispatched to all listeners

    public static String EVENT_VOLUME_CHANGED          = "VOLUME_CHANGED";                // data = Volume object
    public static String EVENT_STATE_CHANGED           = "STATE_CHANGED";                 // data = String renderer_state
    public static String EVENT_POSITION_CHANGED        = "POSITION_CHANGED";              // data = Integer renderer position
    public static String EVENT_TRACK_CHANGED           = "TRACK_CHANGED";                 // data = Track
    public static String EVENT_PLAYLIST_CHANGED        = "PLAYLIST_CHANGED";              // data = Playlist
    public static String EVENT_PLAYLIST_CONTENT_CHANGED = "PLAYLIST_CONTENTS_CHANGED";    // data = Playlist
    public static String EVENT_LIBRARY_CHANGED         = "LIBRARY_CHANGED";               // data = New Library
    public static String EVENT_RENDERER_CHANGED        = "RENDERER_CHANGED";              // data = New Renderer
    public static String EVENT_PLAYLIST_SOURCE_CHANGED = "PLAYLIST_SOURCE_CHANGED";       // data = New PlaylistSource
    public static String EVENT_NEW_DEVICE              = "NEW_DEVICE";                    // data = New Device found in SSDP Search
    public static String EVENT_DEVICE_STATUS_CHANGED   = "DEVICE_STATUS_CHANGED";         // data = Device
    public static String EVENT_SSDP_SEARCH_STARTED     = "SSDP_SEARCH_STARTED";           // data = null
    public static String EVENT_SSDP_SEARCH_FINISHED    = "SSDP_SEARCH_FINISHED";          // data = null
    public static String EVENT_VIRTUAL_FOLDER_CHANGED  = "VIRTUAL_FOLDER_CHANGED";        // data = Virtual Folder Id


    //-----------------------------
    // ARTISAN ONLY EVENTS
    //-----------------------------
    // Not otherwise received as events
    // Interpreted in Artisan

    public static String DISPATCH_UPNP = "DISPATCH_UPNP";
        // data = null
        // called by artisan itself when UPNP_Events have been deferred
    public static String EVENT_PLAYLIST_TRACKS_EXPOSED = "PLAYLIST_TRACKS_EXPOSED";
        // data = Playlist
        // used in expose scheme

    // control commands

    public static String COMMAND_EVENT_PLAY_TRACK = "COMMAND_PLAY_TRACK";
        // data = track
        // play the given track in immediate mode



    //--------------------------
    // method signature
    //--------------------------

    void handleArtisanEvent( String event_id, Object data );

}   // class ArtisanEventHandler

