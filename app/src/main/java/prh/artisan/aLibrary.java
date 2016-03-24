package prh.artisan;

// The Library UI presents a hierarchy of folders, albums, and tracks
// upon which various operations can take place.
//
// The UI maintains records for each element in the hierarchy that it visits,
// that contain only the ID and type of the item, along with control members
// like whether or not that node has been VISITED or is OPEN in a tree view.
//
// All caching of other information (i.e. remote folders and tracks to
// prevent hitting the net) takes place in the underlying Library object.
// The UI does not cache any metadata.
//
// An image cache needs to be slipped in underneath this, but initially
// is implemented to get the images from the urls every time.
//
// Knows if the library isLocal (isArtisan) and can present additional
// metadata (i.e. error codes and icons) in the views for Artisan libraries.
//
// Clicking a single song plays it over the existing playlist, if any.
// Handles multiple selection (long press) with a context menu that includes
// a TrashCan (delete), a PlusSign (add to playlist), and a Play button
// (create a new empty playlist, add the albums and tracks, and play them)
//
// The aPlaying header shows the current library, and the path
// to the current item. Perhaps there will be an option or heuristic
// to only display the leaf folder node in the header.
//
//-------------------------------------------------------------------
// Stack (Back / Dismiss / Home)
//-------------------------------------------------------------------
//
// The default view mode is LIST_VIEW, wherein, each folder is presented
// as a single list, and as you go deeper into the hiearchy, levels are
// added to the Stack, and one uses the Back (or Home) buttons to traverse
// back down the stack.  In LIST_VIEW when the stack is popped, the memory
// is released, and the items re-built upon the next visit. Every folder
// viewed in LIST_VIEW is considered to be OPEN.
//
// There is a TREE_VIEW wherein all folders, except for albums, are presented
// in a single tree, and when you click on an album, you get the list view
// for the album, so the Stack is at most 2 levels deep in TREE_VIEW. In
// TREE_VIEW all visited items are kept in memory, and they can be OPENED
// or not.
//
// One can switch between TREE_VIEW and LIST_VIEW and the Stack will be
// corrected (and pruned when going to LIST_VIEW).
//
// Since the items that appear in the list are also used in aPlaylist,
// the list items are all implemented in separate java files.

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import prh.device.Device;
import prh.device.MediaServer;
import prh.types.intViewHash;
import prh.types.recordList;
import prh.types.viewList;
import prh.utils.Utils;


