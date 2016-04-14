package prh.artisan;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import prh.base.ArtisanPage;
import prh.base.Library;
import prh.types.intList;
import prh.utils.Utils;


public class aExplorer extends Fragment implements ArtisanPage
{
    private static int dbg_exp = 0;
    private Artisan artisan = null;
    private TextView page_title = null;


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
        Utils.log(dbg_exp,0,"aExplorer.onCreateView() called");
        ScrollView my_view = (ScrollView) inflater.inflate(R.layout.activity_explorer,container,false);
        LinearLayout the_list = (LinearLayout) my_view.findViewById(R.id.explorer_list);

        Library library = artisan.getLibrary();
        if (library != null)
        {
            Folder root_folder = library.getRootFolder();
            FolderTreeItem root_item = (FolderTreeItem) inflater.inflate(R.layout.folder_tree_item,null);
            root_item.setRootFolder(root_folder);
            the_list.addView(root_item);
        }
        return my_view;
    }


    @Override
    public void onAttach(Activity activity)
    {
        Utils.log(dbg_exp,0,"aExplorer.onAttach() called");
        super.onAttach(activity);
    }


    @Override
    public void onDetach()
    {
        Utils.log(dbg_exp,0,"aExplorer.onDetach() called");
        super.onDetach();
    }


    //------------------------------------------
    // utilities
    //------------------------------------------

    @Override public void onSetPageCurrent(boolean current)
    {
        page_title = null;
        if (current)
        {
            page_title = new TextView(artisan);
            page_title.setText("Explorer");
            artisan.setArtisanPageTitle(page_title);
        }
    }


    @Override public boolean onMenuItemClick(MenuItem item)
    {
        return true;
    }

    @Override public intList getContextMenuIds()
    {
        return new intList();
    }


}   // class aExplorer
