package prh.artisan;

// The activity should have control of the title bar.
// I'd like it switch to "Preferences" when entering this


import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import prh.device.Device;
import prh.device.DeviceManager;
import prh.utils.Utils;


public class aPrefs extends Fragment implements
    ArtisanPage,
    View.OnClickListener,
    EventHandler
{
    private static int dbg_aprefs = 0;
    private Artisan artisan = null;
    private View my_view = null;
    private int button_id;

    public String getName()  { return "Preferences"; }

    //----------------------------------------------
    // life cycle
    //----------------------------------------------

    @Override
    public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle savedInstanceState)
    {
        Utils.log(dbg_aprefs,0,"aPrefs.onCreateView() called");
        my_view = inflater.inflate(R.layout.activity_preferences, container, false);

        my_view.findViewById(R.id.pref_renderer_label).setOnClickListener(this);
        my_view.findViewById(R.id.pref_refresh_renderers).setOnClickListener(this);
        my_view.findViewById(R.id.pref_clear_renderers).setOnClickListener(this);

        // all the buttons get the same id, then
        // we look at the text when they are pressed

        button_id = View.generateViewId();

        // disappear the renderer buttons

        LinearLayout select_renderer = ((LinearLayout)my_view.findViewById(R.id.pref_select_renderer));
        ViewGroup.LayoutParams params = select_renderer.getLayoutParams();
        params.height = 0;
        select_renderer.setLayoutParams(params);

        // set the starting renderer name

        String display = "No Renderer Selected!";
        if (artisan.getRenderer() != null)
            display = artisan.getRenderer().getName();
        ((TextView)my_view.findViewById(R.id.pref_renderer_selected)).setText(display);
        return my_view;
    }

    @Override
    public void onAttach(Activity activity)
    {
        Utils.log(dbg_aprefs,0,"aPrefs.onAttach() called");
        super.onAttach(activity);
        artisan = (Artisan) activity;
    }


    @Override
    public void onDetach()
    {
        Utils.log(dbg_aprefs,0,"aPrefs.onDetach() called");
        super.onDetach();
        artisan = null;
    }


    @Override
    public void onClick(View v)
    {
        Renderer renderer = artisan.getRenderer();
        if (renderer == null) return;
        int id = v.getId();

        if (id == R.id.pref_renderer_label)
        {
            LinearLayout select_renderer = ((LinearLayout)my_view.findViewById(R.id.pref_select_renderer));
            ViewGroup.LayoutParams params = select_renderer.getLayoutParams();
            if (params.height == 0)     // not showing
            {
                params.height = -2;     // show it
                SetupSelectRenderer();
            }
            else
                params.height = 0;      // hide it

            select_renderer.setLayoutParams(params);

            Utils.log(0,0,"SELECT RENDERER");
        }

        else if (id == R.id.pref_clear_renderers)
        {

        }
        else if (id == R.id.pref_refresh_renderers)
        {

        }
        else if (id == button_id)
        {
            // ask artisan to start the renderer,
            // then re-display ourself

            String name = ((Button)v).getText().toString();
            artisan.setRenderer(name);
            SetupSelectRenderer();

        }

    }


    //------------------------------------------------
    // Preference Handlers
    //------------------------------------------------

    private void SetupSelectRenderer()
    {
        DeviceManager device_manager = artisan.getDeviceManager();
        LinearLayout renderer_list = ((LinearLayout) my_view.findViewById(R.id.pref_renderer_list));
        renderer_list.removeAllViews();

        // cache the selected renderer name

        String selected_name = "";
        if (artisan.getRenderer() != null)
            selected_name = artisan.getRenderer().getName();

        // add MediaRenderer Devices to the list and sort them

        ArrayList<String> names = new ArrayList<String>();
        DeviceManager.DeviceHash renderers = device_manager.getDevices(Device.DEVICE_MEDIA_RENDERER);
        if (renderers != null)
        {
            for (Device device : renderers.values())
                names.add(device.getFriendlyName());
            Collections.sort(names);
        }

        // add the LocalRenderer

        if (artisan.getLocalRenderer() != null)
            names.add(artisan.getLocalRenderer().getName());

        // if the list is empty, show a message

        // ViewGroup.LayoutParams bparams = new ViewGroup.LayoutParams(-1,-1);
        if (names.size() == 0)
        {
            ((TextView) my_view.findViewById(R.id.pref_no_renderers)).setText("No Renderers Found!");
        }
        else
        {
            for (String name : names)
            {
                Boolean selected = name.equals(selected_name);

                Button button = new Button(artisan);
                button.setId(button_id);
                button.setText(name);
                button.setTextSize(14);
                button.setWidth(100);
                button.setHeight(36);
                button.setSelected(selected);
                if (selected)
                    button.setTextColor(Color.BLUE);
                button.setOnClickListener(this);
                renderer_list.addView(button);
            }
        }
    }


    //------------------------------------------------
    // Event Handling
    //------------------------------------------------

    public void handleArtisanEvent( String action, Object data )
    {
        if (action.equals(EVENT_RENDERER_CHANGED))
        {
            String display = "";
            if (data != null)
                display = ((Renderer)data).getName();
           ((TextView)my_view.findViewById(R.id.pref_renderer_selected)).setText(display);

        }
    }




}   // class aPrefs
