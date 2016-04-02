package prh.utils;

import android.app.Dialog;
import android.support.annotation.IdRes;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import prh.artisan.Artisan;
import prh.artisan.Prefs;


public class unused_getStringDialog extends Dialog
    implements View.OnClickListener
{
    Artisan artisan;
    String cur_string;
    String orig_string;
    stringValidator validator;

    public interface stringValidator {
        int checkString(String s); }
            // check the string, and return one of the following values
            //     0 = keep trying
            //    -1 = reset to original value
            //     1 = ok, i got it


    public unused_getStringDialog(stringValidator validator,Artisan ma,String msg,String s)
    {
        super(ma);
        artisan = ma;
        this.validator = validator;
        cur_string = s;
        orig_string = s;

        TextView prompt = new TextView(artisan);
        prompt.setText(msg);
        TextView value = new TextView(artisan);
        value.setText(cur_string);
        Button ok = new Button(artisan);
        ok.setText("OK");
        ok.setOnClickListener(this);
        Button cancel = new Button(artisan);
        ok.setText("Cancel");
        cancel.setOnClickListener(this);

        addContentView(prompt,null);
        addContentView(value,null);
        addContentView(ok,null);
        addContentView(cancel,null);

        this.show();
    }


    private void resetString()
    {
        cur_string = orig_string;
    }

    private void endDialog()
    {

    }

    public void onClick(View v)
    {
        Button button = (Button) v;
        if (button.getText().equals("OK"))
        {
            int rslt = validator.checkString(cur_string);
            if (rslt == -1)
                resetString();
            if (rslt == 1)
                this.dismiss();
        }
        else
            this.dismiss();
    }

}
