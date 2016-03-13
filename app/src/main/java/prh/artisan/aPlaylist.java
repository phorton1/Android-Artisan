package prh.artisan;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import prh.utils.Utils;


public class aPlaylist extends Fragment implements ArtisanPage
{
    static int dbg_aplay = 0;
    private Artisan artisan = null;

    public String getName()  { return "Playlist"; }

    //----------------------------------------------
    // life cycle
    //----------------------------------------------

    @Override
    public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle savedInstanceState)
    {
        Utils.log(dbg_aplay,0,"aPlaylist.onCreateView() called");
        return inflater.inflate(R.layout.activity_playlist, container, false);
    }

    @Override
    public void onAttach(Activity activity)
    {
        Utils.log(dbg_aplay,0,"aPlaylist.onAttach() called");
        super.onAttach(activity);
        artisan = (Artisan) activity;
    }


    @Override
    public void onDetach()
    {
        Utils.log(dbg_aplay,0,"aPlaylist.onDetach() called");
        super.onDetach();
        artisan = null;
    }


}   // class aPlaylist
