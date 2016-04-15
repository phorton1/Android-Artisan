package prh.artisan;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import prh.types.recordList;
import prh.types.viewList;
import prh.utils.Utils;



public class FolderTreeItem extends LinearLayout implements
    View.OnClickListener
{
    private static int NUM_PER_FETCH = 300;
    private static int NUM_INITIAL_FETCH = 100;
        // for local library

    Artisan artisan;
    Folder folder;
    boolean expanded;
    boolean initialized;
    LinearLayout children;

    //------------------------------------------
    // construction, onFinishInflate(), setFolder()
    //------------------------------------------

    public FolderTreeItem(Context context,AttributeSet attrs)
    {
        super(context,attrs);
        artisan = (Artisan) context;
        initialized = false;
        expanded = false;
    }


    @Override public void onFinishInflate()
    {
        children = (LinearLayout) findViewById(R.id.folder_tree_item_children);
        // children.setVisibility(View.GONE);
    }


    public void setFolder(Folder f)
    {
        folder = f;
        TextView title = (TextView) findViewById(R.id.folder_tree_item_title);
        title.setText(folder.getTitle());
        setOnClickListener(this);
    }

    public void setRootFolder(Folder f)
    {
        folder = f;;
        setExpanded(true);
        setPadding(0,0,0,0);
        TextView title = (TextView) findViewById(R.id.folder_tree_item_title);
        title.setVisibility(View.GONE);
    }


    private void initialize()
        // must be called prior to first expansion
        // gets the children folders and adds them
    {
        if (!initialized)
        {
            initialized = true;
            Utils.log(0,0,"FOLDER_TREE_ITEM_INIT(" + folder.getTitle() + ")");
            Fetcher fetcher = folder.getFetcher();
            boolean our_fetcher = false;

            if (fetcher == null)
            {
                // Create a fetcher if its local.

                    our_fetcher = true;
                    fetcher = new Fetcher(
                        artisan,
                        folder,
                        null,
                        NUM_INITIAL_FETCH,
                        NUM_PER_FETCH,
                        "FolderTreeItem(" + folder.getTitle() + ")");
            }

            fetcher.start();
            while (fetcher.getState() == Fetcher.fetcherState.FETCHER_INIT ||
                   fetcher.getState() == Fetcher.fetcherState.FETCHER_RUNNING)
            {
                Utils.log(0,0,"waiting for fetcher");
                Utils.sleep(800);
            }

            recordList records = fetcher.getRecordsRef();
            Utils.log(0,0,"FOLDER_TREE_ITEM_INIT(" + folder.getTitle() + ") adding " + records.size() + " records");

            for (Record rec:records)
            {
                if (rec instanceof Folder)
                {
                    LayoutInflater inflater = artisan.getLayoutInflater();
                    FolderTreeItem child = (FolderTreeItem)
                        inflater.inflate(R.layout.folder_tree_item,null);
                    child.setFolder((Folder)rec);
                    children.addView(child);
                }
            }

            if (our_fetcher)
                fetcher.stop(true,false);
        }
    }



    public void toggleExpanded()
    {
        setExpanded(!expanded);
    }


    public void setExpanded(boolean b)
    {
        /* folder.getNumElements() > 0 &&  */
        if (b && !expanded)
        {
            initialize();
            // children.setVisibility(View.VISIBLE);
            expanded = b;
        }
        else if (!b && expanded)
        {
            initialized = false;
            children.removeAllViews();
            // children.setVisibility(View.GONE);
            expanded = b;
        }
    }




    //------------------------------------------
    // onClick()
    //------------------------------------------

    @Override public void onClick(View v)
    // Handles context menu and artisan general behavior.
    // Passes unhandled events to underlying page.
    {
        int id = v.getId();
        FolderTreeItem tree_item = (FolderTreeItem) v;
        tree_item.toggleExpanded();
    }


}   // class FolderTreeItem