public class aLibrary extends Fragment implements
    ArtisanPage,
    EventHandler,
    View.OnClickListener,
    View.OnLongClickListener
{
    private static int dbg_alib = 0;

    private boolean USE_VIEW_CACHE = false;
        // If true, the libraryListAdapter keeps a cache of the
        // views it has created.  Has to be false right now because,
        // apparently view recycling is invalidating the onClick()
        // pointers and everything goes to hell when I try to cache em.
        // Similar problem occurs in the MainMenu, for which I just
        // resorted to keeping it around all the time.
        //
        // Because of this, at a minimum, I must keep a scroll position
        // in the stack of views, so that when a user returns to a view,
        // it shows the same stuff (we at least don't rebuild the adapter)

    @Override public String getName()  { return "Library"; }

    class ViewStack extends ArrayList<viewStackElement> {}

    private Artisan artisan = null;
    private Library library = null;
    private LinearLayout my_view = null;
    private ViewStack view_stack = null;
        // The view_stack needs to be of objects that have a view
        // along with an integer for the top record showing in the view,
        // so that the scroll position can be reset on popping when
        // !USE_VIEW_CACHE

    public Artisan getArtisan()  { return artisan; }
    public Library getLibrary()  { return library; }
    // public LinearLayout getMyView()  { return my_view; }


    private class viewStackElement
    {
        private View view;
        private int scroll_position = -1;

        public viewStackElement(View v) {view = v;}
        public View getView() {return view;}

        public void setScrollPosition(int p) { scroll_position = p;}
        public int getScrollPosition() { return scroll_position; }

        public ListView getListView()
        {
            if (view instanceof ListView)
                return (ListView) view;
            return (ListView) view.findViewById(R.id.library_list);
        }

        public libraryListAdapter getAdapter()
        {
            ListView list_view = getListView();
            libraryListAdapter adapter = (libraryListAdapter) list_view.getAdapter();
            return adapter;
        }

        public Folder getFolder()
        {
            return getAdapter().getFolder();
        }
    }


    //----------------------------------------------
    // life cycle
    //----------------------------------------------

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
        artisan = (Artisan) activity;
    }


    @Override
    public void onDetach()
    {
        Utils.log(dbg_alib,0,"aLibrary.onDetach() called");
        super.onDetach();
        artisan = null;
    }


    @Override
    public void onDestroy()
    {
        view_stack.clear();
        view_stack = null;
        library = null;
        super.onDestroy();
    }


    //--------------------------------------------------------------
    // Traversal
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
            library.setCurrentFolder(stack_ele.getFolder());
        }
    }


    private void pushViewStack(String id)
    {
        Folder folder = library.getFolder(id);
        boolean is_album = folder.getType().equals("album");
        library.setCurrentFolder(folder);

        // inflate a new list/adapter for the children of the folder

        LayoutInflater inflater = LayoutInflater.from(artisan);
        ListView list_view = (ListView) inflater.inflate(R.layout.library_list,null,false);

        int count = ((Device) library).isLocal() ? 999999 : 0;
        recordList initial_items = library.getSubItems(folder.getId(),0,count,false);
        libraryListAdapter adapter = new libraryListAdapter(this,folder,initial_items);

        list_view.setAdapter(adapter);
        list_view.setItemsCanFocus(true);

        // add the album header

        View main_view = list_view;
        if (is_album)
        {
            main_view = inflater.inflate(R.layout.library_album,null,false);
            ListItem header_view = (ListItem) inflater.inflate(R.layout.list_item_layout,null,false);
            header_view.setFolder(folder);
            header_view.setLargeView();
            header_view.doLayout(this,this);
            ((LinearLayout)main_view).addView(header_view);
            ((LinearLayout)main_view).addView(list_view);
        }

        my_view.removeAllViews();
        my_view.addView(main_view);
        view_stack.add(new viewStackElement(main_view));
    }



    private class libraryListAdapter extends ArrayAdapter<Record>
        // only called for folders ..
        // there are no subitems of tracks.
        //
        // Calls getSubItems(0,999999) for local folders, and
        // getSubItems(0,0) for external folders. Local usage
        // is not complicated, so what follows is only for external
        // usage in conjunction with Device.MediaServer.
        //
        // The initial call to getSubItems(0,0) will happen
        // synchronously, so the adapter can start displaying views.
        // The adapter only sees the records that have been found
        // by the MediaServer.
        //
        // Meanwhile, the MediaServer plugs away and sends EVENT_ADDL_FOLDERS_AVAILABLE
        // to us when it gets some records, in which case we add them to our array,
        // and notify the adapter that the dataset has changed, and away the user
        // goes, scrolling like mad.
    {
        private Folder folder;
        private aLibrary a_library;
        private int num_items = 0;
        private recordList sub_items;
        private intViewHash items = new intViewHash();
        public Folder getFolder()  { return folder; }


        public libraryListAdapter(aLibrary the_a_library, Folder the_folder, recordList initial_records)
        {
            super(artisan,-1,initial_records);
            a_library = the_a_library;
            folder = the_folder;
            sub_items = initial_records;
            num_items = sub_items.size();
            //hasStableIds();
        }

        /*

        public int getCount()
        {
            return num_items;
        }

        public long getItemId(int position)
        {
            return position;
        }

        public View getItem(int position)
        {
            return getView(position,null,null);     // items.get(position);
        }

        */

        public View getView(int position, View re_use, ViewGroup view_group)
        {
            View item = items.get(position);
            if (item == null)
            {
                Record rec = sub_items.get(position);
                LayoutInflater inflater = LayoutInflater.from(artisan);
                ListItem list_item = (ListItem) inflater.inflate(R.layout.list_item_layout,view_group,false);
                if (rec instanceof Track)
                    list_item.setTrack((Track) rec);
                else
                    list_item.setFolder((Folder) rec);

                // OnClickHandlers are set in doLayout()

                list_item.doLayout(a_library,a_library);
                item = list_item;

                // If I attempt to cache these, I lose the click listener

                if (USE_VIEW_CACHE)
                    items.put(position,item);
           }
           return item;
        }


        // called by ARTISAN_EVENT when MediaServer adds more
        // subitems to this adapter's folder ...

        public void addItems(MediaServer.FolderPlus folder_plus)
        {
            recordList new_records = folder_plus.getRecords();
            int first = sub_items.size();
            int last = new_records.size() - 1;
            if (last >= first)
            {
                Utils.log(0,4,"adding " + (last-first+1) + " items to existing " + first + " records");

                for (int i = first; i <= last; i++)
                    sub_items.add(new_records.get(i));

                Utils.log(0,4,"changing num_items to " + sub_items.size() + " and calling notify DataSet changed()");
                num_items = sub_items.size();
                notifyDataSetChanged();
                //notifyDataSetInvalidated();

            }
        }



        // implementing these two methods disables
        // view recycling by the ListView which is supposed
        // to allow the cached items to work, by telling the
        // list view that all objects are of unique types and
        // cannot be recycled. It don't (work).

        /*
        @Override
        public int getViewTypeCount()
        {
            return getCount();
        }

        @Override
        public int getItemViewType(int position)
        {
            return position;
        }

        */

    }   // class libraryListAdapter




    private class old_libraryListAdapter extends BaseAdapter
        // only called for folders ..
        // there are no subitems of tracks.
        //
        // Calls getSubItems(0,999999) for local folders, and
        // getSubItems(0,0) for external folders. Local usage
        // is not complicated, so what follows is only for external
        // usage in conjunction with Device.MediaServer.
        //
        // The initial call to getSubItems(0,0) will happen
        // synchronously, so the adapter can start displaying views.
        // The adapter only sees the records that have been found
        // by the MediaServer.
        //
        // Meanwhile, the MediaServer plugs away and sends EVENT_ADDL_FOLDERS_AVAILABLE
        // to us when it gets some records, in which case we add them to our array,
        // and notify the adapter that the dataset has changed, and away the user
        // goes, scrolling like mad.
    {
        private Folder folder;
        private aLibrary a_library;
        private int num_items = 0;
        private recordList sub_items;
        private intViewHash items = new intViewHash();

        public Folder getFolder()  { return folder; }


        public old_libraryListAdapter(aLibrary the_a_library, Folder the_folder)
        {
            super();
            a_library = the_a_library;
            folder = the_folder;
            int count = ((Device) library).isLocal() ? 999999 : 0;
            sub_items = a_library.getLibrary().getSubItems(folder.getId(),0,count,false);
            num_items = sub_items.size();
            hasStableIds();
        }

        public int getCount()
        {
            return num_items;
        }

        public long getItemId(int position)
        {
            return position;
        }

        public View getItem(int position)
        {
            return getView(position,null,null);     // items.get(position);
        }

        public View getView(int position, View re_use, ViewGroup view_group)
        {
            View item = items.get(position);
            if (item == null)
            {
                Record rec = sub_items.get(position);
                LayoutInflater inflater = LayoutInflater.from(artisan);
                ListItem list_item = (ListItem) inflater.inflate(R.layout.list_item_layout,view_group,false);
                if (rec instanceof Track)
                    list_item.setTrack((Track) rec);
                else
                    list_item.setFolder((Folder) rec);

                // OnClickHandlers are set in doLayout()

                list_item.doLayout(a_library,a_library);
                item = list_item;

                // If I attempt to cache these, I lose the click listener

                if (USE_VIEW_CACHE)
                    items.put(position,item);
            }
            return item;
        }


        // called by ARTISAN_EVENT when MediaServer adds more
        // subitems to this adapter's folder ...

        public void addItems(MediaServer.FolderPlus folder_plus)
        {
            recordList new_records = folder_plus.getRecords();
            int first = sub_items.size();
            int last = new_records.size() - 1;
            if (last >= first)
            {
                Utils.log(0,4,"adding " + (last-first+1) + " items to existing " + first + " records");

                for (int i = first; i <= last; i++)
                    sub_items.add(new_records.get(i));

                Utils.log(0,4,"changing num_items to " + sub_items.size() + " and calling notify DataSet changed()");
                num_items = sub_items.size();
                notifyDataSetChanged();
                //notifyDataSetInvalidated();

            }
        }

        // implementing these two methods disables
        // view recycling by the ListView which is supposed
        // to allow the cached items to work, by telling the
        // list view that all objects are of unique types and
        // cannot be recycled. It don't (work).


        @Override
        public int getViewTypeCount()
        {
            return getCount();
        }

        @Override
        public int getItemViewType(int position)
        {
            return position;
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
                list_item = (ListItem) v;
                Folder folder = list_item.getFolder();
                if (folder != null)
                    pushViewStack(folder.getId());
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
    // EVENT_LIBRARY_CHANGED


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
        else if (event_id.equals(EVENT_ADDL_FOLDERS_AVAILABLE))
        {
            // Assumes that if we get this event, we are (still) hooked to
            // a remote MediaServer.  If not, the folders wont match ..
            // should only be for the TOS but we need to add
            // any for any other folders in the stack, in case
            // it's a lingering call.

            MediaServer.FolderPlus event_folder = (MediaServer.FolderPlus) data;
            for (int stack_pos = view_stack.size() - 1; stack_pos >= 0; stack_pos--)
            {
                viewStackElement stack_ele = view_stack.get(stack_pos);
                Folder folder = stack_ele.getFolder();
                MediaServer.FolderPlus stack_folder = (folder instanceof MediaServer.FolderPlus) ?
                    (MediaServer.FolderPlus) folder : null;

                if (stack_folder != null && stack_folder.equals(event_folder))
                {
                    stack_ele.getAdapter().addItems(event_folder);
                    return;
                }
            }
        }
    }


}   // class aLibrary
