package prh.artisan;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;

import prh.base.ArtisanEventHandler;
import prh.base.ArtisanPage;
import prh.base.EditablePlaylist;
import prh.base.Playlist;
import prh.base.PlaylistSource;
import prh.base.Renderer;
import prh.types.intList;
import prh.types.recordList;
import prh.types.selectedHash;
import prh.types.stringList;
import prh.utils.Utils;


// implements Playlist UI

public class aPlaylist extends Fragment implements
    ArtisanPage,
    ArtisanEventHandler,
    ListItem.ListItemListener,
    Fetcher.FetcherClient
{
    private static int dbg_aplay = 0;
    private static int FETCH_INITIAL = 20;
    private static int NUM_PER_FETCH = 60;

    private TextView page_title = null;
    private Artisan artisan = null;
    private LinearLayout my_view = null;
    private ListView the_list = null;
    private PlaylistFetcher playlist_fetcher = null;
    private EditablePlaylist the_playlist = null;

    private boolean album_mode = false;
    private boolean is_current = false;
    private selectedHash selected;
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
            selected = new selectedHash();
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

            adapter.notifyDataSetInvalidated();

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
        Utils.log(dbg_aplay,0,"aPlaylist.init(" + cold_init + ") finished");

    }   // init()


    //------------------------------------------------
    // Scrolling and Selection
    //------------------------------------------------

    @Override public ListItemAdapter getListItemAdapter()
    {
        return (ListItemAdapter) the_list.getAdapter();
    }



    @Override public void setSelected(Record record, boolean selected)
    {
        this.selected.setSelected(record,selected);
    }

    @Override public boolean getSelected(boolean for_displa, Record record)
    {
        return selected.getSelected(record);
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

    private void updateTitleBar()
    {
        if (is_current && page_title != null)
        {
            page_title.setText(getTitleBarText());
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
        String msg = "Playlist: ";
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
        return msg;
    }


    //--------------------------------------
    // playlistFileDialog
    //--------------------------------------

    private static enum dlgState
    {
        save,
        saveas,
        confirm_new,
        confirm_delete,
        confirm_overwrite,
        confirm_save,
        do_save,
        set_playlist
    };


    private class playlistFileDialog extends DialogFragment
        implements
            TextWatcher,
            View.OnClickListener,
            PopupMenu.OnMenuItemClickListener
    {
        private Artisan artisan;
        private aPlaylist parent;
        private String name_for_inflate;
        private dlgState state = dlgState.save;
        private View my_view;
        private TextView prompt;
        private RelativeLayout combo;
        private EditText value;
        private Button ok_button;
        String command;


        public void setup(aPlaylist a_playlist, Artisan ma, String what, String param)
        {
            artisan = ma;
            command = what;
            parent = a_playlist;
            name_for_inflate = a_playlist.the_playlist.getName();

            if (what.equals("set_playlist") && param.isEmpty())
                what = "new";

            if (what.equals("save") || what.equals("save_as"))
            {
                if (what.equals("save_as") ||
                    name_for_inflate.isEmpty())
                    state = dlgState.saveas;
            }
            else if (what.equals("delete"))
            {
                if (name_for_inflate.isEmpty())
                    return;     // bad call
                state = dlgState.confirm_delete;
            }
            else if (what.equals("new"))
            {
                state = dlgState.confirm_new;
            }

            // otherwise what is a playlist name for set_playlist

            else if (what.equals("set_playlist"))
            {
                Playlist exists = artisan.getPlaylistSource().getPlaylist(param);
                if (exists == null)
                {
                    Utils.error("Could not find playlist: " + param);
                    return;
                }
                if (!the_playlist.isDirty())
                {
                    parent.do_setPlaylist(param);
                    return;
                }
                name_for_inflate = param;
                state = dlgState.set_playlist;
            }
            show(artisan.getFragmentManager(),what);

        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState)
        {
            LayoutInflater inflater = artisan.getLayoutInflater();
            AlertDialog.Builder builder = new AlertDialog.Builder(artisan);
            my_view = inflater.inflate(R.layout.save_as_layout,null);

            my_view.findViewById(R.id.saveas_dialog_cancel).setOnClickListener(this);
            my_view.findViewById(R.id.saveas_dialog_dropdown_button).setOnClickListener(this);

            ok_button = (Button) my_view.findViewById(R.id.saveas_dialog_ok);
            ok_button.setOnClickListener(this);

            prompt = (TextView) my_view.findViewById(R.id.saveas_dialog_prompt);

            combo = (RelativeLayout) my_view.findViewById(R.id.saveas_dialog_combo);
            value = (EditText) my_view.findViewById(R.id.saveas_dialog_value);
            value.setText(name_for_inflate);
            value.addTextChangedListener(this);

            builder.setView(my_view);
            populate();
            return builder.create();
        }

        private void populate()
        {
            boolean enable_ok = true;
            combo.setVisibility(View.GONE);
            String new_name = value.getText().toString();

            if (state == dlgState.set_playlist ||
                state == dlgState.confirm_new)
            {
                prompt.setText("Discard changed playlist '" + new_name + "' ?");
            }
            else if (state == dlgState.confirm_delete)
            {
                prompt.setText("Delete playlist '" + new_name + "' ?");
            }
            else if (state == dlgState.confirm_overwrite)
            {
                prompt.setText("Overwrite existing playlist '" + new_name + "' ?");
            }
            else if (state == dlgState.saveas)
            {
                prompt.setText("Save As");
                combo.setVisibility(View.VISIBLE);
                enable_ok = !value.getText().toString().isEmpty();
            }
            else  // state MUST be save
            {
                prompt.setText("Save playlist '" + new_name + "' ?");
            }
            ok_button.setEnabled(enable_ok);
        }


        // TextWatcher

        @Override public void afterTextChanged(Editable s)
        {
            String name = s.toString();
            ok_button.setEnabled(!name.isEmpty());
        }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after)
        {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count)
        {}


        // View.OnClickListener

        @Override public void onClick(View v)
        {
            PlaylistSource source = artisan.getPlaylistSource();
            if (v.getId() == R.id.saveas_dialog_dropdown_button)
            {
                PopupMenu drop_down = new PopupMenu(artisan,v);
                drop_down.setOnMenuItemClickListener(this);
                stringList pl_names = source.getPlaylistNames();
                Menu menu = drop_down.getMenu();
                for (int i=0; i<pl_names.size(); i++)
                {
                    String name = pl_names.get(i);
                    menu.add(0,i,0,name);
                }
                drop_down.show();
            }
            else if (v.getId() == R.id.saveas_dialog_ok)
            {
                String new_name = value.getText().toString();
                if (state == dlgState.saveas && !new_name.isEmpty())
                {
                    if (source.getPlaylist(new_name) != null)
                        state = dlgState.confirm_overwrite;
                    else
                        state = dlgState.do_save;
                }
                else if (state == dlgState.save ||
                         state == dlgState.confirm_overwrite)
                {
                    state = dlgState.do_save;
                }
                if (state == dlgState.do_save)
                {
                    String name = value.getText().toString();
                    if (artisan.getLocalPlaylistSource().saveAs(
                        artisan.getCurrentPlaylist(),name))
                        artisan.getCurrentPlaylist().setName(name);
                    parent.updateTitleBar();
                    this.dismiss();
                }
                else if (state == dlgState.confirm_new)
                {
                    parent.do_setPlaylist("");
                    this.dismiss();
                }
                else if (state == dlgState.confirm_delete)
                {
                    //artisan.getCurrentPlaylist().setAssociatedPlaylist(null);
                    source.deletePlaylist(new_name);
                    parent.do_setPlaylist("");
                    this.dismiss();
                }
                else if (state == dlgState.set_playlist)
                {
                    parent.do_setPlaylist(new_name);
                    this.dismiss();
                }
                else
                {
                    populate();
                }
            }
            else
                this.dismiss();
        }


        // PopupMenu.OnMenuItemClickListener

        @Override public boolean onMenuItemClick(MenuItem item)
            // clicked on a button in the dropdown for saveas playlist name
            // set the edit_text to the playlist name
        {
            int id = item.getItemId();
            stringList names = artisan.getPlaylistSource().getPlaylistNames();
            value.setText(names.get(id));
            return true;
        }


    }   // class saveAsDialog



    //-------------------------------
    // DOERS
    //-------------------------------

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


    //--------------------------------------------------------------
    // onClick()
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
