package prh.device;


import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;

import prh.artisan.Artisan;
import prh.artisan.EventHandler;
import prh.artisan.Volume;
import prh.artisan.VolumeControl;
import prh.server.LocalVolumeFixer;
import prh.utils.Utils;


public class LocalVolume implements Volume
    // The LocalVolume is intimately tied to the LocalRenderer,
    // which constructs it, and calls getUpdateValues() in it's
    // loop to generate and and dispatch VOLUME_CHANGED Events.

{
    private static int dbg_vol = 0;

    private Artisan artisan;
    private AudioManager am;
    private ContentResolver cr;

    private int max_values[];
    private int current_values[];

    @Override public int[] getMaxValues()  { return max_values; }
    @Override public int[] getValues()     { return current_values.clone(); }


    public LocalVolume(Artisan a)
    {
        artisan = a;
    }

    @Override public void stop()
    {
        Utils.log(0,0,"LocalVolume.stop()");
        am = null;
        cr = null;
        current_values = null;
    }

    @Override public void start()
        // The Volume Object needs access to the AudioManager
        // for most actions, and needs a "ContentResolver" to
        // get/set the Car Stereo Loudness, Balance, and EQ
        // via Settings.System.getString().
    {
        Utils.log(0,0,"LocalVolume.start()");
        am = (AudioManager) artisan.getSystemService(Context.AUDIO_SERVICE);
        cr = artisan.getContentResolver();

        // initialize car stereo maximum values

        if (Utils.ID_CAR_STEREO.equals(Build.ID))
            max_values = new int[]{15, 1, 1, 28, 28, 20, 20, 20};
        else
            max_values = new int[]{15, 0, 0, 0, 0, 0, 0, 0};

        // initialize some current values

        current_values = new int[]{0,0,0,0,0,0,0,0};
        getUpdateValues();
    }


    @Override  public void incDecValue(int idx, int inc)
        // re-impelmented in each derived Volume class
    {
        int values[] = getValues();
        int value = values[idx];
        value += inc;
        setValue(idx,value);
    }


    //---------------------------------
    // GET
    //---------------------------------


    @Override public int[] getUpdateValues()
        // They are gotten as a group
        // Always returns a fresh new copy of the values.
        // We detect if am has "gone away" and just return 0s in that case
    {
        Utils.log(dbg_vol+2,0,"getValues()");
        int new_values[] = new int[]{0,0,0,0,0,0,0,0};

        if (am != null)
        {
            // volume

            new_values[CTRL_VOL] = VolumeControl.valid(max_values,CTRL_VOL,
                am.getStreamVolume(AudioManager.STREAM_MUSIC));
            Utils.log(dbg_vol + 1,1,"vol=" + new_values[CTRL_VOL]);

            // car stereo gets/sets other value

            if (Utils.ID_CAR_STEREO.equals(Build.ID))
            {
                String mute_str = am.getParameters("av_mute=");
                new_values[CTRL_MUTE] = mute_str.equals("true") ? 1 : 0;   // inherently valid
                Utils.log(dbg_vol + 2,1,"mute=" + new_values[CTRL_MUTE]);

                try
                {
                    new_values[CTRL_LOUD] = VolumeControl.valid(max_values,CTRL_LOUD,
                        Settings.System.getInt(cr,"av_lud"));
                }
                catch (Exception e)
                {
                    Utils.error("getting av_lud:" + e);
                }
                Utils.log(dbg_vol + 2,1,"loud=" + new_values[CTRL_LOUD]);

                String balance_str = Settings.System.getString(cr,"KeyBalance");
                Utils.log(dbg_vol + 1,1,"balance_str=" + balance_str);
                if (balance_str != null)
                {
                    String parts[] = balance_str.split(",");
                    new_values[CTRL_BAL] = VolumeControl.valid(max_values,CTRL_BAL,
                        Utils.parseInt(parts[0]));
                    new_values[CTRL_FADE] = VolumeControl.valid(max_values,CTRL_FADE,
                        Utils.parseInt(parts[1]));
                    Utils.log(dbg_vol + 2,1,"bal=" + new_values[CTRL_BAL]);
                    Utils.log(dbg_vol + 2,1,"fade=" + new_values[CTRL_FADE]);
                }

                String eq_str = Settings.System.getString(cr,"KeyCustomEQ");
                Utils.log(dbg_vol + 1,1,"eq_str=" + eq_str);
                if (eq_str != null)
                {
                    String parts[] = eq_str.split(",");
                    new_values[CTRL_BASS] = VolumeControl.valid(max_values,CTRL_BASS,Utils.parseInt(parts[0]));
                    new_values[CTRL_MID] = VolumeControl.valid(max_values,CTRL_MID,Utils.parseInt(parts[1]));
                    new_values[CTRL_HIGH] = VolumeControl.valid(max_values,CTRL_HIGH,Utils.parseInt(parts[2]));
                    Utils.log(dbg_vol + 2,1,"bass=" + new_values[CTRL_BASS]);
                    Utils.log(dbg_vol + 2,1,"mid=" + new_values[CTRL_MID]);
                    Utils.log(dbg_vol + 2,1,"high=" + new_values[CTRL_HIGH]);
                }
            }

            // if any values changed, dispatch the event

            if (VolumeControl.changed(new_values,current_values))
            {
                current_values = new_values;
                artisan.handleArtisanEvent(EventHandler.EVENT_VOLUME_CHANGED,this);
            }

        }   // we have an audio manager

        return current_values.clone();

    }   // getUpdateValues()





    //---------------------------------
    // SET
    //---------------------------------

    @Override public void setValue(int idx, int val)
        // They are set individually.
        // They are always compared to the actual volume controls.
        // Resets last_values if they changed.
    {
        int new_value = VolumeControl.valid(max_values,idx,val);
        Utils.log(dbg_vol,0,"setValue(" + idx + "," + val + ")   old=" + current_values[idx] + "  new=" + new_value);

        if (new_value != current_values[idx])
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
                    String new_balance_str = "" + new_value + "," + current_values[CTRL_FADE];
                    Settings.System.putString(cr,"KeyBalance",new_balance_str);
                    am.setParameters("av_balance=" + new_value + "," + (28 - current_values[CTRL_FADE]));
                }
                if (idx == CTRL_FADE)
                {
                    String new_balance_str = "" + current_values[CTRL_BAL] + "," + new_value;
                    Settings.System.putString(cr,"KeyBalance",new_balance_str);
                    am.setParameters("av_balance=" + current_values[CTRL_BAL] + "," + (28 - new_value));
                }

                if (idx == CTRL_BASS || idx == CTRL_MID || idx == CTRL_HIGH)
                {
                    int bass = idx == CTRL_BASS ? new_value : current_values[CTRL_BASS];
                    int mid = idx == CTRL_MID ? new_value : current_values[CTRL_MID];
                    int high = idx == CTRL_HIGH ? new_value : current_values[CTRL_HIGH];
                    String new_eq_str = "" + bass + "," + mid + "," + high;
                    Settings.System.putString(cr,"KeyCustomEQ",new_eq_str);
                    am.setParameters("av_eq=" + new_eq_str);
                }


            }   // Car Stereo Only controls

            // Set the new value into the current_values
            // and send the event

            current_values[idx] = new_value;
            artisan.handleArtisanEvent(EventHandler.EVENT_VOLUME_CHANGED,this);

        }   // new_value != current_value[idx];
    }   // setValue()

}   // class LocalVolume
