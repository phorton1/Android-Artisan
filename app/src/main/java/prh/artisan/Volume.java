package prh.artisan;

import prh.utils.Utils;

public abstract class Volume
    // base class of Volume control devices
    // base class provides constants for a "volume only" device
    // (no balance, fader, eq, etc) with a range of 0..16
    // Responsible for eventing VOLUME_CHANGED events, but
    // assumes parent will send VOLUME_CONFIG_CHANGED
{
    public static int CTRL_VOL = 0;
    public static int CTRL_MUTE = 1;
    public static int CTRL_LOUD = 2;
    public static int CTRL_BAL = 3;
    public static int CTRL_FADE = 4;
    public static int CTRL_BASS = 5;
    public static int CTRL_MID = 6;
    public static int CTRL_HIGH = 7;
    public static int NUM_CTRLS = 8;

    protected static String ctrl_names[] = new String[]{ "vol", "mute", "loud", "bal", "fade", "bass", "mid", "high" };

    protected int max_values[] =  new int[]{0, 0, 0, 0, 0, 0, 0, 0};
    public int[] getMaxValues()  { return max_values; }

    // virtual API

    public abstract void start();
    public abstract void stop();
    public abstract int[] getValues();
    public abstract void setValue(int idx, int value);

    // immplemented

    public void incDec(int idx, int inc)
    {
        int values[] = getValues();
        int value = values[idx];
        value += inc;
        setValue(idx,value);
    }

    protected int valid(int idx, int val)
    {
        int max = max_values[idx];
        String descrip = ctrl_names[idx];
        if (val < 0)
        {
            Utils.error("illegal value for " + descrip + "=" + val + " ... setting to zero");
            val = 0;
        }
        if (val > max)
        {
            Utils.error("illegal value for " + descrip + "=" + val + " ... setting to max=" + max);
            val = max;
        }
        // Utils.log(3,0,"valid(" + descrip + ") idx=" + idx + " val=" + val + " finished");
        return val;
    }


}
