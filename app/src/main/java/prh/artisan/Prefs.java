package prh.artisan;

import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import prh.utils.Utils;


// Possibles
//
// SaveChangedPlaylistsOnRemoteNew = false
//       Save changed, named playlists when any remote action,
//       like a remote OpenPlaylist "Clear" (DeleteAll),
//       does a "New" with a dirty changed, named playlist
//       or more generically, AutoSaveChangedPlaylists
//
// ExternalAVTransportUseEvents = false
// ExternalRenderingControlUseEvents = false
// ExternalOpenHomeRendererUseEvents = true
// ExternalContentDirectoryUseEvent = false;

// UseHardwareVolumeButtons = true
//      Should default to true for LocalRenderer
//      Need to use Audio Focus in case a call comes in
// (UseHardwareTransportControlButtons)
// PauseResumeOnPhoneCall
//      Otherwise Mutes
// CacheRemotePlaylists
// CacheRemoteLibraries
// CacheImages
// ImageCacheSize

public class Prefs
{
    public static int USE_RENDERER_TRACKS_NEVER = 0;
    public static int USE_RENDERER_TRACKS_WHEN_AVAILABLE = 1;
    public static int USE_RENDERER_TRACKS_FIRST = 2;
        // default = WHEN_AVAILABLE
        //
        //  A Renderer may have a number of tracks, and a current track index
        //  that is separate from any playlist ... though the LocalRenderer
        //  always presents the current_playlist
        //
        //  aRenderer should enable the buttons based on whether the Renderer
        //  says there are tracks, and should show the Renderers version of the
        //  track_number and number of tracks.
        //
        //  Renderer.incAndPlay() normally uses the current_playlist to get
        //  the number of tracks and current track index.  In fact that's all
        //  the LocalRenderer CAN do.
        //
        //  But a remote renderer may decide that if the current playlist is
        //  empty, and it has been given a number of tracks, and an index
        //  to pass the Next/Previous action to the remote renderer.
        //  This is the default WHEN_AVAILALBE.
        //
        //  It can be disabled by setting it to NEVER
        //
        //  Or the Remote Renderers can be made to preferentially use
        //  the number of tracks on the remote by setting it to FIRST

    public enum id
    {
        SELECTED_RENDERER,
        SELECTED_LIBRARY,
        SELECTED_PLAYLIST_SOURCE,
        DEFAULT_RENDERER,
        DEFAULT_LIBRARY,
        DEFAULT_PLAYLIST_SOURCE,

        START_ON_BOOT,
        DEVICE_ROOM,
        DEVICE_NAME,
        MP3S_DIR,
        DATA_DIR,

        KEEP_WIFI_ALIVE,

        START_LOCAL_RENDERER,
        START_LOCAL_LIBRARY,

        START_HTTP_MEDIA_SERVER,
        START_HTTP_MEDIA_RENDERER,
        START_HTTP_OPEN_HOME_SERVER,
        START_HTTP_REMOTE_SERVER,

        START_AS_REMOTE,

        START_VOLUME_FIXER,

        PREFER_REMOTE_RENDERER_TRACKS
    };

    public static String LAST_SELECTED = "Last Selected";
        // special value for DEFAULT_RENDERER and LIBRARY
        // which means "use the last one selected"



    private static Artisan artisan = null;
    private static SharedPreferences prefs = null;

    public static void static_init(Artisan a)
    {
        artisan = a;
        prefs = null;
        if (artisan != null)
            prefs = PreferenceManager.getDefaultSharedPreferences(artisan);
    }

    // convenience accessors

    public static String mp3s_dir()
    {
        return getString(id.MP3S_DIR);
    }

    public static String friendlyName()
    {
        return Utils.programName + " (" + getString(id.DEVICE_NAME) + ")";
    }



    //--------------------------------------------
    // accessors
    //--------------------------------------------

    public static String getString(id id)
    {
        String value = defaultValue(id);
        if (prefs != null)
            value = prefs.getString(id.toString(),value);
        return value;
    }
    public static int getInt(id id)
    {
        int value = Utils.parseInt(defaultValue(id));
        if (prefs != null)
            value = prefs.getInt(id.toString(),value);
        return value;
    }
    public static boolean getBoolean(id id)
    {
        boolean value = Utils.parseInt(defaultValue(id))>0;
        if (prefs != null)
            value = prefs.getBoolean(id.toString(),value);
        return value;
    }

    public static void putString(id id,String value)
    {
        if (prefs != null)
        {
            SharedPreferences.Editor ed = prefs.edit();
            ed.putString(id.toString(),value);
            ed.commit();
        }
    }
    public static void putInteger(id id, int value)
    {
        if (prefs != null)
        {
            SharedPreferences.Editor ed = prefs.edit();
            ed.putInt(id.toString(),value);
            ed.commit();
        }
    }
    public static void putBoolean(id id, boolean value)
    {
        if (prefs != null)
        {
            SharedPreferences.Editor ed = prefs.edit();
            ed.putBoolean(id.toString(),value);
            ed.commit();
        }
    }


