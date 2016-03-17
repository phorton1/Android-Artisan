package prh.artisan;

import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import prh.utils.Utils;


public class Prefs
{
    public enum id
    {
        DEFAULT_RENDERER,

        START_ON_BOOT,
        DEVICE_ROOM,
        DEVICE_NAME,
        MP3S_DIR,
        DATA_DIR,

        KEEP_WIFI_ALIVE,

        DEFAULT_LIBRARY,

        START_LOCAL_RENDERER,
        START_LOCAL_LIBRARY,

        START_HTTP_MEDIA_SERVER,
        START_HTTP_MEDIA_RENDERER,
        START_HTTP_OPEN_HOME_SERVER,
        START_HTTP_REMOTE_SERVER,

        START_AS_REMOTE,

        START_VOLUME_FIXER
    };


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

        // blank == local

        if (id.equals(id.DEFAULT_LIBRARY))
            return "";
        if (id.equals(id.DEFAULT_RENDERER))
            return "";

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


        return "";
    }



}
