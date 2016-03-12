package prh.artisan;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import prh.utils.Utils;


public class aPrefs extends Fragment
{
    private static int dbg_aprefs = 0;
    private Artisan artisan = null;

    //----------------------------------------------
    // life cycle
    //----------------------------------------------

    @Override
    public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle savedInstanceState)
    {
        Utils.log(dbg_aprefs,0,"aPrefs.onCreateView() called");
        return inflater.inflate(R.layout.activity_preferences, container, false);
    }

    @Override
    public void onAttach(Activity activity)
    {
        Utils.log(dbg_aprefs,0,"aPrefs.onAttach() called");
        super.onAttach(activity);
        artisan = (Artisan) activity;
    }


    @Override
    public void onDetach()
    {
        Utils.log(dbg_aprefs,0,"aPrefs.onDetach() called");
        super.onDetach();
        artisan = null;
    }

}   // class aPrefs
