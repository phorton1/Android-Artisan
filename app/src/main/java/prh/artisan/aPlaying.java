package prh.artisan;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;

import android.support.v4.app.Fragment;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import prh.utils.Utils;


public class aPlaying extends Fragment implements EventHandler, View.OnClickListener
{
    private static int dbg_anp = 0;

    // valid for the life of the object
    // between onCreate and onDestroy

    private View my_view = null;
    private PlayListButtonAdapter buttonAdapter = null;

    // state

    private int current_position = 0;
    private String current_state = "";
    private Track current_track = null;
    private Playlist current_playlist = null;

    // only good while attached

    private Artisan artisan = null;

    // working vars

    private boolean in_slider = false;


   //----------------------------------------------
    // life cycle
    //----------------------------------------------

    @Override
    public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle savedInstanceState)
    {
        Utils.log(dbg_anp,0,"aPlaying.onCreateView() called");
        my_view = inflater.inflate(R.layout.activity_now_playing, container,false);

        my_view.findViewById(R.id.button_vol).setOnClickListener(this);
        my_view.findViewById(R.id.button_play_pause).setOnClickListener(this);
        my_view.findViewById(R.id.button_stop).setOnClickListener(this);
        my_view.findViewById(R.id.button_next).setOnClickListener(this);
        my_view.findViewById(R.id.button_prev).setOnClickListener(this);
        ((SeekBar) my_view.findViewById(R.id.track_position_slider)).
            setOnSeekBarChangeListener(new trackSeekBarListener());

        enable(R.id.button_vol,true);
        enable(R.id.button_prev,false);
        enable(R.id.button_next,false);
        enable(R.id.button_play_pause,false);
        enable(R.id.button_stop,false);
        enable(R.id.track_position_slider,false);

        setPlayListNames();

        return my_view;
    }


    @Override
    public void onAttach(Activity activity)
    {
        Utils.log(dbg_anp,0,"aPlaying.onAttach() called");
        super.onAttach(activity);
        artisan = (Artisan) activity;
    }


    @Override
    public void onDetach()
    {
        Utils.log(dbg_anp,0,"aPlaying.onDetach() called");
        super.onDetach();
        artisan = null;
        buttonAdapter = null;
    }


    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
    }


    //---------------------------
    // Button Handlers
    //---------------------------

    @Override
    public void onClick(View v)
    {
        Renderer renderer = artisan.getRenderer();
        if (renderer == null) return;

        switch (v.getId())
        {
            case R.id.button_play_pause:
                if (renderer.getRendererState().equals(Renderer.RENDERER_STATE_PLAYING))
                    renderer.pause();
                else
                    renderer.play();
                break;
            case R.id.button_stop:
                renderer.stop();
                break;
            case R.id.button_prev:
                renderer.incAndPlay(-1);
                break;
            case R.id.button_next:
                renderer.incAndPlay(1);
                break;
            case R.id.button_vol:
                artisan.doVolumeControl();
                    // would like to disable the control
                    // in certain cases
                break;
        }
    }


    class trackSeekBarListener implements SeekBar.OnSeekBarChangeListener
    {
        int progress = 0;

        @Override
        public void onProgressChanged(SeekBar seekBar,int progresValue,boolean fromUser)
        {
            progress = progresValue;
            Utils.log(dbg_anp+3,0,"onProgressChanged(" + progresValue + ")");
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar)
        {
            in_slider = true;
            Utils.log(dbg_anp+1,0,"onStartTrackingTouch()");
            Toast.makeText(artisan,"Started tracking seekbar",Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar)
        {
            in_slider = false;
            Utils.log(dbg_anp + 1,0,"onStopTrackingTouch() progress=" + progress);
            Renderer renderer = artisan.getRenderer();
            if (renderer != null)
                renderer.seekTo(progress);
        }
    };


    //---------------------------
    // UI Utilities
    //---------------------------

    void enable(int id, boolean enable)
    // enable/disable a Button or ImageButton
    // jesus christ, image buttons don't change their
    // appearance when disabled, and there was no easy
    // or apparent way to do it, so instead using the
    // alpha channel, and UNDOCUMENTED range of 0..255
    {
        View btn = my_view.findViewById(id);
        if (btn instanceof ImageButton)
            ((ImageButton)btn).setImageAlpha(enable?255:100);
        btn.setEnabled(enable);
    }


    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap>
        // display the now playing image asynchronously
    {
        ImageView bmImage;

        public DownloadImageTask(ImageView bmImage)
        {
            this.bmImage = bmImage;
        }

        protected Bitmap doInBackground(String... urls)
        {
            String urldisplay = urls[0];
            Bitmap mIcon11 = null;
            try
            {
                InputStream in = new java.net.URL(urldisplay).openStream();
                mIcon11 = BitmapFactory.decodeStream(in);
            }
            catch (Exception e)
            {
                Utils.error("Could not load image:" + e.getMessage());
            }
            return mIcon11;
        }

        protected void onPostExecute(Bitmap result)
        {
            bmImage.setImageBitmap(result);
        }
    }


    //-----------------------------------------------
    // Sliding Playlist Buttons
    //-----------------------------------------------
    // uses an "Adapter" which works with GridView
    // to supply the views for each button, and each
    // button gets a ClickListener ...

    public void setPlayListNames()
    {
        // get the names, if any, from the playlist_source

        String names[] = new String[0];
        Renderer renderer = artisan.getRenderer();
        PlaylistSource playlist_source = renderer==null ? null : renderer.getPlaylistSource();
        if (playlist_source != null)
            names = playlist_source.getPlaylistNames();

        GridView gridview = (GridView) my_view.findViewById(R.id.horizontal_gridView);

        // setup the gridview

        int width = 90;
        DisplayMetrics dm = new DisplayMetrics();
        artisan.getWindowManager().getDefaultDisplay().getMetrics(dm);
        float density = dm.density;
        int totalWidth = (int) (width * names.length * density);
        int singleItemWidth = (int) (width * density);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            totalWidth,LinearLayout.LayoutParams.MATCH_PARENT);

        gridview.setLayoutParams(params);
        gridview.setColumnWidth(singleItemWidth);
        gridview.setHorizontalSpacing(2);
        gridview.setStretchMode(GridView.STRETCH_SPACING);
        gridview.setNumColumns(names.length);

        // set the adapter

        Utils.log(dbg_anp + 1,2,"setting gridview adapter");
        buttonAdapter = new PlayListButtonAdapter(names);
        gridview.setAdapter(buttonAdapter);
    }


    public class PlayListButtonAdapter extends BaseAdapter
        // This class exists to provide the playlist names
        // and ids to create the buttons. The id is the
        // index into the array of playlist_names[]
        //
        // prh - need to handle changes to length of list
    {
        private int ID_BASE = 93393;
        public String playlist_names[];

        public Button getButton(String name)
        {
            for (int i=0; i < playlist_names.length; i++)
            {
                if (playlist_names[i].equals(name))
                {
                    return (Button) getItem(i);
                }
            }
            return null;
        }

        // All remaining methods are necessary overrides of abstract base class.

        public PlayListButtonAdapter(String names[])	{ playlist_names = names; }
        // Ctor gets the context so it can be used in getView
        public int getCount()					{ return playlist_names.length; }
        // Return number of items in the adapter
        public Object getItem(int position)   	{ return my_view.findViewById(position + ID_BASE);  }
        // unimplemented, required by base class
        public long getItemId(int position) 	{ return position; }
        // return the position as the id for quick impl


        public View getView(int position, View exists, ViewGroup parent)
        // return the button ("view") for each position.
        // may already exist, in which case we are re-using it
        // and merely need to change the label
        {
            Button btn;
            if (exists == null)
            {
                Utils.log(dbg_anp+1,3,"new playlist button " + playlist_names[position]);
                btn = new Button(my_view.getContext());
                btn.setLayoutParams(new GridView.LayoutParams(120, 70));
                // params are width, height of the button
                btn.setPadding(8, 8, 8, 8);
            }
            else
            {
                btn = (Button) exists;
            }

            String name = playlist_names[position];
            Utils.log(dbg_anp + 2,3,"playlist button(" + position + ")=" + name);

            btn.setTextSize(10);
            btn.setOnClickListener(new View.OnClickListener()
            {
                public void onClick(View v)
                {
                    int position = v.getId() - ID_BASE;

                    Utils.log(dbg_anp,2,"onClick(" + v.getId() + ") position=" + position);
                    String name = playlist_names[position];
                    Utils.log(dbg_anp,3,"playlist name=" + name);

                    // tell artisan that the playlist has changed
                    // it will event the change back to us

                    artisan.setPlayList(name);

                }
            });

            if (name.equals(""))
                name = "default";

            btn.setText(name);
            btn.setId(ID_BASE + position);
            return btn;
        }
    }


    public void setPlayListButtonSelected(String name, boolean sel)
    {
        if (buttonAdapter != null)
        {
            Button btn = buttonAdapter.getButton(name);
            if (btn != null)
                btn.setTextColor(sel ? Color.BLUE : Color.WHITE);
        }
    }


    //-----------------------------------------------------------
    // Event Handling
    //-----------------------------------------------------------

    public void handleEvent(String action,Object data)
        // handle changes ...
        // in order of most minor, to most major changes
        // where major changes require more updating.
    {
        if (action.equals(EventHandler.EVENT_POSITION_CHANGED))
        {
            update_position((Integer) data);
        }
        else if (action.equals(EventHandler.EVENT_STATE_CHANGED))
        {
            update_state((String) data);
            update_whats_playing_message();
        }
        else if (action.equals(EventHandler.EVENT_TRACK_CHANGED))
        {
            update_track((Track) data);
            update_position(current_position);
            update_state(current_state);
            update_whats_playing_message();
        }
        else if (action.equals(EventHandler.EVENT_PLAYLIST_CHANGED))
        {
            update_playlist((Playlist) data);
            update_track(current_track);
            update_position(current_position);
            update_state(current_state);
            update_whats_playing_message();
        }

        // else if (action.equals(EventHandler.EVENT_PLAYLIST_SOURCE_CHANGED))
        // {
        //     update_playlist_source();
        //     update_playlist(current_playlist);
        //     update_track(current_track);
        //     update_position(current_position);
        //     update_state(current_state);
        //     update_whats_playing_message();
        // }

        // oddball, no data member, not used yet
        // check back with artisan to see if there's a playlist source

        else if (action.equals(EventHandler.EVENT_RENDERER_CHANGED))
        {
            current_state = "";
            current_position = 0;
            current_track = null;
            current_playlist = null;

            setPlayListNames();
            update_playlist(current_playlist);
            update_track(current_track);
            update_position(current_position);
            update_state(current_state);
            update_whats_playing_message();
        }
    }


    // event handlers in order of minor to major changes


    private void update_whats_playing_message()
    {
        if (my_view != null)
        {
            Renderer renderer = artisan.getRenderer();
            String msg = "";
            if (renderer != null)
                msg += renderer.getName() + " :: ";
            if (current_playlist != null)
                msg += current_playlist.getName() + "(" +
                    current_playlist.getCurrentIndex() + "/" +
                    current_playlist.getNumTracks() + ") ";
            msg += current_state;
            TextView title = (TextView) artisan.findViewById(R.id.main_menu_text);
            title.setText(msg);
        }
    }



    private void update_position(int position)
    {
        current_position = position;

        if (!in_slider && my_view != null)
        {
            TextView position_text = (TextView) my_view.findViewById(R.id.track_elapsed);
            SeekBar track_slider = (SeekBar) my_view.findViewById(R.id.track_position_slider);

            if (current_track != null)
            {
                position_text.setText(Utils.durationToString(position,false));
                track_slider.setEnabled(true);
                track_slider.setMax(current_track.getDuration());
                track_slider.setProgress(current_position);
            }
            else
            {
                position_text.setText("");
                track_slider.setEnabled(false);
                track_slider.setMax(100);
                track_slider.setProgress(0);
            }
        }
    }


    private void update_state(String state)
    {
        current_state = state;
        if (my_view != null)
        {
            int image_id = android.R.drawable.ic_media_play;
            if (state.equals("PLAYING"))
                image_id = android.R.drawable.ic_media_pause;
            ImageView btn = (ImageView) my_view.findViewById(R.id.button_play_pause);
            btn.setImageResource(image_id);

            boolean enable_stop = current_track != null && !state.equals("STOPPED");
            enable(R.id.button_stop,enable_stop);
        }
    }


    void update_track(Track track)
    {
        current_state = "";
        current_position = 0;
        current_track = track;

        if (my_view != null)
        {
            String img_url = "";
            String title = "";
            String artist = "";
            String album_title = "";
            String tracknum = "";
            String genre = "";
            String year = "";
            String type = "";
            String duration_str = "";
            String art_uri = "";
            boolean enable_play = false;

            if (track != null)
            {
                enable_play = true;
                img_url = track.getLocalArtUri();
                title = track.getTitle();
                artist = track.getArtist();
                album_title = track.getAlbumTitle();
                tracknum = track.getTrackNum();
                genre = track.getGenre();
                year = track.getYearString();
                type = track.getType();
                duration_str = track.getDurationString(false);
                art_uri = track.getLocalArtUri();
            }

            enable(R.id.button_play_pause,enable_play);

            if (tracknum != "")
                tracknum = "Track " + tracknum;
            String info = tracknum + "     " + genre + "       " + year;

            ((TextView) my_view.findViewById(R.id.content_songtitle)).setText(title);
            ((TextView) my_view.findViewById(R.id.content_artistname)).setText(artist);
            ((TextView) my_view.findViewById(R.id.content_albumname)).setText(album_title);
            ((TextView) my_view.findViewById(R.id.content_trackinfo)).setText(info);
            ((TextView) my_view.findViewById(R.id.track_type)).setText(type);
            ((TextView) my_view.findViewById(R.id.track_duration)).setText(duration_str);

            ImageView img = (ImageView) my_view.findViewById(R.id.content_image);

            try
            {
                if (art_uri.equals(""))
                {
                    img.setImageResource(R.drawable.artisan);
                }
                else if (art_uri.equals("no_image.png"))
                {
                    img.setImageResource(R.drawable.no_image);
                }
                else if (art_uri.startsWith("file://"))
                {
                    String art_path = art_uri.replace("file://","");
                    FileInputStream fis = new FileInputStream(new File(art_path));
                    Drawable d = Drawable.createFromStream(fis,null);
                    img.setImageDrawable(d);
                }
                else
                {
                    new DownloadImageTask(img).execute(art_uri);
                }
            }
            catch(Exception e)
            {
                Utils.error("Could not set art image(" + art_uri + "): " + e);
            }
        }
    }


    void update_playlist(Playlist new_playlist)
    {
        current_state = "";
        current_position = 0;
        current_track = null;

        if (my_view != null)
        {
            if (current_playlist != null)
                setPlayListButtonSelected(current_playlist.getName(),false);
            if (new_playlist != null)
                setPlayListButtonSelected(new_playlist.getName(),true);

            boolean enable_next = false;
            if (new_playlist != null)
            {
                current_track = new_playlist.incGetTrack(0);
                enable_next = current_track != null && new_playlist.getNumTracks() > 0;
            }
            enable(R.id.button_next,enable_next);
            enable(R.id.button_prev,enable_next);
            enable(R.id.button_play_pause,current_track != null);
        }

        current_playlist = new_playlist;
    }



}   // class aPlaying
