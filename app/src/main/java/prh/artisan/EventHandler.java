package prh.artisan;


public interface EventHandler
    // handles events from a renderer
    // to update the Now Playing and other UI
{
    // general message dispatched to all listeners

    public static String EVENT_VOLUME_CHANGED          = "VOLUME_CHANGED";                // data = Volume object
    public static String EVENT_STATE_CHANGED           = "STATE_CHANGED";                 // data = String renderer_state
    public static String EVENT_POSITION_CHANGED        = "POSITION_CHANGED";              // data = Integer renderer position
    public static String EVENT_TRACK_CHANGED           = "TRACK_CHANGED";                 // data = Track
    public static String EVENT_PLAYLIST_CHANGED        = "PLAYLIST_CHANGED";              // data = Playlist
    public static String EVENT_LIBRARY_CHANGED         = "LIBRARY_CHANGED";               // data = New Library
    public static String EVENT_RENDERER_CHANGED        = "RENDERER_CHANGED";              // data = New Renderer
    public static String EVENT_PLAYLIST_SOURCE_CHANGED = "PLAYLIST_SOURCE_CHANGED";       // data = New PlaylistSource
    public static String EVENT_NEW_DEVICE              = "NEW_DEVICE";                    // data = New Device found in SSDP Search
    public static String EVENT_DEVICE_STATUS_CHANGED   = "DEVICE_STATUS_CHANGED";         // data = Device
    public static String EVENT_SSDP_SEARCH_STARTED     = "SSDP_SEARCH_STARTED";           // data = null
    public static String EVENT_SSDP_SEARCH_FINISHED    = "SSDP_SEARCH_FINISHED";          // data = null

    //-----------------------------
    // ARTISAN ONLY EVENTS
    //-----------------------------
    // Interpreted in Artisan

    public static String EVENT_IDLE = "IDLE";                          // data = null
        // called from LocalRenderer refresh loop each time
        // triggers sending of openHome events

    public static String EVENT_PLAYLIST_CONTENT_CHANGED = "PLAYLIST_CONTENTS_CHANGED";     // data = Playlist
        // from LocalPlaylist if playlist is changed incrementally locally
        // intercepted by Artisan to call incUseCount() to event subscribers
        // and/or update the UI
    public static String EVENT_PLAYLIST_TRACKS_EXPOSED = "PLAYLIST_TRACKS_EXPOSED";       // data = Playlist
        // Not otherwise received as events


    // control commands

    public static String COMMAND_EVENT_PLAY_TRACK      = "COMMAND_PLAY_TRACK";          // data = null


    //--------------------------
    // method signature
    //--------------------------

    void handleArtisanEvent( String event_id, Object data );

}   // class EventHandler

