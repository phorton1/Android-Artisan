package prh.artisan;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;

import android.support.v4.app.Fragment;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MenuItem;
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

import prh.types.intList;
import prh.types.stringList;
import prh.utils.ImageLoader;
import prh.utils.Utils;


public class aRenderer extends Fragment implements
    ArtisanPage,
    EventHandler,
    View.OnClickListener
{

    private static int dbg_anp = 1;

    // only good while attached

    private Artisan artisan = null;
    private TextView page_title = null;

    // valid for the life of the object
    // between onCreate and onDestroy

    private View my_view = null;
    private PlayListButtonAdapter buttonAdapter = null;
    private Renderer renderer;

    // state
    // NEVER NULL current_playlist and current_playlist_source

    private int current_position = 0;
    private String current_state = "";
    private Track current_track = null;

    // working vars

    private boolean in_slider = false;


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
        Utils.log(dbg_anp,0,"aRenderer.onCreateView() called");
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

        // not sufficient to just set members
        // also need to update the ui in case
        // renderer selected at startup, so we
        // call our own event handler to set
        // everything up

        handleArtisanEvent(EventHandler.EVENT_RENDERER_CHANGED,
            artisan.getRenderer());

        return my_view;
    }


    @Override
    public void onAttach(Activity activity)
    {
        Utils.log(dbg_anp,0,"aRenderer.onAttach() called");
        super.onAttach(activity);
        // artisan = (Artisan) activity;
    }


    @Override
    public void onDetach()
    {
        Utils.log(dbg_anp,0,"aRenderer.onDetach() called");
        super.onDetach();
        // artisan = null;
        buttonAdapter = null;
    }


    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
    }


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

    @Override public void onSetPageCurrent(boolean current)
    {
        page_title = null;
        if (current)
        {
            page_title = new TextView(artisan);
            page_title.setText(getTitleBarText());
            artisan.setArtisanPageTitle(page_title);
        }
    }

    private String getTitleBarText()
    {
        String msg = renderer == null ?
            "Now Playing " :
            renderer.getRendererName() + " ";

        CurrentPlaylist cur = artisan.getCurrentPlaylist();
        msg += ":: " +
            cur.getName();
        if (cur.isDirty())
            msg += "*";
        if (cur.getCurrentIndex()>0)
            msg += "(" +
                cur.getCurrentIndex() + "/" +
                cur.getNumTracks() + ") ";

        // clean up PAUSED_PLAYBACK for display
        msg += current_state.replace("_PLAYBACK","");
        return msg;
    }

    // event handlers in order of minor to major changes

    private void updateTitleBar()
    {
        if (page_title != null)
            page_title.setText(getTitleBarText());
    }



    //---------------------------
    // Button Handlers
    //---------------------------

    @Override
    public void onClick(View v)
    {
        // Control click handlers call back to Artisan
        // onBodyClicked() and eat the click if it returns true.

        if (artisan.onBodyClicked())
            return;

        // Can't do anything if there's no renderer

        if (renderer == null)
            return;

        // Handle the button click

        switch (v.getId())
        {
            case R.id.button_play_pause:
                if (renderer.getRendererState().equals(Renderer.RENDERER_STATE_PLAYING))
                    renderer.transport_pause();
                else
                    renderer.transport_play();
                break;
            case R.id.button_stop:
                renderer.transport_stop();
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
            Utils.log(dbg_anp+3,0,"onProgressChanged(" + progresValue + ")");
            progress = progresValue;
            String time_str = Utils.durationToString(progress,Utils.how_precise.FOR_DISPLAY);
            TextView position_text = (TextView) my_view.findViewById(R.id.track_elapsed);
            position_text.setText(time_str);

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
            if (renderer != null)
                renderer.seekTo(progress);
        }
    };


    //-----------------------------------------------
    // Sliding Playlist Buttons
    //-----------------------------------------------
    // uses an "Adapter" which works with GridView
    // to supply the views for each button, and each
    // button gets a ClickListener ...

    public void setPlayListNames()
    {
        // get the names, if any, from the playlist_source

        PlaylistSource source = artisan.getPlaylistSource();
        stringList names = source.getPlaylistNames();
        GridView gridview = (GridView) my_view.findViewById(R.id.horizontal_gridView);

        // setup the gridview

        int width = 90;
        DisplayMetrics dm = new DisplayMetrics();
        artisan.getWindowManager().getDefaultDisplay().getMetrics(dm);
        float density = dm.density;
        int totalWidth = (int) (width * names.size() * density);
        int singleItemWidth = (int) (width * density);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            totalWidth,LinearLayout.LayoutParams.MATCH_PARENT);

        gridview.setLayoutParams(params);
        gridview.setColumnWidth(singleItemWidth);
        gridview.setHorizontalSpacing(2);
        gridview.setStretchMode(GridView.STRETCH_SPACING);
        gridview.setNumColumns(names.size());

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
        public stringList playlist_names;

        PlayListButtonAdapter(stringList names)
        {
            playlist_names = names;
        }
        public Button getButton(String name)
        {
            for (int i=0; i < playlist_names.size(); i++)
            {
                if (playlist_names.get(i).equals(name))
                {
                    return (Button) getItem(i);
                }
            }
            return null;
        }

        // All remaining methods are necessary overrides of abstract base class.

        public int getCount()
        {
            return playlist_names.size();
        }

        public Object getItem(int position)
        {
            if (true)
                return my_view.findViewById(position + ID_BASE);
            else
            {
                GridView gridview = (GridView) my_view.findViewById(R.id.horizontal_gridView);
                return getView(position,null,gridview);
            }
        }

        public long getItemId(int position)
        {
            return position;
        }


        public View getView(int position, View exists, ViewGroup parent)
        // return the button ("view") for each position.
        // may already exist, in which case we are re-using it
        // and merely need to change the label
        {
            Button btn;
            if (exists == null)
            {
                Utils.log(dbg_anp+1,3,"new playlist button " + playlist_names.get(position));
                btn = new Button(my_view.getContext());
                btn.setLayoutParams(new GridView.LayoutParams(120, 70));
                // params are width, height of the button
                btn.setPadding(8, 8, 8, 8);
            }
            else
            {
                btn = (Button) exists;
            }

            String name = playlist_names.get(position);
            Utils.log(dbg_anp + 2,3,"playlist button(" + position + ")=" + name);

            btn.setTextSize(10);
            btn.setOnClickListener(new View.OnClickListener()
            {
                public void onClick(View v)
                {
                    int position = v.getId() - ID_BASE;

                    Utils.log(dbg_anp,2,"onClick(" + v.getId() + ") position=" + position);
                    String name = playlist_names.get(position);
                    Utils.log(dbg_anp,3,"playlist name=" + name);
                    artisan.setPlaylist(name,false);
                        // tell artisan that the playlist has changed
                        // it will event the change back to us
                }
            });

            if (name.equals(""))
                name = "default";

            CurrentPlaylist current_playlist = artisan.getCurrentPlaylist();
            btn.setTextColor(name.equals(current_playlist.getName())? 0xFFff9900:Color.WHITE);
            btn.setText(name);
            btn.setId(ID_BASE + position);
            return btn;
        }
    }


    //-----------------------------------------------------------
    // Event Handling
    //-----------------------------------------------------------


    private void update_position(int position)
    {
        current_position = position;
        if (!in_slider && my_view != null)
        {
            TextView position_text = (TextView) my_view.findViewById(R.id.track_elapsed);
            // TextView duration_text = (TextView) my_view.findViewById(R.id.track_duration);

            SeekBar track_slider = (SeekBar) my_view.findViewById(R.id.track_position_slider);

            if (current_track != null)
            {
                int cur_duration = current_track.getDuration();
                position_text.setText(Utils.durationToString(position,Utils.how_precise.FOR_DISPLAY));
                // duration_text.setText(Utils.durationToString(cur_duration,Utils.how_precise.FOR_DISPLAY));
                track_slider.setEnabled(true);
                track_slider.setMax(cur_duration);
                track_slider.setProgress(current_position);
            }
            else
            {
                position_text.setText("");
                // duration_text.setText("");
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
            int image_id = R.drawable.my_ic_media_play;
            if (state.equals(Renderer.RENDERER_STATE_PLAYING))
                image_id = R.drawable.my_ic_media_pause;
            ImageView btn = (ImageView) my_view.findViewById(R.id.button_play_pause);
            btn.setImageResource(image_id);

            boolean enable_stop = /* current_track != null && */
                !state.equals(Renderer.RENDERER_STATE_NONE) &&
                !state.equals(Renderer.RENDERER_STATE_STOPPED);
            enable(R.id.button_stop,enable_stop);
        }
    }


    private void update_track(Track track)
    {
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

            current_track = track;

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
                duration_str = track.getDurationString(Utils.how_precise.FOR_DISPLAY);
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
                if (track == null)
                {
                    img.setImageResource(R.drawable.artisan);
                }
                else if (art_uri.isEmpty())
                {
                    img.setImageResource(R.drawable.no_image);
                }
                else
                {
                    ImageLoader.loadImage(artisan,img,art_uri);
                }
            }
            catch(Exception e)
            {
                Utils.error("Could not set art image(" + art_uri + "): " + e);
            }
        }
    }


    private void update_playlist()
    {
        if (my_view != null)
        {
            CurrentPlaylist cur = artisan.getCurrentPlaylist();
            //setPlayListButtonSelected(cur.getName(),true);

            boolean enable_next =
                current_track != null &&
                cur.getNumTracks() > 0;

            enable(R.id.button_next,enable_next);
            enable(R.id.button_prev,enable_next);
            enable(R.id.button_play_pause,current_track != null);
        }
    }



    @Override public void handleArtisanEvent(String event_id,Object data)
    // handle changes ...
    // in order of most minor, to most major changes
    // where major changes require more updating.
    {
        if (event_id.equals(EVENT_POSITION_CHANGED))
        {
            update_position((Integer) data);
        }
        else if (event_id.equals(EVENT_STATE_CHANGED))
        {
            update_state((String) data);
            updateTitleBar();
        }
        else if (event_id.equals(EVENT_TRACK_CHANGED))
        {
            current_state = renderer.getRendererState();
            current_position = renderer.getPosition();

            update_track((Track) data);
            update_position(current_position);
            update_state(current_state);
            updateTitleBar();
        }
        else if (event_id.equals(EVENT_PLAYLIST_CHANGED))
        {
            current_state = renderer.getRendererState();
            current_position = renderer.getPosition();
            current_track = renderer.getRendererTrack();

            setPlayListNames();

            update_playlist();
            update_track(current_track);
            update_position(current_position);
            update_state(current_state);
            updateTitleBar();

        }


        // if the renderer changed, we move the playing track
        // over to the new renderer. This is complicated enough
        // but really complicated for openHome renderers.
        // FOR NOW JUST SWITCHES TO THE CORRECT RENDERER

        else if (event_id.equals(EVENT_RENDERER_CHANGED))
        {
            renderer = (Renderer) data;

            current_state = "";
            current_position = 0;
            current_track = null;

            if (renderer != null)
            {
                current_state = renderer.getRendererState();
                current_position = renderer.getPosition();
                current_track = renderer.getRendererTrack();
           }

            update_track(current_track);
            update_position(current_position);
            update_state(current_state);
            updateTitleBar();
        }

        else if (event_id.equals(EVENT_PLAYLIST_SOURCE_CHANGED))
        {
            update_playlist();
            setPlayListNames();
        }

        else if (event_id.equals(COMMAND_EVENT_PLAY_TRACK))
        {
            if (renderer != null)
            {
                Track track = (Track) data;
                renderer.setRendererTrack(track,false);
            }
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


}   // class aRenderer
