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
// The aPlaying header shows the current library, and the title of
// the current folder as a drop down menu which allows navigation
// within the stack.


import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.util.ArrayList;

import prh.device.Device;
import prh.device.MediaServer;
import prh.types.recordList;
import prh.utils.Fetcher;
import prh.utils.Utils;


public class aLibrary extends Fragment implements
    ArtisanPage,
    EventHandler,
    ListItem.ListItemListener,
    Fetcher.FetcherClient
{
    private static int dbg_alib = 1;

    class ViewStack extends ArrayList<viewStackElement> {}

    private Artisan artisan = null;
    private Library library = null;
    private TextView page_title = null;
    private LinearLayout my_view = null;
    private ViewStack view_stack = null;

   //  public Artisan getArtisan()  { return artisan; }
    public Library getLibrary()  { return library; }


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

        init(artisan.getLibrary());
        return my_view;
    }


    private void init(Library lib)
    {
        library = lib;
        view_stack = new ViewStack();
        my_view.removeAllViews();
        if (library != null)
            pushViewStack("0");
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
        view_stack.clear();
        view_stack = null;
        library = null;
        super.onDestroy();
    }



    //--------------------
    // viewStackElement
    //--------------------

    private class viewStackElement
    {
        private View view;
        private int scroll_index = 0;
        private int scroll_position = 0;

        public viewStackElement(View v) {view = v;}
        public View getView() {return view;}


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

        public Folder getFolder()
        {
            return getAdapter().getFolder();
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
    }


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
            toolbar.enableButton(R.id.command_back,false);

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
            library.getName();

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
        if (view_stack.size() > 1)
        {
            my_view.removeAllViews();
            view_stack.remove(view_stack.size() - 1);
            viewStackElement stack_ele = view_stack.get(view_stack.size() - 1);
            my_view.addView(stack_ele.getView());
            stack_ele.restoreScroll();

            library.setCurrentFolder(stack_ele.getFolder());
            artisan.getToolbar().enableButton(R.id.command_back,
                view_stack.size() > 1);
            updateTitleBar();
        }
    }


    private void pushViewStack(String id)
    {
        Folder folder = library.getFolder(id);
        boolean is_album = folder.getType().equals("album");
        library.setCurrentFolder(folder);

        // inflate a new list/adapter for this parent folder

        LayoutInflater inflater = LayoutInflater.from(artisan);
        ListView list_view = (ListView) inflater.inflate(R.layout.library_list,null,false);

        int count = ((Device) library).isLocal() ? 999999 : 0;
        recordList initial_items = library.getSubItems(folder.getId(),0,count,false);
        ListItemAdapter adapter = new ListItemAdapter(
            artisan,
            this,
            list_view,
            folder,
            initial_items,
            false,
            false);
        list_view.setAdapter(adapter);

        // if it's an album, we wrap the list_view in a linear layout
        // to contain a fixed (album) header ListItem

        View main_view = list_view;
        if (is_album)
        {
            main_view = inflater.inflate(R.layout.library_album,null,false);
            ListItem header_view = (ListItem) inflater.inflate(R.layout.list_item_layout,null,false);
            header_view.setFolder(folder);
            header_view.setLargeView();
            header_view.doLayout(this);
            ((LinearLayout)main_view).addView(header_view);
            ((LinearLayout)main_view).addView(list_view);
        }

        // save the scroll position

        if (view_stack.size()>0)
        {
            viewStackElement tos = view_stack.get(view_stack.size() - 1);
            tos.saveScroll();
        }

        // add the view to the page

        my_view.removeAllViews();
        my_view.addView(main_view);
        view_stack.add(new viewStackElement(main_view));

        artisan.getToolbar().enableButton(R.id.command_back,
            view_stack.size() > 1);
        updateTitleBar();

    }   // pushViewStack()


    //--------------------------------------------------------------
    // onClick()
    //--------------------------------------------------------------

    @Override public void onClick(View v)
        // Navigate to Folder, or Play Track
        // Contezt menu handled by item itself.
        // This called by ListItem.onClick()
    {
        int id = v.getId();
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
        return false;
    }




    //---------------------------------------------------------------------
    // FetcherClient interface from device.MediaServer via Artisan
    //---------------------------------------------------------------------

    @Override public void notifyFetchRecords(Fetcher fetcher,Fetcher.fetchResult fetch_result)
    {
        MediaServer.FolderPlus event_folder = (MediaServer.FolderPlus) fetcher.getSource();
        Utils.log(dbg_alib,0,"aLibrary.notifyFetchRecords(" + fetch_result + ") called with fetcher.source=" +
            "FolderPlus(" + event_folder.getTitle() + ")");
        for (int stack_pos = view_stack.size() - 1; stack_pos >= 0; stack_pos--)
        {
            viewStackElement stack_ele = view_stack.get(stack_pos);
            Folder folder = stack_ele.getFolder();
            MediaServer.FolderPlus stack_folder = (folder instanceof MediaServer.FolderPlus) ?
                (MediaServer.FolderPlus) folder : null;

            if (stack_folder != null && stack_folder.equals(event_folder))
            {
                recordList records = fetcher.getRecordsRef();
                Utils.log(dbg_alib,1,"aLibrary.notifyFetchRecords() calling adapter.setItems(" +
                    records.size() + ") for the stack_folder(" + stack_folder.getTitle() + ")");
                stack_ele.getAdapter().setItems(records);
                return;
            }
        }
    }

    @Override public void notifyFetcherStop(Fetcher fetcher,Fetcher.fetcherState fetcher_state)
    {
        Utils.error("aLibrary.notifyFetcherStop() not implemented yet");
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
                !new_library.getName().equals(library.getName()))
            {
                init(new_library);
            }
        }
        else if (true)
            return; // for now



        else if (event_id.equals(EVENT_ADDL_FOLDERS_AVAILABLE))
        {
            // Find the adapter, if any, for the FolderPlus to
            // which additional folders have been added (by MediaServer),
            // and reset its list of records.

            MediaServer.FolderPlus event_folder = (MediaServer.FolderPlus) data;
            for (int stack_pos = view_stack.size() - 1; stack_pos >= 0; stack_pos--)
            {
                viewStackElement stack_ele = view_stack.get(stack_pos);
                Folder folder = stack_ele.getFolder();
                MediaServer.FolderPlus stack_folder = (folder instanceof MediaServer.FolderPlus) ?
                    (MediaServer.FolderPlus) folder : null;

                if (stack_folder != null && stack_folder.equals(event_folder))
                {
                    stack_ele.getAdapter().setItems(event_folder.getRecords());
                    return;
                }
            }
        }
    }


}   // class aLibrary
