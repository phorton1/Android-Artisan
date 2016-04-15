package prh.artisan;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import prh.base.ArtisanEventHandler;
import prh.base.ArtisanPage;
import prh.base.EditablePlaylist;
import prh.base.Renderer;
import prh.types.intList;
import prh.types.recordList;
import prh.types.recordSelector;
import prh.base.Selection;
import prh.utils.Utils;
import prh.utils.playlistFileDialog;


// implements Playlist UI

public class aPlaylist extends Fragment implements
    ArtisanPage,
    ArtisanEventHandler,
    recordSelector,
    ListView.OnItemClickListener,
    ListView.OnItemLongClickListener,
    View.OnClickListener,
    Fetcher.FetcherClient
{
    private static int dbg_aplay = 1;
    private static int FETCH_INITIAL = 20;
    private static int NUM_PER_FETCH = 60;

    private TextView page_title = null;
    private Artisan artisan = null;
    private LinearLayout my_view = null;
    private ListView the_list = null;
    private PlaylistFetcher playlist_fetcher = null;
    private EditablePlaylist the_playlist = null;
    public EditablePlaylist getThePlaylist() { return the_playlist; }

    private boolean album_mode = false;
    private boolean is_current = false;
    private Selection selected;
    private int playlist_count_id = -1;
    private int playlist_content_count_id = -1;

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



    public void setAlbumMode(boolean album_mode)
    {
        if (this.album_mode != album_mode)
        {
            saveScroll(true);
            this.album_mode = album_mode;
            init(false);
        }
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
        EditablePlaylist new_playlist = artisan.getCurrentPlaylist();
            // assert new_playlist != null

        if (selected == null)
        {
            Utils.log(dbg_aplay,1,"aPlaylist.onCreateView() COLD_INIT");
            selected = new Selection(artisan);
            cold_init = true;
        }

        init(cold_init);
        Utils.log(dbg_aplay,0,"aPlaylist.onCreateView() finished");
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
        if (playlist_fetcher != null)
            playlist_fetcher.stop(true,true);
        // playlist_fetcher = null;
        super.onDestroy();
    }



    private void init(boolean cold_init)
    {
        // detect "playlist changed" or "playlist content" changed
        // on the the_playlist, regardless of events or params

        Utils.log(dbg_aplay,0,"aPlaylist.init(" + cold_init + ") called");

        // see if the playlist really changed
        // Artisan handles the starts and stops.

        boolean pl_changed = cold_init;
        EditablePlaylist new_playlist = artisan.getCurrentPlaylist();
        // assert new_playlist != null

        if (new_playlist != the_playlist)
        {
            Utils.log(dbg_aplay,1,"aPlaylist.init(pl_changed) playlist changed to '" +
                new_playlist.getName() + "' from '" +
                (the_playlist==null?"null":the_playlist.getName()) + "'");
            the_playlist = new_playlist;
            pl_changed = true;
        }

        // detect a change in the basic playlist identity as
        // given by the playlistCountId on the playlist

        int new_count_id = the_playlist.getPlaylistCountId();
        if (!pl_changed && playlist_count_id != new_count_id)
        {
            Utils.log(dbg_aplay,1,"aPlaylist.init(pl_changed) playlist_count_id changed from " + playlist_count_id + " to =" + new_count_id);
            pl_changed = true;
        }
        playlist_count_id = new_count_id;

        // If no fetcher create one, otherwise,
        // if the playlist changed, clear the selection,
        // stop the old fetcher, and reset its source

        if (playlist_fetcher == null)
        {
            // fetcher should only be created on cold_init,
            // so there is no need to call selected.clear() here

            playlist_fetcher = new PlaylistFetcher(
                artisan,
                the_playlist,
                this,
                FETCH_INITIAL,
                NUM_PER_FETCH,
                "aPlaylist");
        }
        else if (pl_changed)
        {
            selected.clear();
            playlist_fetcher.stop(true,false);
            playlist_fetcher.setPlaylistSource(the_playlist);
        }

        // detect change in playlist contents

        boolean content_changed = pl_changed;
        int new_content_id = the_playlist.getContentChangeId();
        if (!content_changed && playlist_content_count_id != new_content_id)
        {
            Utils.log(dbg_aplay,1,"aPlaylist.init(content_changed) playlist_content_count_id changed from " + playlist_content_count_id + " to " + new_content_id);
            content_changed = true;
        }
        playlist_content_count_id = new_content_id;


        // save the cursor position if the content changed
        // but not the playlist ....

        if (content_changed && !pl_changed)
        {
            Utils.log(dbg_aplay,1,"aPlaylist.init() saving scroll position");
            saveScroll(true);
        }

        // set the album mode into the playlist_fetcher

        boolean mode_changed = false;
        if (playlist_fetcher.getAlbumMode() != album_mode)
        {
            Utils.log(dbg_aplay,1,"aPlaylist.init() album_mode changed to " + album_mode);
            playlist_fetcher.setAlbumMode(album_mode);
            mode_changed = true;
        }

        // start or restart the playlist_fetcher

        boolean ok = true;
        if (cold_init)
        {
            Utils.log(dbg_aplay,1,"aPlaylist.init() calling fetcher.start()");
            ok = playlist_fetcher.start();
        }
        else if (pl_changed || mode_changed || content_changed)
        {
            Utils.log(dbg_aplay,1,"aPlaylist.init() calling fetcher.restart()");
            ok = playlist_fetcher.restart();
        }

        // get the records

        recordList records = ok ?
            playlist_fetcher.getRecords() :
            new recordList();

        // and away we go ...

        ListItemAdapter adapter = (ListItemAdapter) the_list.getAdapter();
        if (adapter == null)
        {
            Utils.log(dbg_aplay,1,"aPlaylist.init() building new ListItemAdapter");
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
            Utils.log(dbg_aplay,1,"aPlaylist.init() using existing ListItemAdapter");
            adapter.setLargeFolders(true);
            adapter.setLargeTracks(!album_mode);
            adapter.setItems(records);

            // fragile - fragile - fragile
            // messed around with this all day
            // direct changes like above did not seem
            // to show until I did this invalidate, even
            // though setItems does notifyDataSetChanged()

            // adapter.notifyDataSetInvalidated();

            // and no matter what, I had a hard time
            // getting ny scroll thumb to show reliably
        }

        // restore the scroll position anytime
        // the playlist did not change

        if (!cold_init && !pl_changed)
        {
            Utils.log(dbg_aplay,1,"aPlaylist.init() restoring scroll position");
            restoreScroll(content_changed);
        }

        // finished ...

        updateTitleBar();
        the_list.setOnItemClickListener(this);
        the_list.setOnItemLongClickListener(this);
        Utils.log(dbg_aplay,0,"aPlaylist.init(" + cold_init + ") finished");

    }   // init()


    //------------------------------------------------
    // Selection
    //------------------------------------------------


    private recordList getAllRecords(Record record)
        // if the record is a folder
        // then return the tracks below it
        // otherwise, return null
    {
        if (record instanceof Folder)
        {
            while (playlist_fetcher.getState() == Fetcher.fetcherState.FETCHER_RUNNING)
            {
                Utils.log(0,0,"waiting for fetcher");
                Utils.sleep(400);
            }

            boolean done = false;
            recordList tracks = new recordList();
            recordList fetcher_list = playlist_fetcher.getRecordsRef();
            int start_index = fetcher_list.indexOf(record) + 1;
            while (start_index<fetcher_list.size() && !done)
            {
                Record rec = fetcher_list.get(start_index++);
                if (rec instanceof Track)
                    tracks.add(rec);
                else
                    done = true;
            }
            return tracks;
        }
        return null;
    }



    @Override public void setSelected(Record record, boolean sel)
        // if they have selected an Album (folder)
        // use the subsequent tracks
    {
        recordList tracks = getAllRecords(record);

        if (tracks != null)
        {
            for (Record track:tracks)
                selected.setSelected(track,((Track)track).getPosition(),sel);
        }

        // otherwise, it's a track

        else
        {
            selected.setSelected(record,((Track)record).getPosition(),sel);
        }

        ((ListItemAdapter) the_list.getAdapter()).notifyDataSetChanged();
        updateTitleBar();
    }



    @Override public boolean getSelected(boolean for_display, Record record)
        // if talking about a folder header,
        // it is "selected" !for_display if all the children are selected
    {
        recordList tracks = getAllRecords(record);
        if (tracks != null)
        {
            if (for_display)
                return false;

            boolean b = true;
            for (Record track : tracks)
            {
                if (!selected.getSelected(track))
                {
                    b = false;
                    break;
                }
            }
            return b;
        }

        return this.selected.getSelected(record);
    }



    @Override public void onItemClick(AdapterView<?> parent, View v, int position, long id)
        // Single Clicks are always selection actions
        // or deselect the item
    {
        ListItem list_item = (ListItem) v;
        Record rec = list_item.getRecord();
        boolean sel = getSelected(false,rec);
        setSelected(rec,!sel);
    }


    @Override public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id)
        // Regardless of selection, Long means to advance the
        // playlist or show the folder metadata.
    {
        ListItem list_item = (ListItem) v;
        Folder folder = list_item.getFolder();
        Renderer renderer = artisan.getRenderer();

        if (folder == null &&
            renderer != null)
        {
            Track track = list_item.getTrack();
            the_playlist.seekByIndex(track.getPosition());
            renderer.incAndPlay(0);
        }
        else if (folder != null)
        {
            MetaDialog.showFolder(artisan,folder);
        }
        return true;
    }


    @Override public void onClick(View v)
        // called on the "Selected" title to
        // clear the selection
    {
        selected.clear();
        updateTitleBar();
        ((ListItemAdapter)the_list.getAdapter()).notifyDataSetChanged();

    }



    //--------------------------------------------------------------
    // onMenuItemClick()
    //--------------------------------------------------------------

    @Override public boolean onMenuItemClick(MenuItem item)
    {
        int id = item.getItemId();

        switch (id)
        {
            case R.string.context_menu_new:
            {
                if (!the_playlist.isDirty())
                {
                    do_setPlaylist("");
                }
                else
                {
                    playlistFileDialog dlg = new playlistFileDialog();
                    dlg.setup(this,artisan,"new",null);
                }
                break;
            }
            case R.string.context_menu_saveas:
            {
                playlistFileDialog dlg = new playlistFileDialog();
                dlg.setup(this,artisan,"save_as",null);
                break;
            }
            case R.string.context_menu_save:
            {
                playlistFileDialog dlg = new playlistFileDialog();
                dlg.setup(this,artisan,"save",null);
                break;
            }
            case R.string.context_menu_delete:
            {
                playlistFileDialog dlg = new playlistFileDialog();
                dlg.setup(this,artisan,"delete",null);
                break;
            }
        }
        return true;
    }




    //------------------------------------------------
    // Scrolling
    //------------------------------------------------

    public void saveScroll(boolean by_track)
        // by_track saves the current track for reloading
        // otherwise it's just the index and offset
    {
        scroll_track = null;
        scroll_index = the_list.getFirstVisiblePosition();

        // special case .. if switching to album mode from the 0th item
        // don't use track relative searching .. just show top of page

        if (!album_mode && scroll_index == 0)
            by_track = false;

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
        Utils.log(0,3,"adapter count=" + the_list.getAdapter().getCount());

        if (playlist_fetcher != null && scroll_track != null)
        {
            Utils.log(0,3,"playlist_fetcher count=" + playlist_fetcher.getNumRecords());

            // if switching TO album_mode, start looking for the track forwards
            // if switching FROM album_mode, start looking backwards.
            // Note that care has been taken to ensure that the entire
            // record list has been rebuilt, and passed to the adapter.

            recordList records = playlist_fetcher.getRecordsRef();
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
        // ListItemAdapter adapter = (ListItemAdapter) the_list.getAdapter();
        // adapter.setScrollPosition(scroll_index,scroll_offset);
        // adapter.setScrollBar(true);

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
            artisan.setArtisanPageTitle(page_title);
            updateTitleBar();
        }
    }


    public void updateTitleBar()
    {
        if (is_current && page_title != null)
        {
            page_title.setText(getTitleBarText());
            page_title.setOnClickListener(selected.size()>0?this:null);

            MainMenuToolbar toolbar = artisan.getToolbar();
            toolbar.initButtons();
            toolbar.showButton(album_mode ?
                R.id.command_playlist_tracks :
                R.id.command_playlist_albums,true);


            if (getContextMenuIds().size()>0)
            {
                toolbar.showButton(R.id.command_context,true);
            }
        }
    }


    @Override public intList getContextMenuIds()
    {
        intList res_ids = new intList();

        // we don't allow them to create empty un-named playlists,
        // though if they add a track, and delete the track,
        // making it dirty, they can save it empty, cuz it's dirty

        if (selected.size() == 0)   // Playlist Context
        {
            res_ids.add(R.string.context_menu_new);

            if (the_playlist.isDirty())
            {
                res_ids.add(R.string.context_menu_save);
            }
            if (!the_playlist.getName().isEmpty())
            {
                res_ids.add(R.string.context_menu_saveas);
                res_ids.add(R.string.context_menu_rename);
                res_ids.add(R.string.context_menu_delete);
                if (the_playlist.getNumTracks()>0)
                {
                    res_ids.add(R.string.context_menu_shuffle);
                    res_ids.add(R.string.context_menu_edit_header);
                }
            }
        }
        else    // Selected Items Context
        {
            res_ids.add(R.string.context_menu_cut);
            res_ids.add(R.string.context_menu_copy);
            res_ids.add(R.string.context_menu_add);

            res_ids.add(R.string.context_menu_move);
            res_ids.add(R.string.context_edit_metadata);
        }

        if (!the_playlist.getName().isEmpty() &&
            the_playlist.getNumTracks()>0)
        {
            res_ids.add(R.string.context_menu_download);
        }

        return res_ids;
    }


    private String getTitleBarText()
    {
        String msg = "";
        if (selected.size() > 0)
        {
            msg = "" + selected.size() + " " + selected.getType() + " selected";
        }
        else
        {
            msg = "Playlist: ";
            String name = the_playlist.getName();
            if (!name.isEmpty())
                msg += name;
            if (the_playlist.isDirty())
                msg += "*";
            if (the_playlist.getNumTracks() > 0)
            {
                msg += "(";
                if (playlist_fetcher != null && playlist_fetcher.getNumRecords() < the_playlist.getNumTracks())
                    msg += playlist_fetcher.getNumRecords() + "/";
                msg += the_playlist.getNumTracks();
                msg += ")";
            }
        }
        return msg;
    }




    //---------------------------------------------
    // DOERS (called by playlistFileDialog)
    //---------------------------------------------

    public void setPlaylist(String name, boolean force)
    {
        playlistFileDialog dlg = new playlistFileDialog();
        if (force || !the_playlist.isDirty())
            do_setPlaylist(name);
        else
            dlg.setup(this,artisan,"set_playlist",name);
    }


    public void do_setPlaylist(String name)
    {
        artisan.setPlaylist(name);
    }



    //------------------------------------------
    // FetcherClient Interface
    //------------------------------------------

    @Override public void notifyFetchRecords(Fetcher fetcher, Fetcher.fetchResult fetch_result)
    {
        Utils.log(dbg_aplay,0,"aPlaylist.notifyFetchRecords(" + fetch_result + ") called with " + fetcher.getNumRecords() + " records");
        ((ListItemAdapter) the_list.getAdapter()).
            setItems(fetcher.getRecordsRef());
        updateTitleBar();
    }

    @Override public void notifyFetcherStop(Fetcher fetcher,Fetcher.fetcherState fetcher_state)
    {
        Utils.warning(0,0,"aPlaylist.notifyFetcherStop() not implemented yet");
        updateTitleBar();
    }


    //--------------------------------------------------------------
    // Artisan Event Handling
    //--------------------------------------------------------------

    @Override public void handleArtisanEvent(final String event_id,final Object data)
    {
        if (event_id.equals(EVENT_PLAYLIST_CHANGED))
            init(true);
        else if (event_id.equals(EVENT_PLAYLIST_CONTENT_CHANGED))
            init(false);
    }


}   // class aPlaylist
