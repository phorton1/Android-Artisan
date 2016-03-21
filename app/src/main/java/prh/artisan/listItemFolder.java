package prh.artisan;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.net.URI;

import prh.utils.ImageLoader;
import prh.utils.Utils;


public class listItemFolder extends RelativeLayout implements View.OnClickListener
{
    Artisan artisan;
    private Folder folder;
    boolean open = false;
    boolean visited = false;
    boolean is_album = false;


    public Folder getFolder()  { return folder; }

    //-------------------------------------------------
    // post-inflate configuration
    //-------------------------------------------------

    public boolean setFolder(Folder the_folder)
    {
        folder = the_folder;
        is_album = folder.getType().equals("album");
        return true;
    }

    public boolean setFolder(Library library, String id)
    {
        Folder folder = library.getFolder(id);
        if (folder == null)
        {
            Utils.error("listItemFolder: NULL FOLDER for id(" + id + ")");
            return false;
        }
        return setFolder(folder);
    }

    public boolean setFolder(String uri, String didl)
    {
        Folder folder = new Folder(uri,didl);
        if (folder == null)
        {
            Utils.error("listItemFolder: NULL FOLDER for uri,did(" + uri + "," + didl + ")");
            return false;
        }
        return setFolder(folder);
    }


    //-------------------------------------------
    // Get Child Views
    //-------------------------------------------
    // Upon clicking on an item, this method adds and
    // returns the new libraryList child of the item,
    // which may be a an album ...



    //------------------------------------------
    // construction, onFinishInflate()
    //------------------------------------------

    public listItemFolder(Context context, AttributeSet attrs)
        // called when inflated from the layout
    {
        super(context,attrs);
    }


    public void onFinishInflate()
        // called when the inflate is finished
        // set the default (small, non-artisan, view)
        // sets the onClick handler for the item
    {
        artisan = (Artisan) getContext();
        // this.setOnClickListener(this);
    }


    public int convertDipToPixels(float dips)
    {
        return (int) (dips * artisan.getResources().getDisplayMetrics().density + 0.5f);
    }


    //---------------------------------------------
    // doLayout()
    //---------------------------------------------
    // Not beefed up yet

    public void doLayout()
    {
        ((TextView) this.findViewById(R.id.list_item_folder_title)).
            setText(folder.getTitle());

        ImageView image = (ImageView) findViewById(R.id.list_item_folder_icon);

        String artist = "";
        if (is_album)
        {
            artist = folder.getArtist();
            String art_uri = folder.getLocalArtUri();
            if (!art_uri.isEmpty())
            {
                ViewGroup.LayoutParams image_params = image.getLayoutParams();
                MarginLayoutParams margins = new MarginLayoutParams(image_params);
                margins.setMargins(0,0,0,0);
                margins.height = convertDipToPixels(48F);
                margins.width = convertDipToPixels(48F);
                image.setLayoutParams(new RelativeLayout.LayoutParams(margins));

                ImageLoader image_loader = new ImageLoader(artisan,image,art_uri);
                Thread image_thread = new Thread(image_loader);
                image_loader.start();
            }
            else
            {
                image.setImageResource(R.drawable.icon_album);
            }
        }
        else
        {
            image.setImageResource(R.drawable.icon_folder);
        }

        TextView artist_text = (TextView) findViewById(R.id.list_item_folder_artist);
        ViewGroup.LayoutParams artist_params = artist_text.getLayoutParams();

        if (artist.isEmpty())
        {
            artist_params.height = 0;
        }
        else
        {
            artist_text.setText(artist);
        }
    }



    //----------------------------------------------
    // onClick
    //----------------------------------------------

    public void onClick(View v)
    {
        int id = v.getId();
        if (id == 0)
        {
            Utils.error("blah");
        }
    }


}   // class listItemFolder
