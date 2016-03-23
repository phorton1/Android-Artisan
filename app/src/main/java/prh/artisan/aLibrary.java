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
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.util.List;

import prh.device.Device;
import prh.types.intViewHash;
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

    private Artisan artisan = null;
    private Library library = null;
    private LinearLayout my_view = null;
    private viewList view_stack = null;
        // The view_stack needs to be of objects that have a view
        // along with an integer for the top record showing in the view,
        // so that the scroll position can be reset on popping when
        // !USE_VIEW_CACHE

    public Artisan getArtisan()  { return artisan; }
    public Library getLibrary()  { return library; }
    // public LinearLayout getMyView()  { return my_view; }


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
        view_stack = new viewList();
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
            view_stack.remove(view_stack.size() - 1);
            View view = view_stack.get(view_stack.size()-1);
            my_view.removeAllViews();
            my_view.addView(view);

            ListView list_view;
            if (view instanceof ListView)
            {
                list_view = (ListView) view;
            }
            else
            {
                list_view = (ListView) view.findViewById(R.id.library_list);
            }

            libraryListAdapter adapter = (libraryListAdapter) list_view.getAdapter();
            Folder current_folder = adapter.getFolder();
            library.setCurrentFolder(current_folder);
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
        list_view.setAdapter(new libraryListAdapter(this,folder));
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
        view_stack.add(main_view);
    }


    private class libraryListAdapter extends BaseAdapter
        // only called for folders ..
        // there are no subitems of tracks.
        //
        // DONT LIKE IT ..
        //
        // if I attempt to cache these, I lose clickHandlers
        // So, I don't cache em, which means I need to keep
        // a scroll position for popping the stack.
        //
        // The whole "polling" scheme for remote servers, where
        // we call getSubItems() until it gives us what we want,
        // is also bothersome.  Seems like it should be event driven,
        // with new items getting added to the list as they show up,
        // and the adapter only using the ones that have shown up.
    {
        private Folder folder;
            // the parent folder for the list that this
            // adapter is associated with
        public Folder getFolder()  { return folder; }
        private aLibrary a_library;
        private List<Record> sub_items;
        private intViewHash items = new intViewHash();


        public libraryListAdapter(aLibrary the_a_library, Folder the_folder)
        {
            a_library = the_a_library;
            folder = the_folder;

            int count = ((Device) library).isLocal() ? 999999 : 0;
            sub_items = a_library.getLibrary().getSubItems(folder.getId(),0,count,false);
        }

        public int getCount()
        {
            return sub_items == null ? 0 : folder.getNumElements();
        }

        public long getItemId(int position)
        {
            return position;
        }

        public View getItem(int position)
        {
            return getView(position,null,null);     // items.get(position);
        }

        public View getView(int position, View parent, ViewGroup view_group)
        {
            View item = items.get(position);
            if (item == null)
            {
                // we may need to do another get subItems call ...
                // must be larger than wait time in mediaServer

                int WAIT_TIME = 500;
                int WAIT_NUM = 30;

                int wait_count = 0;
                while (position < folder.getNumElements() &&
                       position >= sub_items.size() &&
                        wait_count++ <= WAIT_NUM )
                {
                    Utils.log(0,0,"aLibrary.listAdapter calling getSubItems(" + sub_items.size() + ",0) position=" + position);
                    List<Record> new_subitems = library.getSubItems(folder.getId(),sub_items.size(),0,false);
                    Utils.log(0,1,"aLibrary.listAdapter call to getSubItems() returned " + new_subitems.size() + " items");
                    if (new_subitems.size() > 0)
                    {
                        Utils.log(0,1,"aLibrary.listAdapter adding " + new_subitems.size() + " elements to existing array of " + sub_items.size() + " records");
                        sub_items.addAll(new_subitems);
                        Utils.log(0,2,"now have " + sub_items.size() + " records");
                    }

                    if (position < folder.getNumElements() && position >= sub_items.size())
                    {
                        Utils.log(0,0,"aLibrary.listAdapter NOT SATISFIED!! sleeping for " + WAIT_TIME + " millis and trying again");
                        Utils.sleep(WAIT_TIME);
                    }
                }

                if (wait_count == WAIT_NUM && position >= sub_items.size())
                {
                    Utils.error("aLibrary.listAdapter.getView("+position+") timed out calling getSubItems() sub_items.size()=" + sub_items.size());
                    return null;
                }

                if (position >= sub_items.size())
                {
                    Utils.error("aLibrary.listAdapter().getView("+position+") wait_count="+wait_count+" is OUT OF BOUNDS sub_items.size()=" + sub_items.size());
                    return null;
                }

                Record rec = sub_items.get(position);
                LayoutInflater inflater = LayoutInflater.from(artisan);
                ListItem list_item = (ListItem) inflater.inflate(R.layout.list_item_layout,view_group,false);
                if (rec instanceof Track)
                    list_item.setTrack((Track) rec);
                else
                    list_item.setFolder((Folder) rec);

                // OnClickHandlers are set in doLayout()

                list_item.doLayout(a_library,a_library);
                // return list_item;

                // If I attempt to cache these, I lose the click listener
                item = list_item;

                if (USE_VIEW_CACHE)
                    items.put(position,item);
           }
           return item;
        }


        // implementing these two methods disables
        // view recycling by the ListView which
        // allows the cached items to work, by
        // essentially telling the list view that
        // all objects are of unique types and cannot
        // be recycled.

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
    }


    // otherwise, add the subfolders


}   // class aLibrary
