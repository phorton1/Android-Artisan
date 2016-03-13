package prh.artisan;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import prh.utils.Utils;


public class aExplorer extends Fragment implements ArtisanPage
{
    private static int dbg_exp = 0;
    private Artisan artisan = null;

    public String getName()  { return "Explorer"; }


    //----------------------------------------------
    // life cycle
    //----------------------------------------------

    @Override
    public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle savedInstanceState)
    {
        Utils.log(dbg_exp,0,"aExplorer.onCreateView() called");
        return inflater.inflate(R.layout.activity_explorer, container, false);
    }

    @Override
    public void onAttach(Activity activity)
    {
        Utils.log(dbg_exp,0,"aExplorer.onAttach() called");
        super.onAttach(activity);
        artisan = (Artisan) activity;
    }


    @Override
    public void onDetach()
    {
        Utils.log(dbg_exp,0,"aExplorer.onDetach() called");
        super.onDetach();
        artisan = null;
    }


}   // class aPlaying
