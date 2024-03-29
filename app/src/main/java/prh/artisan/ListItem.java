package prh.artisan;

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
//
// Usage: inflate, setFolder or Track, maybe setLargeView(),
// and then call doLayout() before handing off to adapter.
//
// This class intercepts onClick on the metaData button.
// onLongClick Selection is handled by the ListItemAdapter,
// and aPlaylist and aLibrary handle onItemClick.

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import prh.base.EditablePlaylist;
import prh.types.recordSelector;
import prh.utils.Utils;
import prh.utils.ImageLoader;


public class ListItem extends RelativeLayout implements
    View.OnClickListener
{
    // member variables

    Artisan artisan;
    private Track track = null;
    private Folder folder = null;
    private boolean is_album = false;
    private boolean is_track = false;
    private boolean large = false;
    private boolean open = false;
    private boolean visited = false;

    // accessors

    public Folder getFolder()
    {
        return folder;
    }

    public Track getTrack()
    {
        return track;
    }

    public Record getRecord()
    {
        return is_track ? track : folder;
    }

    //-------------------------------------------------
    // post-inflate configuration
    //-------------------------------------------------

    public void setLargeView()
        // called AFTER setTrack/setFolder
    {
        large = true;
    }


    public boolean setTrack(Track the_track)
    {
        folder = null;
        is_track = true;
        is_album = false;
        track = the_track;
        large = false;
        return true;
    }

    public boolean setFolder(Folder the_folder)
    {
        track = null;
        folder = the_folder;
        is_track = false;
        is_album = folder.getType().equals("album");
        large = false;
        return true;
    }


    //------------------------------------------
    // construction, onFinishInflate()
    //------------------------------------------

    public ListItem(Context context,AttributeSet attrs)
    {
        super(context,attrs);
        artisan = (Artisan) context;
    }


    @Override public void onFinishInflate()
    {
    }


    //---------------------------------------------
    // doLayout()
    //---------------------------------------------

    private static int SELECTED_COLOR = 0xFF332200;
    private static int ALBUM_HEADER_COLOR = 0xFF002222;


    public void doLayout(recordSelector selector)
        // cannot assume that the layout is fresh from the xml
    {
        // get sub views

        ImageView image = (ImageView) findViewById(R.id.list_item_icon);
        TextView line1 = (TextView) findViewById(R.id.list_item_line1);
        TextView line2 = (TextView) findViewById(R.id.list_item_line2);
        TextView line3 = (TextView) findViewById(R.id.list_item_line3);
        TextView line4 = (TextView) findViewById(R.id.list_item_line4);
        TextView line5 = (TextView) findViewById(R.id.list_item_line5);
        TextView item_left = (TextView) findViewById(R.id.list_item_left);
        RelativeLayout item_right = (RelativeLayout) findViewById(R.id.list_item_right);
        TextView item_right_text = (TextView) findViewById(R.id.list_item_right_text);

        // set background color

        int color =
            selector.getSelected(true,getRecord()) ? SELECTED_COLOR :
            large && !is_track ? ALBUM_HEADER_COLOR : 0;
        setBackgroundColor(color);

        // IMAGE
        // set icon_container size to one of three heights
        // the width is zero for small tracks

        float container_height = is_track ?
            large ? 52 : 36 :
            large ? 70 : 52;
        float container_width = is_track && !large ?
            0 : container_height;
        Utils.setViewSize(artisan,this,R.id.list_item_icon_container,container_height,container_width);

        // set the icon size itself based on what it is,
        // and whether there is an art_uri

        String art_uri = is_track ?
            track.getLocalArtUri() :
            folder.getLocalArtUri();

        float default_image_size = 24;

        float image_size = is_track ?
            large ?  art_uri.isEmpty() ?
                default_image_size : 52 :  0 :
            art_uri.isEmpty()? default_image_size :
                large? 70 : 52;

        Utils.setViewSize(artisan,image,image_size,image_size);

        if (image_size == 0)
            image.setImageBitmap(null);
        else if (!art_uri.isEmpty())
            ImageLoader.loadImage(artisan,image,art_uri);
        else if (is_track)
            ImageLoader.loadImage(artisan,image,R.drawable.icon_track);
        else if (is_album)
            ImageLoader.loadImage(artisan,image,R.drawable.icon_album);
        else
            ImageLoader.loadImage(artisan,image,R.drawable.icon_folder);


        // TITLE (First Line)

        String title = is_track ? track.getTitle() : folder.getTitle();
        line1.setText(title);


        // NUM_TRACKS OR TRACK_TIME
        // sets the right side onClickListener if displayed

        if (is_track || !large)
        {
            int n = is_track ? 0 : folder.getNumElements();
            String num = is_track ?
                track.getDurationString(Utils.how_precise.FOR_DISPLAY) :
                n > 0 ? "(" + n + ")" : "";

            item_right.setVisibility(View.VISIBLE);
            item_right.setOnClickListener(this);
            item_right_text.setOnClickListener(this);
            Utils.setViewSize(artisan,item_right,container_height,null);
            item_right_text.setText(num);
        }
        else
        {
            item_right.setVisibility(View.GONE);
        }


        // TRACK_NUM
        // only in small track view, show the track num,
        // if it exists, to the left. disappear it otherwise

        if (!is_track || large)
            item_left.setVisibility(View.GONE);
        else
        {
            item_left.setVisibility(View.VISIBLE);
            String track_num = track.getTrackNum();
            if (!track_num.isEmpty())
                track_num += ".";
            item_left.setText(track_num);
        }


        // ARTIST (Second Line)
        // get the artist to the one from the record
        // except for small tracks or folders that are not albums
        // then set it if it is not empty, or even in the
        // large folder(album) view as it takes up an empty line

        String artist = is_track ?
            large ? track.getArtist() : "" :
            is_album ? folder.getArtist() : "";

        if (!artist.isEmpty() || (large && !is_track))
        {
            line2.setVisibility(View.VISIBLE);
            line2.setText(artist);
        }
        else
            line2.setVisibility(View.GONE);


        // LINES 3-5 are only for large albums

        if (is_track || !large)
        {
            line3.setVisibility(View.GONE);
            line4.setVisibility(View.GONE);
            line5.setVisibility(View.GONE);
        }
        else    // large => also implies is_album
        {
            line3.setVisibility(View.VISIBLE);
            line4.setVisibility(View.VISIBLE);
            line5.setVisibility(View.VISIBLE);

            String s3 = "";
            int num = folder.getNumElements();
            if (num > 0)
                s3 += "" + num + " tracks      ";
            s3 += folder.getGenre() + "      ";
            s3 += folder.getYearString();

            int duration = folder.getDuration();
            if (duration > 0)
                s3 += "     " + Utils.durationToString(duration,Utils.how_precise.FOR_DISPLAY);


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


        // FINISHING UP
        // Shrink the text size of line1, line2, and right
        // when its a track.  There's probably a way to do this
        // with a style or class.

        if (is_track)
        {
            line1.setTextSize(TypedValue.COMPLEX_UNIT_DIP,12);
            line2.setTextSize(TypedValue.COMPLEX_UNIT_DIP,10);
            item_right_text.setTextSize(TypedValue.COMPLEX_UNIT_DIP,10);
        }
        else
        {
            line1.setTextSize(TypedValue.COMPLEX_UNIT_DIP,14);
            line2.setTextSize(TypedValue.COMPLEX_UNIT_DIP,12);
            item_right_text.setTextSize(TypedValue.COMPLEX_UNIT_DIP,12);
        }

    }   // listItem.doLayout()



    //--------------------------------------------------------------
    // onClick()
    //--------------------------------------------------------------


    @Override public void onClick(View v)
        // Handles context menu and artisan general behavior.
        // Passes unhandled events to underlying page.
    {
        int id = v.getId();

        // Popup the list_item context menu

        ListItem list_item;
        switch (id)
        {
            case R.id.list_item_right :
            case R.id.list_item_right_text :

                String msg = "ListItem ContextMenu ";
                View item = (View) v.getParent();
                if (id == R.id.list_item_right_text)
                    item = (View) item.getParent();
                list_item = (ListItem) item;

                // show the Folder/Track metaData (info) in a dialogWindow

                if (list_item.getFolder() != null)
                {
                    MetaDialog.showFolder(artisan,list_item.getFolder());
                }
                else
                {
                    if (false)
                    {
                        Track track = list_item.getTrack();
                        EditablePlaylist current_playlist = artisan.getCurrentPlaylist();
                        Utils.log(0,0,"Inserting Track(" + track.getTitle() + " into playlist");
                        current_playlist.insertTrack(current_playlist.getNumTracks() + 1,track);
                    }
                    MetaDialog.showTrack(artisan,list_item.getTrack());
                }
                break;
        }
    }

}   // class ListItem
