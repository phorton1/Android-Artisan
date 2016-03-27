package prh.artisan;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import prh.types.recordList;
import prh.types.selectedHash;
import prh.utils.Utils;


public class aPlaylist extends Fragment implements
    ArtisanPage,
    EventHandler,
    ListItem.ListItemListener,
    Fetcher.FetcherClient
{
    private static int dbg_aplay = 1;
    private static int FETCH_INITIAL = 200;
    private static int NUM_PER_FETCH = 400;

    private TextView page_title = null;
    private Artisan artisan = null;
    private Playlist playlist = null;
    private LinearLayout my_view = null;
    private ListView the_list = null;
    private Fetcher fetcher = null;
    private boolean album_mode = false;
    private boolean is_current = false;
    private selectedHash selected;


    // scroll position

    private int scroll_index = 0;
    private int scroll_offset = 0;
    private Track scroll_track = null;


    //----------------------------------------------
    // top level accessors
    //----------------------------------------------

    public void setArtisan(Artisan ma)
    // called immediately after construction
    {
        artisan = ma;
    }


    //----------------------------------------------
    // life cycle
    //----------------------------------------------

    @Override
    public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle savedInstanceState)
    {
        Utils.log(dbg_aplay,0,"aPlaylist.onCreateView() called");
        my_view = (LinearLayout) inflater.inflate(R.layout.activity_playlist,container,false);
        the_list = (ListView) my_view.findViewById(R.id.playlist);

        // Determines if this is a cold init based on
        // selected == null or if the playlist has changed

        boolean cold_init = false;
        if (selected == null)
        {
            selected = new selectedHash();
            cold_init = true;
        }

        Renderer renderer = artisan.getRenderer();
        Playlist pl = renderer == null ? null : renderer.getPlaylist();

        init(pl,cold_init,cold_init);
        return my_view;
    }


    @Override
    public void onPause()
    {
        Utils.log(dbg_aplay,0,"aPlaylist.onPause() called");
        saveScroll(false);
        super.onPause();
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
        // fetcher = null;
        super.onDestroy();
    }



    //------------------------------------------------
    // init()
    //------------------------------------------------

    private void init(Playlist pl, boolean pl_changed, boolean content_changed)
    {
        if (playlist != pl)
            pl_changed = true;
        playlist = pl;

        // save the cursor position if the only that change was content

        if (!pl_changed && content_changed)
            saveScroll(true);

        // clear the selection on playlist or content change

        if (pl_changed || content_changed)
            selected.clear();

        // if the playlist changed, then everything changes

        if (pl_changed)
        {
            if (fetcher != null)
                fetcher.stop(true,false);
            fetcher = null;
        }

        // same playlist == same fetcher

        boolean new_fetcher = false;
        if (fetcher == null && playlist != null)
        {
            new_fetcher = true;
            fetcher = new Fetcher(
                artisan,
                playlist,
                this,
                FETCH_INITIAL,
                NUM_PER_FETCH,
                "Playlist::" + playlist.getName());
        }

        // create empty record list in case things go bad

        recordList records = new recordList();

        // start, rebuild, or use the fetcher as is

        if (fetcher != null)
        {
            boolean mode_changed = false;
            if (fetcher.getAlbumMode() != album_mode)
                mode_changed = true;
            fetcher.setAlbumMode(album_mode);

            boolean ok = true;
            if (pl_changed || new_fetcher)
            {
                ok = fetcher.start();
            }

            // this should work ok for now with localPlaylists
            // but if this was a remote playlist, then how does
            // content_changed get the new records?

            else if (mode_changed || content_changed)
            {
                ok = fetcher.rebuild();
            }

            // falls thru to use as is in warm-init case

            if (ok)
                records = fetcher.getRecords();
        }

        // and away we go ...

        ListItemAdapter adapter = (ListItemAdapter) the_list.getAdapter();

        if (adapter == null)
        {
            adapter = new ListItemAdapter(
                artisan,
                this,
                the_list,
                null,
                records,
                true,
                !album_mode);
            the_list.setAdapter(adapter);
        }
        else
        {
            adapter.setLargeFolders(true);
            adapter.setLargeTracks(!album_mode);
            adapter.setItems(records);

            // fragile - fragile - fragile
            // messed around with this all day
            // direct changes like above did not seem
            // to show until I did this invalidate, even
            // though setItems does notifyDataSetChanged()

            adapter.notifyDataSetInvalidated();

            // and no matter what, I had a hard time
            // getting ny scroll thumb to show reliably
            //     adapter.setScrollBar(true);
        }

        updateTitleBar();

        // restore the scroll position anytime
        // the playlist did not change

        if (!pl_changed)
            restoreScroll(content_changed);

    }   // init()





    //------------------------------------------------
    // Scrolling and Selection
    //------------------------------------------------

    @Override public void setSelected(Record record, boolean selected)
    {
        this.selected.setSelected(record,selected);
    }

    @Override public boolean getSelected(Record record)
    {
        return selected.getSelected(record);
    }


    public void setAlbumMode(boolean album_mode)
    {
        if (this.album_mode != album_mode)
        {
            saveScroll(true);
            this.album_mode = album_mode;
            init(playlist,false,false);
        }
    }



    public void saveScroll(boolean by_track)
        // by_track saves the current track for reloading
        // otherwise it's just the index and offset
    {
        scroll_track = null;
        scroll_index = the_list.getFirstVisiblePosition();
        ListItem list_item = (ListItem) the_list.getChildAt(0);
        if (by_track && list_item != null)
        {
            scroll_track = list_item.getTrack();
            if (scroll_track == null)
            {
                list_item = (ListItem) the_list.getChildAt(1);
                if (list_item != null)
                {
                    scroll_index--;
                    scroll_track = list_item.getTrack();
                }
            }
        }
        scroll_offset = (list_item == null) ? 0 : (list_item.getTop());
        Utils.log(0,0,"saveScroll(" + scroll_index + "," + scroll_offset + ") track=" + (scroll_track == null ? null : scroll_track.getTitle()));
        // - the_list.getPaddingTop());
    }


    public void restoreScroll(boolean content_changed)
    {
        Utils.log(0,0,"restoreScroll(" + scroll_index + "," + scroll_offset + ") track=" + (scroll_track==null ? null : scroll_track.getTitle()));
        Utils.log(0,3,"fetcher count=" + fetcher.getNumRecords());
        Utils.log(0,3,"adapter count=" + the_list.getAdapter().getCount());

        if (scroll_track != null)
        {
            // if switching TO album_mode, start looking for the track forwards
            // if switching FROM album_mode, start looking backwards.
            // Note that care has been taken to ensure that the entire
            // record list has been rebuilt, and passed to the adapter.

            recordList records = fetcher.getRecordsRef();
            boolean done = false;

            int inc = album_mode ? 1 : -1;
            int count = records.size();
            int new_index = scroll_index;

            if (new_index>=count)        // assert
                new_index=count-1;

            while (!done && new_index >= 0 && new_index < count)
            {
                Record record = records.get(new_index);
                if (record.equals(scroll_track))
                    done = true;
                else
                    new_index += inc;
            }

            if (done)
                scroll_index = new_index;
        }

        Utils.log(0,1,"about to set the scroll_index(" + scroll_index + ") adapter has " + the_list.getAdapter().getCount() + " records");
        the_list.setSelectionFromTop(scroll_index,scroll_offset);

        // more attempts to get scrolling and thumb reliable
        //ListItemAdapter adapter = (ListItemAdapter) the_list.getAdapter();
        //adapter.setScrollPosition(scroll_index,scroll_offset);
        //adapter.setScrollBar(true);

        scroll_index = 0;
        scroll_offset = 0;
        scroll_track = null;
    }



    //--------------------------------
    // Page UI utilities
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
        Utils.warning(0,0,"aPlaylist.notifyFetcherStop() not implemented yet");
    }



    //--------------------------------------------------------------
    // Artisan Event Handling
    //--------------------------------------------------------------


    @Override public void handleArtisanEvent(final String event_id,final Object data)
    {
        if (event_id.equals(EVENT_PLAYLIST_CHANGED))

            init((Playlist) data,true,true);

        else if (event_id.equals(EVENT_PLAYLIST_CONTENT_CHANGED))

            init((Playlist) data,false,true);

        else if (event_id.equals(EVENT_RENDERER_CHANGED))
        {
            Renderer renderer = (Renderer) data;
            Playlist pl = renderer == null ? null :
                renderer.getPlaylist();
            init(pl,true,true);
        }
    }


}   // class aPlaylist
