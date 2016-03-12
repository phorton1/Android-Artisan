package prh.artisan;


import android.content.ContentResolver;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;

import prh.server.LocalVolumeFixer;
import prh.utils.Utils;


public class LocalVolume extends Volume
    // Sends an VOLUME_CHANGED event on an explicit volume change
    // and has a monitor loop to check for changes outside of Artisan
    // which can also send the VOLUME_CHANGED event.
    //
    // Assumes parent will event the VOLUME_CONFIG_CHANGED messages
    // when we go in, or out, of scope.

{
    private static int dbg_vol = 0;

    private static int REFRESH_TIME = 300;


    private Artisan artisan;
    private AudioManager am;
    private ContentResolver cr;
    private Handler refresh_handler;
    private VolumeMonitor volume_monitor;
    private int last_values[] = {0,0,0,0,0,0,0,0};


    public LocalVolume(Artisan a)
    {
        artisan = a;
    }

    public void stop()
    {
        Utils.log(0,0,"LocalVolume.stop()");
        if (refresh_handler != null)
            refresh_handler.removeCallbacks(volume_monitor);
        refresh_handler = null;
        volume_monitor = null;
        am = null;
        cr = null;
    }

    public void start()
        // The Volume Object needs access to the AudioManager
        // for most actions, and needs a "ContentResolver" to
        // get/set the Car Stereo Loudness, Balance, and EQ
        // via Settings.System.getString().
    {
        Utils.log(0,0,"LocalVolume.start()");
        am = (AudioManager) artisan.getSystemService(artisan.AUDIO_SERVICE);
        cr = artisan.getContentResolver();

        // initialize car stereo maximum values

        if (Utils.ID_CAR_STEREO.equals(Build.ID))
            max_values = new int[]{15, 1, 1, 28, 28, 20, 20, 20};
        else
            max_values = new int[]{15, 0, 0, 0, 0, 0, 0, 0};

        // start the refresh loop

        refresh_handler = new Handler();
        volume_monitor = new VolumeMonitor(this);
        refresh_handler.postDelayed(volume_monitor,REFRESH_TIME);
    }


    //---------------------------------
    // GET
    //---------------------------------

    public int[] getValues()
        // They are gotten as a group
        // Always returns a fresh new copy of the values.
        // We detect if am has "gone away" and just return 0s in that case
    {
        Utils.log(dbg_vol+2,0,"getValues()");
        int values[] = new int[]{0,0,0,0,0,0,0,0};
        if (am != null)
        {
            // volume

            values[CTRL_VOL] = valid(CTRL_VOL,am.getStreamVolume(AudioManager.STREAM_MUSIC));
            Utils.log(dbg_vol + 1,1,"vol=" + values[CTRL_VOL]);

            // car stereo gets/sets other value

            if (Utils.ID_CAR_STEREO.equals(Build.ID))
            {
                String mute_str = am.getParameters("av_mute=");
                values[CTRL_MUTE] = mute_str.equals("true") ? 1 : 0;   // inherently valid
                Utils.log(dbg_vol + 2,1,"mute=" + values[CTRL_MUTE]);

                try
                {
                    values[CTRL_LOUD] = valid(CTRL_LOUD,Settings.System.getInt(cr,"av_lud"));
                }
                catch (Exception e)
                {
                    Utils.error("getting av_lud:" + e);
                }
                Utils.log(dbg_vol + 2,1,"loud=" + values[CTRL_LOUD]);

                String balance_str = Settings.System.getString(cr,"KeyBalance");
                Utils.log(dbg_vol + 1,1,"balance_str=" + balance_str);
                if (balance_str != null)
                {
                    String parts[] = balance_str.split(",");
                    values[CTRL_BAL] = valid(CTRL_BAL,Utils.parseInt(parts[0]));
                    values[CTRL_FADE] = valid(CTRL_FADE,Utils.parseInt(parts[1]));
                    Utils.log(dbg_vol + 2,1,"bal=" + values[CTRL_BAL]);
                    Utils.log(dbg_vol + 2,1,"fade=" + values[CTRL_FADE]);
                }

                String eq_str = Settings.System.getString(cr,"KeyCustomEQ");
                Utils.log(dbg_vol + 1,1,"eq_str=" + eq_str);
                if (eq_str != null)
                {
                    String parts[] = eq_str.split(",");
                    values[CTRL_BASS] = valid(CTRL_BASS,Utils.parseInt(parts[0]));
                    values[CTRL_MID] = valid(CTRL_MID,Utils.parseInt(parts[1]));
                    values[CTRL_HIGH] = valid(CTRL_HIGH,Utils.parseInt(parts[2]));
                    Utils.log(dbg_vol + 2,1,"bass=" + values[CTRL_BASS]);
                    Utils.log(dbg_vol + 2,1,"mid=" + values[CTRL_MID]);
                    Utils.log(dbg_vol + 2,1,"high=" + values[CTRL_HIGH]);
                }
            }
        }
        return values;
    }





    //---------------------------------
    // SET
    //---------------------------------

    public void setValue(int idx, int val)
        // They are set individually.
        // They are always compared to the actual volume controls.
        // Resets last_values if they changed.
    {
        int new_value = valid(idx,val);
        int old_values[] = getValues();
        Utils.log(dbg_vol,0,"setValue(" + idx + "," + val + ")   old=" + old_values[idx] + "  new=" + new_value);

        if (new_value != old_values[idx])
        {
            if (idx == CTRL_VOL)
            {
                LocalVolumeFixer.setMTCVolume(am,new_value);
            }

            // only car stereo gets controls besides volume
            // these should not be called anyways since the values
            // could not change from their initial states if the
            // original maxValues are setup correctly.

            if (Utils.ID_CAR_STEREO.equals(Build.ID))
            {
                if (idx == CTRL_MUTE)
                {
                    am.setParameters("av_mute=" + (new_value == 1 ? "true" : "false"));
                }

                if (idx == CTRL_LOUD)
                {
                    Settings.System.putInt(cr,"av_lud",new_value);
                    am.setParameters("av_lud=" + (new_value == 1 ? "on" : "off"));
                }

                if (idx == CTRL_BAL)
                {
                    String new_balance_str = "" + new_value + "," + old_values[CTRL_FADE];
                    Settings.System.putString(cr,"KeyBalance",new_balance_str);
                    am.setParameters("av_balance=" + new_value + "," + (28 - old_values[CTRL_FADE]));
                }
                if (idx == CTRL_FADE)
                {
                    String new_balance_str = "" + old_values[CTRL_BAL] + "," + new_value;
                    Settings.System.putString(cr,"KeyBalance",new_balance_str);
                    am.setParameters("av_balance=" + old_values[CTRL_BAL] + "," + (28 - new_value));
                }

                if (idx == CTRL_BASS || idx == CTRL_MID || idx == CTRL_HIGH)
                {
                    int bass = idx == CTRL_BASS ? new_value : old_values[CTRL_BASS];
                    int mid = idx == CTRL_MID ? new_value : old_values[CTRL_MID];
                    int high = idx == CTRL_HIGH ? new_value : old_values[CTRL_HIGH];
                    String new_eq_str = "" + bass + "," + mid + "," + high;
                    Settings.System.putString(cr,"KeyCustomEQ",new_eq_str);
                    am.setParameters("av_eq=" + new_eq_str);
                }
            }

            // client will call getValues again, so it' alright
            // if we set the parameter into old_values and set
            // last_values to that.

            last_values = old_values;
            last_values[idx] = new_value;

            artisan.handleArtisanEvent(EventHandler.EVENT_VOLUME_CHANGED,this);
        }
    }


    //-----------------------------------------------------------------------
    // Monitor
    //-----------------------------------------------------------------------

    private class VolumeMonitor implements Runnable
    {
        LocalVolume m_this;

        public VolumeMonitor(LocalVolume volume)
        {
            m_this = volume;
        }

        public void run()
        {
            boolean changed = false;
            int values[] = getValues();
            for (int i=0; i<Volume.NUM_CTRLS; i++)
            {
                if (values[i] != last_values[i])
                {
                    changed = true;
                    i = Volume.NUM_CTRLS;
                }
            }

            if (changed)
            {
                last_values = values;
                artisan.handleArtisanEvent(EventHandler.EVENT_VOLUME_CHANGED,m_this);
            }

            refresh_handler.postDelayed(this,REFRESH_TIME);
        }
    }


}   // class LocalVolume
