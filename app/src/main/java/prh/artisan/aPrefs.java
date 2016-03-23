package prh.artisan;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import prh.device.Device;
import prh.types.intViewHash;
import prh.utils.Utils;


public class aPrefs extends Fragment implements
    ArtisanPage,
    View.OnClickListener,
    EventHandler
{
    private static int dbg_aprefs = 0;
    private Artisan artisan = null;
    private LinearLayout my_view = null;
    private PrefListAdapter pref_list_adapter = null;

    @Override public String getName()
    {
        return "Preferences";
    }


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

        my_view = (LinearLayout) inflater.inflate(R.layout.activity_preferences, container, false);
        ListView list = (ListView) my_view.findViewById(R.id.preferences_list_view);
        pref_list_adapter = new PrefListAdapter();
        list.setAdapter(pref_list_adapter);
        return my_view;
    }


    //-------------------------------------
    // PrefListAdapter
    //-------------------------------------

    public class PrefListAdapter extends BaseAdapter
        // Fill out the preferences
        // The "VIRTUAL_PREFS" include the Select Renderer Item
    {
        private intViewHash items = new intViewHash();

        public int getCount()
        {
            return Prefs.id.values().length;
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
                Prefs.id id = Prefs.id.values()[position];

                // Default Device Preferences

                if (id == Prefs.id.DEFAULT_LIBRARY)
                {
                    item = inflater.inflate(R.layout.prefs_default_device,view_group,false);
                    DefaultDevicePref pref = new DefaultDevicePref(artisan,id,item,
                        "Library",Device.deviceGroup.DEVICE_GROUP_LIBRARY);
                    item.setTag(pref);
                }
                else if (id == Prefs.id.DEFAULT_RENDERER)
                {
                    item = inflater.inflate(R.layout.prefs_default_device,view_group,false);
                    DefaultDevicePref pref = new DefaultDevicePref(artisan,id,item,
                        "Renderer",Device.deviceGroup.DEVICE_GROUP_RENDERER);
                    item.setTag(pref);
                }
                else if (id == Prefs.id.DEFAULT_PLAYLIST_SOURCE)
                {
                    item = inflater.inflate(R.layout.prefs_default_device,view_group,false);
                    DefaultDevicePref pref = new DefaultDevicePref(artisan,id,item,
                        "PlaylistSource",Device.deviceGroup.DEVICE_GROUP_PLAYLIST_SOURCE);
                    item.setTag(pref);
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

                items.put(position,item);

            }   // item == null

            return item;

        }   // PrefListAdapter.getView()

    }   // class PrefListAdapter



    //------------------------------------------------
    // onClick
    //------------------------------------------------
    // currently not used

    @Override
    public void onClick(View v)
    {
        int id = v.getId();
    }



    //-------------------------------------
    // Default Device
    //-------------------------------------

    private class DefaultDevicePref implements View.OnClickListener
    {
        Artisan artisan;
        aPrefs aprefs;
        Prefs.id pref_id;
        View item;
        String thing;
        Device.deviceGroup group;
        boolean default_open = false;

        public DefaultDevicePref(Artisan ma,Prefs.id id, View it,String th,Device.deviceGroup dg)
        {
            artisan = ma;
            pref_id = id;
            item = it;
            thing = th;
            group = dg;

            ((TextView) item.findViewById(R.id.pref_default_device_type))
                .setText("Default " + thing);

            populateDefault();

            item.setOnClickListener(this);
            ((Button) item.findViewById(R.id.pref_clear_default_device))
                .setOnClickListener(this);
            ((Button) item.findViewById(R.id.pref_set_default_last_selected))
                .setOnClickListener(this);
            ((Button) item.findViewById(R.id.pref_set_default_device))
                .setOnClickListener(this);
        }


        public void populateDefault()
        {
            // show the current preference

            String use_cur = Prefs.getString(pref_id);
            if (use_cur.isEmpty())
                use_cur = "None";
            ((TextView) item.findViewById(R.id.pref_default_device_name))
                .setText(use_cur);

            // set the label on the button to the current device
            // from artisan, and if it's empty, we hide the button

            Device current_device = artisan.getCurrentDevice(group);
            String selected_name = current_device == null ? "" :
                current_device.getFriendlyName();
            Button button = (Button) item.findViewById(R.id.pref_set_default_device);
            ViewGroup.LayoutParams button_params = button.getLayoutParams();
            if (selected_name.isEmpty())
            {
                button_params.width = 0;
            }
            else
            {
                button_params.width = -2;
                button.setText(selected_name);
            }
            button.setLayoutParams(button_params);

            // hide or show the whole thing

            RelativeLayout default_layout = (RelativeLayout)
                item.findViewById(R.id.pref_default_device_layout);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
                default_layout.getLayoutParams();
            if (default_open)
                params.height = -2;
            else
                params.height = 0;
            default_layout.setLayoutParams(params);

            // pref_list_adapter.notifyDataSetChanged();
        }


        public void onClick(View v)
        {
            int id = v.getId();
            TextView default_name = (TextView) item.findViewById(R.id.pref_default_device_name);

            if (id == R.id.pref_default_device_item)
            {
                default_open = !default_open;
                populateDefault();
            }
            else if (id == R.id.pref_set_default_last_selected)
            {
                Prefs.putString(pref_id,Prefs.LAST_SELECTED);
                default_name.setText(Prefs.LAST_SELECTED);
                default_open = !default_open;
                populateDefault();
            }
            else if (id == R.id.pref_clear_default_device)
            {
                Prefs.putString(pref_id,"");
                default_name.setText("None");
                default_open = !default_open;
                populateDefault();
            }
            else if (id == R.id.pref_set_default_device)
            {
                String name = ((Button) v).getText().toString();
                Prefs.putString(pref_id,name);
                default_name.setText(name);
                default_open = !default_open;
                populateDefault();
            }
        }

    }   // class DefaultDevicePref




    //------------------------------------------------
    // Artisan Event Handling
    //------------------------------------------------

    @Override public void handleArtisanEvent( String event_id, Object data )
    {
        // no event handling till there's a view

        if (my_view == null)
            return;

        // in all these cases, the data is a Device
        // from which can get it's group, and thus it's preferences

        if (event_id.equals(EVENT_NEW_DEVICE) ||
            event_id.equals(EVENT_LIBRARY_CHANGED) ||
            event_id.equals(EVENT_RENDERER_CHANGED) ||
            event_id.equals(EVENT_PLAYLIST_SOURCE_CHANGED))
        {
            Device device = (Device) data;
            Device.deviceGroup group = device.getDeviceGroup();

            int select_position;
            int default_position;

            if (group.equals(Device.deviceGroup.DEVICE_GROUP_LIBRARY))
            {
                select_position = Prefs.id.SELECTED_LIBRARY.ordinal();
                default_position = Prefs.id.DEFAULT_LIBRARY.ordinal();
            }
            else if (group.equals(Device.deviceGroup.DEVICE_GROUP_RENDERER))
            {
                select_position = Prefs.id.SELECTED_RENDERER.ordinal();
                default_position = Prefs.id.DEFAULT_RENDERER.ordinal();
            }
            else // DEVICE_GROUP_PLAYLIST_SOURCE
            {
                select_position = Prefs.id.SELECTED_PLAYLIST_SOURCE.ordinal();
                default_position = Prefs.id.DEFAULT_PLAYLIST_SOURCE.ordinal();
            }

            // We basically rebuild the whole list on any events

            DefaultDevicePref dpref = (DefaultDevicePref) pref_list_adapter
                .getItem(default_position).getTag();

            dpref.populateDefault();
        }

    }   // handleArtisanEvent()




}   // class aPrefs