    //--------------------------------------------
    // default values
    //--------------------------------------------

    private static String defaultValue(id id)
    {
        // The Last_Selected library or renderer is set whenever artisan
        // setLibrary() or setRenderer() is called. aPlaylistSource has special
        // UI that presents lists of available items for the user to select.
        // The pref itself just keeps track of it for next boot in case
        // they use "Last Selected" as the Startup Default.

        if (id.equals(id.SELECTED_RENDERER))
            return "";  // LocalRenderer
        if (id.equals(id.SELECTED_LIBRARY))
            return "";  // LocalLibrary if available
        if (id.equals(id.SELECTED_PLAYLIST_SOURCE))
            return "";  // LocalPlaylistSource if available

        // The Startup_Default library and renderer.
        // These are used at program startup as the desired library
        // and renderer to start with.

        //   "" (blank) = LocalRenderer if LocalLibrary as available
        //   "Last Selected" = use the last selected library or renderer
        //   name = use the named library or renderer

        // Startup goes like this (renderer for example)
        //
        //    Get the default name
        //         default_name = pref.LastSelectedRenderer if default_name = "Last Selected"
        //
        //    If default_name = ""
        //         use the LocalRenderer
        //         done
        //
        //    If default_name != ""
        //          in the cache?
        //          use it from the cache and try to start it
        //               failure to start?  TELL THE USER
        //
        //    default_name != "" and not in the cache
        //        Device Manager (aPlaylistSource?) patiently waiting for SSDP to find the device
        //        *** NEED TO KNOW WHEN SSDP SEARCH HAS COMPLETED
        //        Still not found?  TELL THE USER
        //        Found!! Try to start it
        //             failure to start?  TELL THE USER


        // These will be used from the device cache on startup, or
        // if they're not in the cache, at the end of the SSDPSearch,
        // if they are found.
        //
        // User will get a message telling them that the default
        // renderer/library could not be started or found, as may
        // be the case.


        if (id.equals(id.DEFAULT_LIBRARY))
            return "Last Selected";
        if (id.equals(id.DEFAULT_RENDERER))
            return "Last Selected";
        if (id.equals(id.DEFAULT_PLAYLIST_SOURCE))
            return "Last Selected";


        if (id.equals(id.START_ON_BOOT))
            return "0";

        if (id.equals(id.DEVICE_ROOM))
            return "salon";

        if (id.equals(id.DEVICE_NAME))
        {
            String device_name = Build.DEVICE;
            if (device_name.equals("vbox86tp")) device_name = "emulator";
            if (device_name.equals("polaris-p1")) device_name = "tablet1";
            if (device_name.equals("rk30sdk")) device_name = "car stereo";
            if (device_name.equals("android_4x_wet_kk")) device_name = "phone";
            return device_name;
        }

        if (id.equals(id.MP3S_DIR))
        {
            String mp3s_dir = "";
            String device_name = getString(id.DEVICE_NAME);
            if (device_name.equals("car stereo")) mp3s_dir = "/mnt/usb_storage2/mp3s";
                // car stereo uses an actual mount point.
            if (device_name.equals("emulator")) mp3s_dir = "/mnt/shared/mp3s";
                // on emulator, /mnt/shared/mp3s is set to
                // c:\mp3_backups\mp3s_for_gennymotion in the
                // virutalBox "Shared Folders" configuration.
                // It is a copy of the actual Windows /mp3s directory.
            return mp3s_dir;
        }

        if (id.equals(id.DATA_DIR))
        {
            String data_dir = getString(id.MP3S_DIR) + "/_data";
            return data_dir;
        }

        if (id.equals(id.KEEP_WIFI_ALIVE))
            return "1";


        if (id.equals(id.START_LOCAL_RENDERER))
            return "1";
        if (id.equals(id.START_LOCAL_LIBRARY))
            return "1";

        if (id.equals(id.START_HTTP_MEDIA_SERVER))
            return "1";
        if (id.equals(id.START_HTTP_MEDIA_RENDERER))
            return "1";
        if (id.equals(id.START_HTTP_OPEN_HOME_SERVER))
            return "1";
        if (id.equals(id.START_HTTP_REMOTE_SERVER))
            return "1";
        if (id.equals(id.START_VOLUME_FIXER))
            return "1";

        // START_AS_REMOTE is tri-state
        // "" = default (start remote if no local library)
        // "0" = dont start it
        // "1" = do start it

        if (id.equals(id.START_AS_REMOTE))
            return "";

        // See notes about prefering renderer tracks

        if (id.equals(id.PREFER_REMOTE_RENDERER_TRACKS))
            return Integer.toString(USE_RENDERER_TRACKS_FIRST);
            // return Integer.toString(USE_RENDERER_TRACKS_WHEN_AVAILABLE);

        return "";
    }



}
