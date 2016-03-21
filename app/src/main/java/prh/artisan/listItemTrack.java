package prh.artisan;

//-------------------------------------------------------
// listItemTrack
//-------------------------------------------------------
// The object comes in two different varieties.
// The default is a single short line of text where the album is implied.
//
//     [artisan_icon] track_num track_title     [artist]    track_time context_menu
//
// If set to Large, the object is a multiple line view that includes an image
//
//      +-------+
//      | image |  [artisan_icon] track_num track_title   [artist]   track_time context_menu
//      +-------+         album_title album_artist                   genre year
//
// There can be preferences to show the track_num and track_time.
// The artist in the first line should only be shown if it is different
// than the album artist set with setAlbumArtist().
//
// If isArtisan, then the track will be expected to have a higheset_error
// field, from which the colored artisan_icon will be gotten


import android.content.Context;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import prh.utils.ImageLoader;
import prh.utils.Utils;

public class listItemTrack extends RelativeLayout implements View.OnClickListener
    // RelativeLayout that represents a single Track.
    // Used by the libraryAlbum and Playlist views.
    // After inflation, call setTrack() from a Library
    // and an ID, or a URI and some Didl. The item
    // is no good if setTrackFails.  Then call setAlbumArtist()
    // and or setLargeView(), and then finally call doLayout().
{
    private Artisan artisan;
    private Track track;
    private String album_artist = "";
    boolean isArtisan = false;
    boolean isLarge = false;

    //-------------------------------------------------
    // post-inflate configuration
    //-------------------------------------------------

    public void setLargeView(boolean large, boolean isArtisan_track)

    {
        large = isLarge;
        isArtisan = isArtisan_track;
    }


    // setTrack()

    public boolean setTrack(Track the_track)
    {
        track = the_track;
        return true;
    }

    public boolean setTrack(Library library, String id)
    {
        Track track = library.getTrack(id);
        if (track == null)
        {
            Utils.error("listItemTrack: NULL TRACK for id(" + id + ")");
            return false;
        }
        return setTrack(track);
    }

    public boolean setTrack(String uri, String didl)
    {
        Track track = new Track(uri,didl);
        if (track == null)
        {
            Utils.error("listItemTrack: NULL TRACK for uri/didl(" + uri + "," + didl + ")");
            return false;
        }
        return setTrack(track);
    }


    // setAlbumArtist

    public void setAlbumArtist(String aa)
        // if set, the [artist] field will only be shown
        // if it is different than the album_artist.
        // Should be called after setTrack()
    {
        album_artist = aa;
    }

    public void setAlbumArtist(Folder album)
        // set the album artist from the folder
    {
        setAlbumArtist(album.getArtist());
    }

    public void setAlbumArtist(Library library)
        // set the album artist by getting the parent
        // folder (album) from the library
    {
        Folder folder = library.getFolder(track.getParentId());
        setAlbumArtist(folder);
    }

    public void setAlbumArtist(String uri, String didl)
    // set the album artist by creating a folder
    // from the given uri (ignored) and didl
    {
        Folder folder = new Folder(uri,didl);
        setAlbumArtist(folder);
    }


    //------------------------------------------
    // construction, onFinishInflate()
    //------------------------------------------

    public listItemTrack(Context context, AttributeSet attrs)
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


    //---------------------------------------------
    // doLayout()
    //---------------------------------------------

    public void doLayout()
    {
        // Set all the normal fields (except image and artisan_icon)

        String use_artist = track.getArtist();
        if (album_artist.equals(use_artist))
            use_artist = "";

        // basic first line stuff

        ((TextView) this.findViewById(R.id.list_item_track_tracknum))
            .setText(track.getTrackNum());
        ((TextView) this.findViewById(R.id.list_item_track_title))
            .setText(track.getTitle());
        ((TextView) this.findViewById(R.id.list_item_track_artist))
            .setText(use_artist);
        ((TextView) this.findViewById(R.id.list_item_track_time))
            .setText(track.getDurationString(Utils.how_precise.FOR_DISPLAY));

        // disappear the image and 2nd line if !isLarge

        RelativeLayout second_line = (RelativeLayout) this.findViewById(
            R.id.list_item_track_second_line);
        ImageView image_view = (ImageView) this.findViewById(
            R.id.list_item_track_image);

        ViewGroup.LayoutParams second_line_params = second_line.getLayoutParams();
        ViewGroup.LayoutParams image_params = image_view.getLayoutParams();

        if (!isLarge)
        {
            second_line_params.height = 0;
            image_params.height = 0;
            image_params.width = 0;
        }

        // set second_line and image if isLarge

        else
        {
            second_line_params.height =  ViewGroup.LayoutParams.WRAP_CONTENT;
            image_params.height = 48;
            image_params.width = 48;

            ((TextView) this.findViewById(R.id.list_item_track_album_title))
                .setText(track.getAlbumTitle());
            ((TextView) this.findViewById(R.id.list_item_track_album_artist))
                .setText(track.getAlbumArtist());
            ((TextView) this.findViewById(R.id.list_item_track_year))
                .setText(track.getYearString());
            ((TextView) this.findViewById(R.id.list_item_track_genre))
                .setText(track.getGenre());

            String image_url = track.getLocalArtUri();
            if (!image_url.isEmpty())
            {
                ImageLoader image_loader = new ImageLoader(artisan,image_view,image_url);
                Thread thread = new Thread(image_loader);
                thread.start();
            }

        }   // isLarge

        second_line.setLayoutParams(second_line_params);
        image_view.setLayoutParams(image_params);

        // currently does not touch the artisan_icon

    }   // listItemTrack.doLayout()



    //----------------------------------------------
    // onClick
    //----------------------------------------------

    public void onClick(View v)
    {

    }


}   // class listItemTrac
