package prh.artisan;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import java.util.HashMap;

import prh.artisan.utils.MainMenuButton;


public class MainMenuToolbar extends LinearLayout
{
    private Artisan unused_artisan;
    private HashMap<Integer,Float> orig_alpha = new HashMap<>();

    //------------------------------------------
    // construction, onFinishInflate()
    //------------------------------------------


    public MainMenuToolbar(Context context,AttributeSet attrs)
    {
        super(context,attrs);
        unused_artisan = (Artisan) context;
    }


    @Override public void onFinishInflate()
    {
        for (int i=0; i<getChildCount(); i++)
        {
            View v = getChildAt(i);
            orig_alpha.put(v.getId(),v.getAlpha());
        }
        initButtons();
    }


    public void initButtons()
        // by default, all buttons are enabled,
        // but not showing.
    {
        for (int i=0; i<getChildCount(); i++)
        {
            View v = getChildAt(i);
            v.setVisibility(View.GONE);
            v.setEnabled(true);
        }
    }


    public void showButton(int res_id, boolean show)
    {
        View v = findViewById(res_id);
        if (v != null)
            v.setVisibility(show ? View.VISIBLE : View.GONE );
    }

    public void enableButton(int res_id, boolean enabled)
    {
        View v = findViewById(res_id);
        if (v != null)
        {
            Float alpha = orig_alpha.get(res_id);
            v.setAlpha(enabled ? alpha : alpha / 2);

            // we don't really disable the button
            // so that presses on it don't fall thru
            // header ... i.e. when you're backing library
            // you don't want it to change full_screen
            // if you press an extra one.

            ((MainMenuButton) v).setFakeEnabled(enabled);

            // v.setEnabled(enabled);

            // following is needed because the button could
            // get disabled by it's own press and never
            // receive the up event
            // ((ImageView)v).clearColorFilter();
        }
    }

    public void showButtons(int res_ids[])
    {
        initButtons();
        for (int res_id : res_ids)
            showButton(res_id,true);
    }


}   // class MainMenuToolbar
