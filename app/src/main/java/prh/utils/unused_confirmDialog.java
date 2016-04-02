package prh.utils;

import android.app.AlertDialog;
import android.content.DialogInterface;

import prh.artisan.Artisan;

public class unused_confirmDialog
{
    Artisan artisan;

    public unused_confirmDialog(Artisan ma)
    {
        artisan = ma;
    }


    public void YesNo(String msg, Runnable yes_action, Runnable no_action)
    {

    }

    public void YesNoCancel(String msg, Runnable yes_action, Runnable no_action)
    {

    }

    public void confirm(
        //String title,
        String msg,
        final Runnable run_on_yes)
    {
        new AlertDialog.Builder(artisan)
            //.setTitle(title)
            .setMessage(msg)
                //.setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(android.R.string.yes,
                new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog,int id)
                    {
                        run_on_yes.run();
                    }
                })
                //.setNegativeButton(android.R.string.no,null)
            .show();
    }

}
