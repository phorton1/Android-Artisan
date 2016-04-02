package prh.artisan;

// The Library UI presents a hierarchy of folders, albums, and tracks
// upon which various operations can take place.
//
// The UI maintains a stack of viewStackElements (views) that
// gets pushed as the user traverses, and popped as they go back.
//
// Works closely with Library (LocalLibrary and device.MediaServer)
// to orchestrate pre-fetching of remote subItem lists.
//
// Knows if the library isLocal and calls it with count=999999 to
// get all the records immediately, but uses special count=0 flat
// to initiate pre-fetch scheme in MediaServer.
//
// Clicking a single song plays it over the existing playlist, if any.
//
// Handles multiple selection (long press) with a context menu that includes
// a TrashCan (delete), a PlusSign (add to playlist), and a Play button
// (create a new empty playlist, add the albums and tracks, and play them)
//
// The aRenderer header shows the current library, and the title of
// the current folder as a drop down menu which allows navigation
// within the stack.


import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

import prh.base.ArtisanEventHandler;
import prh.base.ArtisanPage;
import prh.base.Library;
import prh.types.intList;
import prh.types.recordList;
import prh.types.selectedHash;
import prh.utils.Utils;


public class aLibrary extends Fragment implements
    ArtisanPage,
    ArtisanEventHandler,
    ListItem.ListItemListener
{
    private static int dbg_alib = 0;

    // constants for LOCAL LIBRARY

    private static int NUM_PER_FETCH = 300;
    private static int NUM_INITIAL_FETCH = 100;
    class ViewStack extends ArrayList<viewStackElement> {}

    // These are not reset by view recycling
    // They are created once and left dangling on program terminatino
    // The views themselves are invalidated in onDestroy()

    private Artisan artisan = null;
    private Library library = null;
    private ViewStack view_stack = null;
    selectedHash selected = new selectedHash();

    Folder root_folder;

        // one level of selection

    // Due to view recycling, we cannot assume that
    // these are constant over the lifetime of the object.
    // In order to implement a persistent view, we have
    // to restore all of the state

    private TextView page_title = null;
    private LinearLayout my_view = null;


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
        Utils.log(dbg_alib,0,"aLibrary.onCreateView() called");
        my_view = (LinearLayout) inflater.inflate(R.layout.activity_library, container, false);
        if (view_stack == null || library != artisan.getLibrary())
        {
            view_stack = new ViewStack();
            library = artisan.getLibrary();
            init(library);
        }
        else
            reInflate();

        Utils.log(dbg_alib,0,"aLibrary.onCreateView() finished");
        return my_view;
    }


    private void init(Library lib)
    {
        Utils.log(dbg_alib,0,"aLibrary.init() called");

        library = lib;
        selected.clear();
        my_view.removeAllViews();
        for (int i=0; i<view_stack.size(); i++)
            view_stack.get(i).stop();
        view_stack.clear();
        if (library != null)
        {
            root_folder = library.getRootFolder();
            pushViewStack("0");
        }
        Utils.log(dbg_alib,0,"aLibrary.init() finished");
    }



    private void reInflate()
    // We have been view-recycled, but the library,
    // and folders may still be current.
    {
        for (int i=0; i<view_stack.size(); i++)
        {
            view_stack.get(i).inflate();
        }
        viewStackElement stack_element = view_stack.get(view_stack.size() - 1);
        showView(stack_element,true,true);
    }



    @Override
    public void onAttach(Activity activity)
    {
        Utils.log(dbg_alib,0,"aLibrary.onAttach() called");
        super.onAttach(activity);
    }


    @Override
    public void onDetach()
    {
        Utils.log(dbg_alib,0,"aLibrary.onDetach() called");
        super.onDetach();
    }


    @Override
    public void onDestroy()
    {
        super.onDestroy();
        Utils.log(dbg_alib,0,"aLibrary.onDestroy() called");

        // as a precaution we pause any fetchers in progress
        // and disassociate ourself from them ...

        for (viewStackElement stack_element:view_stack)
        {
            stack_element.pause();
            if (stack_element.getFolder().getFetcher() == null)
                // && blah.getFetcher().getClient() == this
                stack_element.getFetcher().setClient(null);
        }

        // We keep the stack and library around forever
        // view_stack.clear();
        // view_stack = null;
        // library = null;
        Utils.log(dbg_alib,0,"aLibrary.onDestroy() finished");
    }



    //--------------------
    // viewStackElement
    //--------------------

    private class viewStackElement implements Fetcher.FetcherClient
    {
        private View view = null;
        private int scroll_index = 0;
        private int scroll_position = 0;
        private Folder folder;
        private Fetcher fetcher;
        boolean our_fetcher = false;

        public viewStackElement(Folder folder)
        {
            this.folder = folder;

            // Create a fetcher if its local.
            // Otherwise associate ourself with the fetcher

            fetcher = folder.getFetcher();
            if (fetcher == null)
            {
                our_fetcher = true;
                fetcher = new Fetcher(
                    artisan,
                    folder,
                    this,
                    NUM_INITIAL_FETCH,
                    NUM_PER_FETCH,
                    "viewStack(" + folder.getTitle() + ")");
            }
            else
                fetcher.setClient(this);
        }


        public Folder getFolder()
        {
            return folder;
        }
        public Fetcher getFetcher()
        {
            return fetcher;
        }
        public View getView()
        {
            return view;
        }
        public void setView(View v)
        {
            view=v;
        }


        public boolean start()
        {
            return fetcher.start();
        }
        public void stop()      // coming down the stack
        {
            if (our_fetcher)
                fetcher.stop(true,false);
            else
            {
                fetcher.pause(false);
                // if (fetcher.getClient() == this)
                fetcher.setClient(null);
            }
        }
        public void pause()     // going up the stack, onDestroy (view cycling)
        {
           fetcher.pause(false);
        }


        public ListView getListView()
        {
            if (view instanceof ListView)
                return (ListView) view;
            return (ListView) view.findViewById(R.id.library_list);
        }

        public ListItemAdapter getAdapter()
        {
            ListView list_view = getListView();
            ListItemAdapter adapter = (ListItemAdapter) list_view.getAdapter();
            return adapter;
        }

        public void saveScroll()
        {
            ListView list_view = getListView();
            scroll_index = list_view.getFirstVisiblePosition();
            View v = list_view.getChildAt(0);
            scroll_position = (v == null) ? 0 : (v.getTop() - list_view.getPaddingTop());
        }

        public void restoreScroll()
        {
            ListView list_view = getListView();
            list_view.setSelectionFromTop(scroll_index,scroll_position);
        }

        public void inflate()
        {
            boolean is_album = folder.getType().equals("album");

            // create adapter

            LayoutInflater inflater = LayoutInflater.from(artisan);
            ListView list_view = (ListView) inflater.inflate(R.layout.library_list,null,false);

            // int count = ((Device) library).isLocal() ? 999999 : 0;
            // recordList initial_items = library.getSubItems(folder.getId(),0,count,false);

            ListItemAdapter adapter = new ListItemAdapter(
                artisan,
                aLibrary.this,
                list_view,
                folder,
                fetcher.getRecordsRef(),
                false,
                false);
            list_view.setAdapter(adapter);

            // if it's an album, we wrap the list_view in a linear layout
            // to contain a fixed (album) header ListItem

            view = list_view;
            if (is_album)
            {
                view = inflater.inflate(R.layout.library_album,null,false);
                ListItem header_view = (ListItem) inflater.inflate(R.layout.list_item_layout,null,false);
                header_view.setFolder(folder);
                header_view.setLargeView();
                header_view.doLayout(aLibrary.this);
                ((LinearLayout)view).addView(header_view);
                ((LinearLayout)view).addView(list_view);
            }
        }


        //------------------------------------------
        // viewStack FetcherClient interface
        //------------------------------------------

        @Override public void notifyFetchRecords(Fetcher fetcher,Fetcher.fetchResult fetch_result)
            // The library adapter uses the underlying array of records
            // from the fetcher directly by reference.
        {
            Utils.log(dbg_alib,0,"viewStack.notifyFetchRecords(" + fetch_result + "," + fetcher.getTitle() + ")");
            recordList records = fetcher.getRecordsRef();
            getAdapter().setItems(records);
        }

        @Override public void notifyFetcherStop(Fetcher fetcher,Fetcher.fetcherState fetcher_state)
        {
            Utils.warning(0,0,"viewStack.notifyFetcherStop(" + fetcher.getTitle() + ")");
            getAdapter().setItems(new recordList());
            ListView list_view = (ListView) getView();
            list_view.deferNotifyDataSetChanged();
        }


    }   // class viewStackElement



    //--------------------------------------------
    // utilities
    //--------------------------------------------

    @Override public void onSetPageCurrent(boolean current)
    {
        page_title = null;
        if (current)
        {
            page_title = new TextView(artisan);
            page_title.setText(getTitleBarText());
            artisan.setArtisanPageTitle(page_title);

            // set the main toolbar buttons for this ativity

            MainMenuToolbar toolbar = artisan.getToolbar();
            toolbar.showButtons(new int[]{
                R.id.command_back});
            toolbar.enableButton(R.id.command_back,
                view_stack != null &&
                view_stack.size() > 1);
        }
    }

    private void updateTitleBar()
    {
        if (page_title != null)
            page_title.setText(getTitleBarText());
    }


    private String getTitleBarText()
    {
        String msg = library == null ?
            "Library" :
            library.getLibraryName();

        if (view_stack != null && view_stack.size() > 1)
        {
            for (int i=1; i<view_stack.size(); i++)
            {
                Folder folder = view_stack.get(i).getFolder();
                if (!folder.getType().equals("album"))
                {
                    msg += "/";
                    msg += folder.getTitle();
                }
            }
        }
        return msg;
    }


    //--------------------------------------------------------------
    // viewStack
    //--------------------------------------------------------------
    // Start by populating a list with the root items returned
    // by library.getSubItems("0").  Thereafter, new views are
    // pushed on the stack with their ids.  The traversal only
    // does folders (including albums).

    public void doBack()
    {
        Utils.log(dbg_alib,0,"aLibrary.doBack() called");
        selected.clear();

        if (view_stack.size() > 1)
        {
            viewStackElement tos = view_stack.get( view_stack.size() - 1);
            tos.pause(); // leave it in memory
            view_stack.remove(view_stack.size() - 1);

            viewStackElement stack_element = view_stack.get(view_stack.size() - 1);
            stack_element.start();
            showView(stack_element,true,true);
        }
        Utils.log(dbg_alib,0,"aLibrary.doBack() finished");
    }


    private void pushViewStack(String id)
    {
        Utils.log(dbg_alib,0,"aLibrary.pushViewStack(" + id + ") called");

        selected.clear();
        int stack_size = view_stack.size();
        viewStackElement tos = stack_size > 0 ?
            view_stack.get( stack_size - 1) :
            null;
        if (tos != null)
            tos.pause();

        Folder folder = tos == null ?
            root_folder :
            findChildFolder(tos.getFetcher(),id);

        Utils.log(dbg_alib,1,"aLibrary.pushViewStack(" + id + ") folder=" + folder.getTitle());

        if (folder != null)
        {
            viewStackElement stack_element = new viewStackElement(folder);
            stack_element.start();
            stack_element.inflate();
            if (tos != null)
                tos.saveScroll();
            view_stack.add(stack_element);
            showView(stack_element,true,false);
        }
        else
        {
            Utils.error("No folder found in pushViewStack(" + id + ")");
        }

        Utils.log(dbg_alib,0,"aLibrary.pushViewStack(" + id + ") finished");

    }   // pushViewStack()



    public Folder findChildFolder(Fetcher parent_fetcher, String id)
    {
        recordList records = parent_fetcher.getRecordsRef();
        for (int i=0; i<records.size(); i++)
        {
            Record record = records.get(i);
            if (record instanceof Folder &&
                ((Folder)record).getId().equals(id))
                return ((Folder)record);
        }
        return null;
    }


    private void showView(viewStackElement stack_element, boolean last_one, boolean restore_scroll)
        // last_one means to show the page title, and set this folder
        // as the current library folder.
    {
        my_view.removeAllViews();
        my_view.addView(stack_element.getView());
        if (restore_scroll)
            stack_element.restoreScroll();
        if (last_one)
        {
            artisan.getToolbar().enableButton(
                R.id.command_back,
                view_stack.size() > 1);
            updateTitleBar();
        }
    }


    //--------------------------------------------------------------
    // onClick()
    //--------------------------------------------------------------
    // includes ListItemListener interface


    @Override public void onClick(View v)
        // Navigate to Folder, or Play Track
        // Contezt menu handled by item itself.
        // This called by ListItem.onClick()
    {
        int id = v.getId();
        Utils.log(dbg_alib,0,"aLibrary.onClick( " + id + ")");

        switch (id)
        {
            case R.id.list_item_layout:
                ListItem list_item = (ListItem) v;

                // one or the other ...

                Folder folder = list_item.getFolder();
                if (folder != null)
                    pushViewStack(folder.getId());

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
        Utils.log(dbg_alib,0,"aLibrary.onLongClick( " + v.getId() + ")");
        return false;
    }


    @Override public void setSelected(Record record, boolean selected)
    {
        this.selected.setSelected(record,selected);
    }

    @Override public boolean getSelected(Record record)
    {
        return this.selected.getSelected(record);
    }


    @Override public boolean onMenuItemClick(MenuItem item)
    {
        return true;
    }

    @Override public intList getContextMenuIds()
    {
        return new intList();
    }


    //--------------------------------------------------------------
    // Artisan Event Handling
    //--------------------------------------------------------------

    @Override public void handleArtisanEvent(final String event_id,final Object data)
    {
        if (event_id.equals(EVENT_LIBRARY_CHANGED))
        {
            Library new_library = (Library) data;
            if (new_library == null || library == null ||
                !new_library.getLibraryName().equals(library.getLibraryName()))
            {
                init(new_library);
            }
        }
    }


}   // class aLibrary
