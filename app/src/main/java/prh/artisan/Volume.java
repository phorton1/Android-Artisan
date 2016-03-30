package prh.artisan;

import prh.utils.Utils;

public interface Volume
    // base class of Volume control devices
    // base class provides constants for a "volume only" device
    // (no balance, fader, eq, etc) with a range of 0..16
{
    int CTRL_VOL = 0;
    int CTRL_MUTE = 1;
    int CTRL_LOUD = 2;
    int CTRL_BAL = 3;
    int CTRL_FADE = 4;
    int CTRL_BASS = 5;
    int CTRL_MID = 6;
    int CTRL_HIGH = 7;
    int NUM_CTRLS = 8;

    static String ctrl_names[] = new String[]{ "vol", "mute", "loud", "bal", "fade", "bass", "mid", "high" };

    void start();
    void stop();

    int[] getMaxValues();
    int[] getValues();
    int[] getUpdateValues();

    // Never called by me with illegal value ..

    void setValue(int idx, int value);
    void incDecValue(int idx, int inc);


}
