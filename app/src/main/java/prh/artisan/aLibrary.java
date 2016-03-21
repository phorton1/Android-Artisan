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
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import prh.utils.Utils;


public class aLibrary extends Fragment implements
    ArtisanPage,
    EventHandler,
    View.OnClickListener
{
    private static int dbg_alib = 0;

    public String getName()  { return "Library"; }
    public class viewStack extends ArrayList<View> {}


    private Artisan artisan = null;
    private Library library = null;
    private LinearLayout my_view = null;
    private viewStack view_stack = null;

    public Artisan getArtisan()  { return artisan; }
    public Library getLibrary()  { return library; }
    public LinearLayout getMyView()  { return my_view; }




    //----------------------------------------------
    // life cycle
    //----------------------------------------------

    @Override
    public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle savedInstanceState)
    {
        Utils.log(dbg_alib,0,"aLibrary.onCreateView() called");
        my_view = (LinearLayout) inflater.inflate(R.layout.activity_library, container, false);

        view_stack = new viewStack();
        library = artisan.getLibrary();
        if (library != null)
            pushViewStack("0");
        return my_view;
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
            view_stack.remove(view_stack.size()-1);
            View list_view = view_stack.get(view_stack.size()-1);
            my_view.removeAllViews();
            my_view.addView(list_view);
        }
    }


    private void pushViewStack(String id)
    {
        Folder folder = library.getFolder(id);
        boolean is_album = folder.getType().equals("album");
        String table = is_album ? "tracks" : " folders";

        LayoutInflater inflater = LayoutInflater.from(artisan);
        ListView list_view = (ListView) inflater.inflate(R.layout.library_list,null,false);
        list_view.setAdapter(new libraryListAdapter(this,folder));

        view_stack.add(list_view);
        my_view.removeAllViews();
        my_view.addView(list_view);
    }


    private class libraryListAdapter extends BaseAdapter
    {
        private Folder folder;
        private boolean is_album;
        private aLibrary a_library;
        List<Record> sub_items;
        HashMap<Integer, View> items = new HashMap<Integer,View>();
            // I shouldn't need to cache these


        public libraryListAdapter(aLibrary the_a_library, Folder the_folder)
        {
            a_library = the_a_library;
            folder = the_folder;
            is_album = folder.getType().equals("album");
            String table = is_album ? "tracks" : "folders";
            sub_items = a_library.getLibrary().getSubItems(table,folder.getId(),0,999999);
        }


        public int getCount()
        {

            return sub_items == null ? 0 :
                sub_items.size() + (is_album ? 1 : 0);
        }

        public long getItemId(int position)
        {
            return position;
        }

        public View getItem(int position)
        {
            return items.get(position);
        }

        public View getView(int position, View parent, ViewGroup view_group)
        {
            View item = items.get(position);
            if (item == null)
            {
                LayoutInflater inflater = LayoutInflater.from(artisan);
                int item_offset = is_album ? 1 : 0;
                if (is_album && position == 0)
                {
                    listItemFolder ifolder = (listItemFolder) inflater.inflate(R.layout.list_item_folder,view_group,false);
                    ifolder.setFolder(folder);
                    ifolder.doLayout();
                    item = ifolder;
                }
                else
                {
                    int list_idx = position - item_offset;
                    Record rec = sub_items.get(list_idx);
                    if (is_album)   // sub_items are tracks
                    {
                        listItemTrack strack = (listItemTrack) inflater.inflate(R.layout.list_item_track,view_group,false);
                        strack.setTrack((Track) rec);
                        strack.setAlbumArtist(folder);
                        strack.doLayout();
                        item = strack;
                    }
                    else    // sub-items are folders
                    {
                        listItemFolder sfolder = (listItemFolder) inflater.inflate(R.layout.list_item_folder,view_group,false);
                        sfolder.setFolder((Folder) rec);
                        sfolder.doLayout();
                        item = sfolder;
                        item.setOnClickListener(a_library);
                    }
                }
                // items.put(position,item);
            }
            return item;
        }

    }   // class libraryListAdapter



    //--------------------------------------------------------------
    // onClick()
    //--------------------------------------------------------------

    public void onClick(View v)
    {
        int id = v.getId();
        switch (id)
        {
            case R.id.list_item_folder:
                Folder folder = ((listItemFolder)v).getFolder();
                String next_id = folder.getId();
                pushViewStack(next_id);
                break;

        }   // switch
    }   // onClick()


    //--------------------------------------------------------------
    // Artisan Event Handling
    //--------------------------------------------------------------
    // EVENT_LIBRARY_CHANGED


    public void handleArtisanEvent(final String event_id,final Object data)
    {
        if (event_id.equals(EVENT_LIBRARY_CHANGED))
        {
            Library new_library = (Library) data;
            if (new_library == null || library == null ||
                !new_library.getName().equals(library.getName()))
            {
                library = new_library;
                my_view.removeAllViews();
                view_stack = new viewStack();
                if (library != null)
                    pushViewStack("0");
            }
        }
    }


    // otherwise, add the subfolders


}   // class aLibrary
