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
    public static String EVENT_LIBRARY_CHANGED         = "LIBRARY_CHANGED";            // data = New Library
    public static String EVENT_RENDERER_CHANGED        = "RENDERER_CHANGED";           // data = New Renderer
    public static String EVENT_NEW_DEVICE              = "NEW_DEVICE";                 // data = New Device found in SSDP Search

    void handleArtisanEvent(
        String event_id,
        Object data
    );
}
