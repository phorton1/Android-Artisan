package prh.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import prh.artisan.Artisan;
import prh.artisan.Prefs;
import prh.utils.Utils;


public class StartAtBoot extends BroadcastReceiver
{
    private static int dbg_start = 0;

    @Override
    public void onReceive(Context context, Intent intent)
    {
        Utils.log(dbg_start,0,"StartAtBoot.onReceive() called");

        if (true)
        {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Utils.log(dbg_start,1,"prefs = " + prefs);
            String id = Prefs.id.START_ON_BOOT.toString();
            Boolean start_at_boot = prefs.getBoolean(id,false);
            Utils.log(dbg_start,1,"prefs(" + id + ")=" + start_at_boot);

            if (start_at_boot)
            {
                Utils.log(0,0,"STARTING ARTISAN AT BOOT");
                Intent i = new Intent(context,Artisan.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        }
    }
}
