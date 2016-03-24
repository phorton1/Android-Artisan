package prh.artisan;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import java.util.HashMap;

public class MainMenuToolbar extends LinearLayout implements
    View.OnClickListener
{
    private Artisan artisan;
    private HashMap<Integer,Boolean> showing = new HashMap<>();


    //------------------------------------------
    // construction, onFinishInflate()
    //------------------------------------------

    public MainMenuToolbar(Context context,AttributeSet attrs)
    {
        super(context,attrs);
        artisan = (Artisan) context;
    }


    @Override public void onFinishInflate()
    {
        for (int i=0; i<getChildCount(); i++)
        {
            View v = getChildAt(i);
            showing.put(v.getId(),false);
            v.setOnClickListener(this);
            updateButtons();
        }
    }


    //--------------------------------------------
    // setup
    //--------------------------------------------

    private void updateButtons()
    {
        for (int res_id : showing.keySet())
            findViewById(res_id).setVisibility(
                showing.get(res_id) ?
                    View.VISIBLE :
                    View.GONE);
    }


    public void setup(int res_ids[])
    {
        for (int res_id : showing.keySet())
            showing.put(res_id,false);

        for (int i : res_ids)
            showing.put(i,true);

        updateButtons();
    }


    //--------------------------------------
    // onClick()
    //--------------------------------------

    public void onClick(View v)
    {

    }




}   // class MainMenuToolbar
