package prh.artisan;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import prh.device.MediaServer;
import prh.types.intViewHash;
import prh.types.recordList;
import prh.utils.Utils;


public class aPlaylist extends Fragment implements
    ArtisanPage,
    EventHandler,
    View.OnClickListener,
    View.OnLongClickListener
{
    static int dbg_aplay = 0;
    private Artisan artisan = null;
    private Playlist playlist = null;
    LinearLayout my_view = null;
    ListView the_list = null;


    @Override public String getTitle()
    {
        return getArtisanTitleBarText();
    }

    private void updateArtisanTitleBar()
    {
        artisan.SetMainMenuText(
            Artisan.PAGE_PLAYLIST,
            getArtisanTitleBarText());
    }

    private String getArtisanTitleBarText()
    {
        String msg =
            playlist == null ? "Playlist" :
            playlist.isLocal() ? "LocalPlaylist " :
            "RemotePlaylist";
        String name =
            playlist == null ? "" :
            playlist.getName();

        if (!name.isEmpty())
            msg += "(" + name + ")";
        return msg;
    }




    //----------------------------------------------
    // life cycle
    //----------------------------------------------

    @Override
    public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle savedInstanceState)
    {
        Utils.log(dbg_aplay,0,"aPlaylist.onCreateView() called");
        my_view = (LinearLayout) inflater.inflate(R.layout.activity_playlist, container, false);
        the_list = (ListView) my_view.findViewById(R.id.playlist);
        the_list.setScrollBarSize(44);
        the_list.setVerticalScrollBarEnabled(true);
        the_list.setScrollBarDefaultDelayBeforeFade(7000);

        init(artisan.getRenderer());
        return my_view;
    }


    private void init(Renderer renderer)
    {
        playlist = renderer == null ? null :
            renderer.getPlaylist();
        init(playlist);
    }


    private void init(Playlist new_playlist)
        // note that Playlist.getTrack() is one based
    {
        playlist = new_playlist;
        recordList tracks = new recordList();
        if (playlist != null)
            tracks = playlist.getAvailableTracks();
        playlistAdapter adapter = new playlistAdapter(tracks,false);
        the_list.setAdapter(adapter);
        updateArtisanTitleBar();
    }



    // connect to the current playlist as given by the current renderer
    // construct the adapter



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


    //---------------------------------------
    // playlistAdapter
    //---------------------------------------


    private class playlistAdapter extends ArrayAdapter<Record>
    {
        private int num_items = 0;
        private recordList records = new recordList();
        private boolean album_mode = false;
        public Playlist getPlaylist()  { return playlist; }

        // ctor

        public playlistAdapter(recordList the_records, boolean the_album_mode)
        {
            super(artisan,-1,the_records);
            album_mode = the_album_mode;
            records = the_records;
            num_items = records.size();

            // notifyDataSetChanged();
        }

        // build the views
        // code almost exactly the same as aLibary version

        public View getView(int position, View re_use, ViewGroup view_group)
        {
            Record rec = records.get(position);
            LayoutInflater inflater = LayoutInflater.from(artisan);
            ListItem list_item = (ListItem) inflater.inflate(R.layout.list_item_layout,view_group,false);
            if (rec instanceof Track)
            {
                list_item.setTrack((Track) rec);
                if (!album_mode)
                    list_item.setLargeView();
            }
            else
            {
                list_item.setFolder((Folder) rec);
                list_item.setLargeView();
            }
            list_item.doLayout(aPlaylist.this,aPlaylist.this);
            return list_item;
        }


        // add items

        public void addItems(Playlist playlist)
            // called by ARTISAN_EVENT when the remote Playlist device
            // gets more fetched tracks.
        {
            recordList tracks = playlist.getAvailableTracks();
            int first = records.size();
            int last = tracks.size() - 1;
            if (last >= first)
            {
                Utils.log(0,4,"adding " + (last-first+1) + " items to existing " + first + " records");
                records.addAll(tracks.subList(first,last));
                Utils.log(0,4,"changing num_items to " + records.size() + " and calling notify DataSet changed()");
                num_items = records.size();
                notifyDataSetChanged();
            }
        }

    }   // class libraryListAdapter




    //--------------------------------------------------------------
    // onClick()
    //--------------------------------------------------------------


    @Override public void onClick(View v)
    // traverse to child node, or
    // popup context menu
    {
        int id = v.getId();

        // Control click handlers call back to Artisan
        // onBodyClicked() and eat the click if it returns true.

        if (artisan.onBodyClicked())
            return;

        // Otherwise, handle the click to a Folder or Track

        ListItem list_item;
        switch (id)
        {
            case R.id.list_item_layout :
                // single click on playlist item not defined yet
                break;

            case R.id.list_item_right :
            case R.id.list_item_right_text :
                String msg = "ListItem ContextMenu ";
                View item = (View) v.getParent();
                if (id == R.id.list_item_right_text)
                    item = (View) item.getParent();
                list_item = (ListItem) item;
                if (list_item.getFolder() != null)
                    msg += "Folder:" + list_item.getFolder().getTitle();
                else
                    msg += "Track:" + list_item.getTrack().getTitle();
                Toast.makeText(artisan,msg,Toast.LENGTH_LONG).show();
                break;
        }
    }



    @Override public boolean onLongClick(View v)
    {
        if (artisan.onBodyClicked())
            return true;

        if (v.getId() == R.id.list_item_layout)
        {
            ListItem list_item = (ListItem) v;
            list_item.setSelected(!list_item.getSelected());
            if (list_item.getSelected())
                list_item.setBackgroundColor(0xFF332200);
            else
                list_item.setBackgroundColor(Color.BLACK);
            return true;
        }
        return false;
    }



    //--------------------------------------------------------------
    // Artisan Event Handling
    //--------------------------------------------------------------


    @Override public void handleArtisanEvent(final String event_id,final Object data)
    {
        if (event_id.equals(EVENT_RENDERER_CHANGED))
        {
            init((Renderer) data);
        }
        else if (event_id.equals(EVENT_PLAYLIST_CHANGED))
        {
            Playlist new_playlist = (Playlist) data;
            if (new_playlist == null || playlist == null ||
                !new_playlist.getName().equals(playlist.getName()))
            {
                init(new_playlist);
            }
        }
        else if (event_id.equals(EVENT_ADDL_PLAYLIST_ITEMS))
        {
            // Assumes that if we get this event, we are (still) hooked to
            // a remote Playlist.  If not, the names wont match ..

            Playlist event_playlist = (Playlist) data;
            if (playlist != null && playlist.equals(event_playlist))
            {
                ((playlistAdapter)the_list.getAdapter()).
                    addItems(event_playlist);
            }
        }
    }


}   // class aPlaylist
