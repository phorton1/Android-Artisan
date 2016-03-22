package prh.artisan;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.net.URI;

import prh.utils.ImageLoader;
import prh.utils.Utils;


public class ListItem extends RelativeLayout
    // Implements a do-all list item for use in the Library and Playlist.
    //
    // Takes a Track or a Folder and comes in two sizes for each.
    //
    // Track Small is a single line of text, and used when a
    // in the Library album view and Playlist when albums are showing.
    //
    // Track Large is two lines of text, only used in the Playlist
    // when albums are not showing.
    //
    // Folder Small is the same size as Track Large, two lines,
    // and is used in the Library Browser while traversing folders,
    // showing a small image or icon, and two lines of text.
    //
    // Folder Large is 5 lines of text and a larger image, shown
    // at the top of the Library album view, or as Album breaks
    // in the playlist.
{
    Artisan artisan;
    private Track track = null;
    private Folder folder = null;
    private boolean is_album = false;
    private boolean is_track = false;
    private boolean large = false;
    private boolean open = false;
    private boolean visited = false;
    private OnClickListener click_listener = null;


    public Folder getFolder()
    {
        return folder;
    }

    public Track getTrack()
    {
        return track;
    }


    //-------------------------------------------------
    // post-inflate configuration
    //-------------------------------------------------

    public void setLargeView()
    {
        large = true;
    }


    public boolean setTrack(Track the_track)
    {
        is_track = true;
        track = the_track;
        return true;
    }

    public boolean setFolder(Folder the_folder)
    {
        folder = the_folder;
        is_album = folder.getType().equals("album");
        return true;
    }


    //------------------------------------------
    // construction, onFinishInflate()
    //------------------------------------------

    public ListItem(Context context,AttributeSet attrs)
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

    public void mySetOnClickListener(OnClickListener listener)
    {
        click_listener = listener;
    }

    //---------------------------------------------
    // doLayout()
    //---------------------------------------------

    public void doLayout()
    {
        // set background color

        if (large && !is_track)
            setBackgroundColor(0xFF002222);

        // set icon_container size to one of three heights
        // the width is zero for small tracks

        float container_height = is_track ?
            large ? 52 : 36 :
            large ? 70 : 52;
        float container_width = is_track && !large ?
            0 : container_height;

        Utils.setViewSize(artisan,this,R.id.list_item_icon_container,container_height,container_width);


        // IMAGE
        // not done for small tracks. otherwise if there
        // is an art_uri, fill up the icon container, or
        // if no art_uri, use params from xml

        if (!is_track || large)
        {
            String art_uri = folder.getLocalArtUri();
            ImageView image = (ImageView) findViewById(R.id.list_item_icon);

            if (!art_uri.isEmpty())
            {
                float image_size = container_height - (large ? 0 : 4);
                Utils.setViewSize(artisan,image,image_size,image_size);
                ImageLoader image_loader = new ImageLoader(artisan,image,art_uri);
                Thread image_thread = new Thread(image_loader);
                image_thread.start();
            }
            else if (is_album)
            {
                image.setImageResource(R.drawable.icon_album);
            }
            else
            {
                image.setImageResource(R.drawable.icon_folder);
            }
        }


        // 1st line - TITLE

        String title = is_track ? track.getTitle() : folder.getTitle();
        Utils.setText(this,R.id.list_item_line1,title);

        // if not in large folder view
        // Show the num_tracks, or track_time to the right

        if (is_track || !large)
        {
            int n = is_track ? 0 : folder.getNumElements();
            String num = is_track ?
                track.getDurationString(Utils.how_precise.FOR_DISPLAY) :
                n > 0 ? "(" + n + ")" : "";
            Utils.setText(this,R.id.list_item_right,num);
        }

        // only in small track view, show the track num,
        // if it exists, to the left. disappear it otherwise

        if (!is_track || large)
            Utils.setViewSize(artisan,this,R.id.list_item_left,null,0F);
        else
        {
            String track_num = track.getTrackNum();
            if (!track_num.isEmpty())
                track_num += ".";
            Utils.setText(this,R.id.list_item_left,track_num);
        }

        // 2nd-line - ARTIST
        // get the artist to the one from the record
        // except for small tracks or folders that are not albums
        // then set it if it is not empty, or even in the
        // large folder(album) view as it takes up an empty line

        String artist = is_track ?
            large ? track.getArtist() : "" :
            is_album ? folder.getArtist() : "";
        TextView line2 = (TextView) findViewById(R.id.list_item_line2);

        if (!artist.isEmpty() || (large && !is_track))
            line2.setText(artist);
        else
            Utils.setViewSize(artisan,line2,0F,null);


        // LINES 3-5 are only for large albums

        TextView line3 = (TextView) findViewById(R.id.list_item_line3);
        TextView line4 = (TextView) findViewById(R.id.list_item_line4);
        TextView line5 = (TextView) findViewById(R.id.list_item_line5);
        if (is_track || !large)
        {
            Utils.setViewSize(artisan,line3,0F,null);
            Utils.setViewSize(artisan,line4,0F,null);
            Utils.setViewSize(artisan,line5,0F,null);
        }
        else    // large => also implies is_album
        {

            String s3 = "";
            int num = folder.getNumElements();
            if (num > 0)
                s3 += "" + num + " tracks      ";
            s3 += folder.getGenre() + "      ";
            s3 += folder.getYearString();

            String s4 = "";
            if (folder.getFolderError() != 0)
                s4 += "error:" + folder.getFolderError();
            if (folder.getHighestFolderError() != 0)
            {
                if (!s4.isEmpty()) s4 += "    ";
                s4 += "high_error:" + folder.getHighestFolderError();
            }
            if (folder.getHighestTrackError() != 0)
            {
                if (!s4.isEmpty()) s4 += "    ";
                s4 += "high_track_error:" + folder.getHighestTrackError();
            }

            String s5 = "";
            s5 += "id:" + folder.getId();
            s5 += "   parent_id:" + folder.getParentId();


            line3.setText(s3);
            line4.setText(s4);
            line5.setText(s5);

        }

    }   // listItem.doLayout()



}   // class listItemFolder
