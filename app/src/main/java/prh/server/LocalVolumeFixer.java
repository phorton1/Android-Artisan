package prh.server;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;

import prh.artisan.Artisan;
import prh.utils.Utils;


public class LocalVolumeFixer
    // The main entry point is ctor, start(),
    // which sets up a timer loop to normalize the Android
    // and MTC volumes on the real car stereo.
    //
    // Does not touch any UI.
    //
    // This class also provides a pure static low level method,
    // setMTCVolume(), which may be called by other clients.
    // As such it needs a few static variables.
{
    private static int dbg_fixer = 1;
    private static int NORMALIZE_RATE = 500;
        // milliseconds

    private static int g_last_android_vol = -1;
    private static int g_last_mtc_vol = -1;
    private static boolean g_android_hit = false;
        // statics used by setMTCVolume()

    private int timer_count = 0;
    private Handler refresh_handler = null;
    private VolumeNormalizer normalizer = null;
    private boolean in_vol_change = false;
    private Artisan artisan;

    public LocalVolumeFixer(Artisan a)
    {
        artisan = a;
    }


    public void start()
        // creates the timer loop that fixes the volumes
        // Should run all the time on the car stereo
    {
        if (Utils.ID_CAR_STEREO.equals(Build.ID))
        {
            Utils.log(0,0,"LocalVolumeFixer.start() ...");
            timer_count = 0;
            in_vol_change = false;
            refresh_handler = new Handler();
            normalizer = new VolumeNormalizer();
            refresh_handler.postDelayed(normalizer,NORMALIZE_RATE);
            Utils.log(0,0,"LocalVolumeFixer started");
        }
        else
        {
            Utils.log(dbg_fixer,0,"Ignoring call to LocalVolumeFixer.start() on non-car-stereo id:" + Build.ID);
        }
    }


    public void stop()
    {
        Utils.log(0,0,"LocalVolumeFixer.stop()");
        if (refresh_handler != null)
            refresh_handler.removeCallbacks(normalizer);
        refresh_handler = null;
        normalizer = null;
    }


    private class VolumeNormalizer implements Runnable
        // only called on real car stereo
    {
        @Override
        public void run()
        {
            Utils.log(dbg_fixer,0,"normalizeVolumes::run() called");

            if (!in_vol_change)
            {
                in_vol_change = true;
                timer_count++;

                Utils.log(dbg_fixer+1,1,"!in_vol_change");

                Context ctx = artisan.getApplicationContext();
                AudioManager am = (AudioManager) ctx.getSystemService(ctx.AUDIO_SERVICE);

                Utils.log(dbg_fixer+1,1,"getting volumes");
                    // get the mtc volume (0..30)
                    // and the android_vol (0..15)

                int mtc_vol = android.provider.Settings.System.getInt(ctx.getContentResolver(), "av_volume=", 15);
                int android_vol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                Utils.log(dbg_fixer+1,1,"checkVolumes(" + timer_count+ ") android_vol=" + android_vol + " mtc_vol=" + mtc_vol);

                // hopefully only one of them will changed
                // first time ... init the values

                if (g_last_android_vol == -1)
                {
                    g_last_android_vol = android_vol;
                    g_last_mtc_vol = mtc_vol;
                    Utils.log(dbg_fixer + 1,0,"checkVolumes(" + timer_count + ") set initial volumes android=" + android_vol + "  mtc=" + mtc_vol);
                }
                else if (g_last_android_vol != android_vol)
                {
                    Utils.log(dbg_fixer+1,1,"checkVolumes(" + timer_count + ") android_vol changed from " + g_last_android_vol + " to " + android_vol);
                    Utils.log(dbg_fixer+1,1,"            (" + timer_count + ") mtc_vol=" + mtc_vol + "  last_mtc_vol=" + g_last_mtc_vol);
                    Utils.log(dbg_fixer+1,1,"            (" + timer_count + ") setting last_mtc_vol=" + (g_last_android_vol * 2));
                    setMTCVolume(am,g_last_android_vol);
                }
                else if (g_last_mtc_vol != mtc_vol)
                {
                    // in the case that the last call was an g_android_hit,
                    // we DONT touch the last_android_vol, but in either case
                    // we do set last_mtc vol and call setStreamVolume().
                    // Harder than hell to explain, but it seems to work/

                    Utils.log(dbg_fixer+1,1,"checkVolumes(" + g_android_hit + "," + timer_count + ") mtc_vol changed from " + g_last_mtc_vol + " to " + mtc_vol);
                    Utils.log(dbg_fixer+1,1,"            (" + timer_count + ") android_vol=" + android_vol + " last_android_vol=" + g_last_android_vol);
                    Utils.log(dbg_fixer+1,1,"            (" + timer_count + ") setting last_mtc_vol to " + mtc_vol);

                    if (g_android_hit)
                    {
                        g_android_hit = false;
                    }
                    else
                    {
                        Utils.log(dbg_fixer+1,1,"            (" + timer_count + ") NORMAL CASE setting last_android_vold=" + (mtc_vol / 2));
                        g_last_android_vol = mtc_vol / 2;
                    }
                    g_last_mtc_vol = mtc_vol;
                    am.setStreamVolume(AudioManager.STREAM_MUSIC,g_last_android_vol, 0); // AudioManager.FLAG_SHOW_UI);//

                }
                else
                {
                    // log("checkVolumes(" + m_timer_count + ") no changes");
                }
                in_vol_change = false;

            }

            refresh_handler.postDelayed(this,NORMALIZE_RATE);

        }   // run()()
    }   // class checkVolumes


    // static methods common to normalizeVolumesLoop and
    // doVolumeControl dialog (loop to set volumes)

    private static int androidToMTCVolume(int android_vol)
    {
        // This function is reverse engineered from package
        // android.microntek.service.MicrontekServer
        // prh - constant 15 is maxVolume

        float f1 = 100.0F * android_vol / 15;
        float f2;
        if (f1 < 20.0F) {
            f2 = f1 * 3.0F / 2.0F;
        } else if (f1 < 50.0F) {
            f2 = f1 + 10.0F;
        } else {
            f2 = 20.0F + f1 * 4.0F / 5.0F;
        }
        return (int) f2;
    }


    public static void setMTCVolume(AudioManager am, int android_vol)
    // Called on ALL devices, just uses MTC Volume rantes
    {
        int mtc_scaled_vol = androidToMTCVolume(android_vol);
        Utils.log(dbg_fixer,0,"setMTCVolume(" + android_vol + ") mtc_scaled_vol=" + mtc_scaled_vol);
        // android.provider.Settings.System.putInt(ctx.getContentResolver(), "av_volume=", android_vol);

        am.setParameters("av_volume=" + mtc_scaled_vol);
        Utils.log(dbg_fixer,0,"setMTCVolume2");

        // next line may not be required!
        am.setStreamVolume(AudioManager.STREAM_MUSIC,android_vol,0); // AudioManager.FLAG_SHOW_UI);
        Utils.log(dbg_fixer,0,"setMTCVolume3");

        // set extra funny business
        g_last_android_vol = android_vol;
        g_last_mtc_vol = g_last_android_vol * 2;
        g_android_hit = true;
    }


}   // class VolumeFixer
