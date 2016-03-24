package prh.artisan;


public interface EventHandler
    // handles events from a renderer
    // to update the Now Playing and other UI
{

    public static String EVENT_IDLE                    = "IDLE";                       // data = null, called from LocalRenderer refresh loop each time
    public static String EVENT_VOLUME_CHANGED          = "VOLUME_CHANGED";             // data = Volume object
    public static String EVENT_STATE_CHANGED           = "STATE_CHANGED";              // data = String renderer_state
    public static String EVENT_POSITION_CHANGED        = "POSITION_CHANGED";           // data = Integer renderer position
    public static String EVENT_TRACK_CHANGED           = "TRACK_CHANGED";              // data = Track
    public static String EVENT_PLAYLIST_CHANGED        = "PLAYLIST_CHANGED";           // data = Playlist
    public static String EVENT_PLAYLIST_TRACKS_EXPOSED = "PLAYLIST_TRACKS_EXPOSED";    // data = Playlist
    public static String EVENT_LIBRARY_CHANGED         = "LIBRARY_CHANGED";            // data = New Library
    public static String EVENT_RENDERER_CHANGED        = "RENDERER_CHANGED";           // data = New Renderer
    public static String EVENT_PLAYLIST_SOURCE_CHANGED = "PLAYLIST_SOURCE_CHANGED";    // data = New PlaylistSource
    public static String EVENT_NEW_DEVICE              = "NEW_DEVICE";                 // data = New Device found in SSDP Search
    public static String EVENT_SSDP_SEARCH_STARTED     = "SSDP_SEARCH_STARTED";        // data = null
    public static String EVENT_SSDP_SEARCH_FINISHED    = "SSDP_SEARCH_FINISHED";       // data = null
    public static String EVENT_ADDL_FOLDERS_AVAILABLE  = "ADDITONAL_FOLDERS_AVAILABLE";// data = MediaRenderer.FolderPlus

    // control commands

    public static String COMMAND_EVENT_PLAY_TRACK      = "COMMAND_PLAY_TRACK";          // data = null


    void handleArtisanEvent(
        String event_id,
        Object data
    );
}
