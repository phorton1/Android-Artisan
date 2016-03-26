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
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import prh.device.MediaServer;
import prh.types.intViewHash;
import prh.types.recordList;
import prh.utils.Fetcher;
import prh.utils.Utils;


public class aPlaylist extends Fragment implements
    ArtisanPage,
    EventHandler,
    ListItem.ListItemListener,
    Fetcher.FetcherClient
{
    private static int dbg_aplay = 1;
    private static int FETCH_INITIAL = 20;
    private static int NUM_PER_FETCH = 200;

    private TextView page_title = null;
    private Artisan artisan = null;
    private Playlist playlist = null;
    private LinearLayout my_view = null;
    private ListView the_list = null;
    private Fetcher fetcher = null;
    private boolean album_mode = false;
    private boolean is_current = false;

    public void setAlbumMode(boolean album_mode)
    {
        if (this.album_mode != album_mode)
        {
            this.album_mode = album_mode;
            init(playlist);
        }
    }

    //----------------------------------------------
    // life cycle
    //----------------------------------------------

    public void setArtisan(Artisan ma)
    // called immediately after construction
    {
        artisan = ma;
    }


    @Override
    public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle savedInstanceState)
    {
        Utils.log(dbg_aplay,0,"aPlaylist.onCreateView() called");
        my_view = (LinearLayout) inflater.inflate(R.layout.activity_playlist,container,false);
        the_list = (ListView) my_view.findViewById(R.id.playlist);
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
        if (fetcher != null)
            fetcher.stop(true,false);
        fetcher = null;

        recordList records = new recordList();
            // an empty list in case things go bad

        if (playlist != null)
        {
            fetcher = new Fetcher(
                artisan,
                playlist,
                this,
                FETCH_INITIAL,
                NUM_PER_FETCH,
                "Playlist::" + playlist.getName());
            if (album_mode)
                fetcher.setHow(Playlist.fetchHow.WITH_ALBUMS);
            if (fetcher.start())
                records = fetcher.getRecords();
                // give a copy, NOT the reference, to the
                // adapter, who takes ownership below
        }

         // and away we go ...

        ListItemAdapter adapter = new ListItemAdapter(
            artisan,
            this,
            the_list,
            null,
            records,
            true,
            !album_mode);

        the_list.setAdapter(adapter);
        updateTitleBar();
    }


    @Override
    public void onAttach(Activity activity)
    {
        Utils.log(dbg_aplay,0,"aPlaylist.onAttach() called");
        super.onAttach(activity);
        // artisan = (Artisan) activity;
    }


    @Override
    public void onDetach()
    {
        Utils.log(dbg_aplay,0,"aPlaylist.onDetach() called");
        super.onDetach();
        // artisan = null;
    }


    @Override
    public void onDestroy()
    {
        Utils.log(dbg_aplay,0,"aPlaylist.onDestroy() called");
        if (fetcher != null)
            fetcher.stop(true,true);
        fetcher = null;
        super.onDestroy();
    }


    //--------------------------------
    // utilities
    //--------------------------------


    @Override public void onSetPageCurrent(boolean current)
    {
        page_title = null;
        is_current = current;
        if (current)
        {
            page_title = new TextView(artisan);
            page_title.setText(getTitleBarText());
            artisan.setArtisanPageTitle(page_title);
            MainMenuToolbar toolbar = artisan.getToolbar();
            toolbar.showButtons(new int[]{ album_mode ?
                R.id.command_playlist_tracks :
                R.id.command_playlist_albums });

        }
    }

    private void updateTitleBar()
    {
        if (is_current && page_title != null)
        {
            page_title.setText(getTitleBarText());
            MainMenuToolbar toolbar = artisan.getToolbar();
            toolbar.initButtons();
            toolbar.showButtons(new int[]{ album_mode ?
                R.id.command_playlist_tracks :
                R.id.command_playlist_albums });
        }
    }


    private String getTitleBarText()
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


    //--------------------------------------------------------------
    // onClick()
    //--------------------------------------------------------------

    @Override public void onClick(View v)
        // Popup Metadata for Folder, or Play Track
        // Contezt menu handled by item itself.
        // This called by ListItem.onClick()
    {
        int id = v.getId();
        switch (id)
        {
            case R.id.list_item_layout:
                ListItem list_item = (ListItem) v;
                Track track = list_item.getTrack();
                if (track != null)
                    artisan.handleArtisanEvent(COMMAND_EVENT_PLAY_TRACK,track);
                break;
        }
    }


    @Override public boolean onLongClick(View v)
        // Currently does nothing in this class
        // This called by ListItem.onLongClick()
    {
        return false;
    }


    //------------------------------------------
    // FetcherClient Interface
    //------------------------------------------

    @Override public void notifyFetchRecords(Fetcher fetcher, Fetcher.fetchResult fetch_result)
    {
        Utils.log(dbg_aplay,0,"aPlaylist.notifyFetchRecords(" + fetch_result + ") called with " + fetcher.getNumRecords() + " records");
        ((ListItemAdapter) the_list.getAdapter()).
            setItems(fetcher.getRecordsRef());
    }

    @Override public void notifyFetcherStop(Fetcher fetcher,Fetcher.fetcherState fetcher_state)
    {
        Utils.error("aPlaylist.notifyFetcherStop() not implemented yet");
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

        else if (
            event_id.equals(EVENT_PLAYLIST_CHANGED) ||
            event_id.equals(EVENT_PLAYLIST_CONTENT_CHANGED) )
        {
            Playlist new_playlist = (Playlist) data;
            if (new_playlist == null || playlist == null ||
                !new_playlist.equals(playlist))
            {
                init(new_playlist);
            }
            //else if ( new_playlist != null)
            //    ((ListItemAdapter) the_list.getAdapter()).
            //        setItems(new_playlist.getAvailableTracks(false));
        }
    }


}   // class aPlaylist
