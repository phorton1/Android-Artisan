package prh.artisan;

import android.app.ActionBar;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.HashMap;

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
    private ListView my_view = null;
    private PrefListAdapter pref_list_adapter = null;

    private TextView selected_renderer_name = null;
    private LinearLayout select_renderer_list = null;
    private boolean renderers_open = false;

    private boolean default_renderers_open = false;
    private RelativeLayout default_renderer_layout = null;
    private Button set_default_renderer = null;



    //private int button_id;
    //private String last_renderer = "";

    public String getName()  { return "Preferences"; }


    //----------------------------------------------
    // The main ListView (my_view)
    //----------------------------------------------

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
    public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle savedInstanceState)
    {
        Utils.log(dbg_aprefs,0,"aPrefs.onCreateView() called");

        // inflate my_view and set the PrefListAdapater

        my_view = (ListView) inflater.inflate(R.layout.activity_preferences, container, false);
        pref_list_adapter = new PrefListAdapter(this);
        my_view.setAdapter(pref_list_adapter);
        return my_view;
    }



    public class PrefListAdapter extends BaseAdapter
        // Fill out the preferences
        // The "VIRTUAL_PREFS" include the Select Renderer Item
    {
        private static final int NUM_VIRTUAL_PREFS = 1;
        HashMap<Integer, View> items = new HashMap<Integer,View>();
        aPrefs aprefs;


        public PrefListAdapter(aPrefs this_fragment)
        {
            aprefs = this_fragment;
        }

        public int getCount()
        {
            return NUM_VIRTUAL_PREFS + Prefs.id.values().length;
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

                // create the SelectRendererItem
                // which is filled out by populateRenderers()

                if (position == 0)
                {
                    item = inflater.inflate(R.layout.prefs_select_renderer,view_group,false);
                    selected_renderer_name = (TextView) item.findViewById(R.id.pref_selected_renderer);
                    select_renderer_list = (LinearLayout) item.findViewById(R.id.pref_renderer_list);
                    item.setOnClickListener(aprefs);
                    populateRenderers();
                }

                // "Normal" prefs in Prefs.id enum
                // The "Default Renderer" preference is an openable-relative layout

                else
                {
                    Prefs.id id = Prefs.id.values()[position-NUM_VIRTUAL_PREFS];
                    if (id == Prefs.id.DEFAULT_RENDERER)
                    {
                        // create it and set pref value like normal

                        item = inflater.inflate(R.layout.prefs_default_renderer,view_group,false);
                        item.setOnClickListener(aprefs);

                        String use_cur = Prefs.getString(id);
                        if (use_cur.isEmpty())
                            use_cur = "None";
                        TextView value = (TextView) item.findViewById(R.id.pref_default_renderer_value);
                        value.setText(use_cur);

                        // setup the dropdown RelativeLayout that lets one clear or set the default
                        // set the name on the button to the current renderer, if any
                        // set both click handlers and the initial showing state
                        // and hide it to start with

                        set_default_renderer = (Button) item.findViewById(R.id.set_default_renderer);
                        default_renderer_layout = (RelativeLayout) item.findViewById(R.id.pref_default_renderer_layout);

                        String use_name = "None";
                        Renderer renderer = artisan.getRenderer();
                        if (renderer != null)
                            use_name = renderer.getName();
                        set_default_renderer.setText(use_name);


                        Button clear_renderer_button = (Button) item.findViewById(R.id.clear_default_renderer);
                        Button last_selected_button = (Button) item.findViewById(R.id.set_default_renderer_last_selected);
                        clear_renderer_button.setOnClickListener(aprefs);
                        last_selected_button.setOnClickListener(aprefs);

                        set_default_renderer.setOnClickListener(aprefs);
                        setDefaultRendererLayoutShowing();

                    }

                    // Normal Pref List Items

                    else
                    {
                        item = inflater.inflate(R.layout.prefs_list_item,view_group,false);
                        TextView label = (TextView) item.findViewById(R.id.pref_list_item_label);
                        TextView value = (TextView) item.findViewById(R.id.pref_list_item_value);
                        label.setText(id.name());
                        value.setText(Prefs.getString(id));

                    }   // does not require special handling
                }   // is a Prefs.id enum item

                items.put(position,item);

            }   // item == null

            return item;

        }   // PrefListAdapter.getView()

    }   // class PrefListAdapter


    private void setDefaultRendererLayoutShowing()
    {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
            default_renderer_layout.getLayoutParams();
        if (default_renderers_open)
            params.height = -2;
        else
            params.height = 0;
        default_renderer_layout.setLayoutParams(params);
        pref_list_adapter.notifyDataSetChanged();
    }


    //-----------------------------------------------------
    // Populate Selected Renderer ListViewItem
    //-----------------------------------------------------
    // Which has a :inearLayout for the list of renderers

    private void populateRenderers()
    {
        // show the currently selected renderer

        Renderer selected_renderer = artisan.getRenderer();
        String selected_name = selected_renderer == null ? "" : selected_renderer.getName();
        selected_renderer_name.setText(selected_name.isEmpty() ? "None" : selected_name);

        // re-build the list of renderers if it's open.
        // If it's closed, the list is empty

        select_renderer_list.removeAllViews();
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
            select_renderer_list.getLayoutParams();

        if (renderers_open)
        {
            params.height = -2;
            DeviceManager device_manager = artisan.getDeviceManager();
            if (device_manager != null)
            {
                Device renderers[] = device_manager.getSortedDevices(Device.DEVICE_MEDIA_RENDERER);
                for (Device renderer:renderers)
                    addRenderer(renderer,selected_name);
            }
            Device renderer = (Device) artisan.getLocalRenderer();
            if (renderer != null)
                addRenderer(renderer,selected_name);
        }
        else
            params.height = 0;
        select_renderer_list.setLayoutParams(params);

    }



    private void addRenderer(Device renderer, String selected_name)
        // Add a renderer to the list of renderers
    {
        LayoutInflater inflater = LayoutInflater.from(artisan);
        View item = inflater.inflate(R.layout.pref_renderer_item,null,false);
        item.setOnClickListener(this);
        TextView name = (TextView) item.findViewById(R.id.pref_list_renderer_name);

        // set the color to blue if it's the selected renderer

        if (selected_name.equals(renderer.getFriendlyName()))
        {
            set_default_renderer.setText(renderer.getFriendlyName());
            name.setTextColor(Color.BLUE);
        }
        else
            name.setTextColor(Color.WHITE);
        name.setText(renderer.getFriendlyName());

        // fire-off asynch task to get the icon

        String icon = renderer.getIconUrl();
        if (!icon.isEmpty())
        {
            IconLoader IconLoader = new IconLoader(item,icon);
            Thread icon_thread = new Thread(IconLoader);
            icon_thread.start();
        }
        select_renderer_list.addView(item);
    }


    public class IconLoader extends Thread
        // The icon loader must run on a separate thread from the UI
        // but the View must be updated from the UI thread ...
    {
        View item_view;
        String icon_url;

        IconLoader(View item, String icon)
        {
            item_view = item;
            icon_url = icon;
        }

        public void run()   // NOT the ui-thread
        {
            synchronized (artisan)
            {
                Bitmap icon_bitmap = null;
                try
                {
                    InputStream in = new java.net.URL(icon_url).openStream();
                    icon_bitmap = BitmapFactory.decodeStream(in);
                }
                catch (Exception e)
                {
                    Utils.warning(0,0,"Could not load image:" + e.getMessage());
                }

                // call back to the UI thread

                if (icon_bitmap != null)
                    setItemIcon(item_view,icon_bitmap);
            }
        }
    }



    private void setItemIcon(final View item, final Bitmap icon_bitmap)
        // runOnUiThread() to set bitmap into image view
    {
        artisan.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                ImageView icon_image = (ImageView) item.findViewById(R.id.pref_list_renderer_icon);
                icon_image.setImageDrawable(
                    new BitmapDrawable(icon_bitmap));
            }

        });
    }


    //------------------------------------------------
    // onClick
    //------------------------------------------------

    @Override
    public void onClick(View v)
    {
        Renderer renderer = artisan.getRenderer();
        if (renderer == null) return;
        int id = v.getId();

        //-------------------------------------------
        // Default Renderer
        //-------------------------------------------
        // Open or close the layout that lets you pick the default renderer

        if (id == R.id.pref_default_renderer_item)
        {
            default_renderers_open = !default_renderers_open;
            setDefaultRendererLayoutShowing();
        }

        else if (id == R.id.set_default_renderer_last_selected)
        {
            Prefs.putString(Prefs.id.DEFAULT_RENDERER,Prefs.LAST_SELECTED);
            TextView value = (TextView) my_view.findViewById(R.id.pref_default_renderer_value);
            value.setText(Prefs.LAST_SELECTED);
        }

        else if (id == R.id.clear_default_renderer)
        {
            Prefs.putString(Prefs.id.DEFAULT_RENDERER,"");
            TextView value = (TextView) my_view.findViewById(R.id.pref_default_renderer_value);
            value.setText("None");
        }

        else if (id == R.id.set_default_renderer)
        {
            String name = set_default_renderer.getText().toString();
            Prefs.putString(Prefs.id.DEFAULT_RENDERER,name);
            TextView value = (TextView) my_view.findViewById(R.id.pref_default_renderer_value);
            value.setText(name);
        }


        //-------------------------------------------
        // Select Renderer
        //-------------------------------------------
        // Open or close the list of Renderers

        else if (id == R.id.pref_select_renderer)
        {
            renderers_open = !renderers_open;

            if (false)
            {
                // if opening the renderers,
                // start an SSDP Search if not in progress already
                if (renderers_open)
                {
                    DeviceManager device_manager = artisan.getDeviceManager();
                    if (device_manager != null)
                        device_manager.doDeviceSearch();
                }
            }

            populateRenderers();
            pref_list_adapter.notifyDataSetChanged();
        }


        // Select a Renderer

        else if (id == R.id.pref_renderer_item)
        {
            TextView name_field = (TextView) v.findViewById(R.id.pref_list_renderer_name);
            String name = name_field.getText().toString();
            if (artisan.setRenderer(name))
                artisan.getViewPager().setCurrentItem(1);
            populateRenderers();
        }

    }



    //------------------------------------------------
    // Artisan Event Handling
    //------------------------------------------------

    public void handleArtisanEvent( String event_id, Object data )
        // We basically rebuild the whole list on any events
    {
        if (event_id.equals(EVENT_RENDERER_CHANGED))
        {
            String display = "";
            Renderer renderer = (Renderer) data;
            if (renderer != null)
                display = renderer.getName();
            //((TextView)my_view.findViewById(R.id.pref_renderer_selected)).setText(display);
            // SetupSelectRenderer();
        }
        else if (event_id.equals(EVENT_NEW_DEVICE))
        {
            // pref_list_adapter.notifyDataSetChanged();
            populateRenderers();
        }

    }




}   // class aPrefs
