package prh.artisan;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import prh.device.Device;
import prh.base.ArtisanEventHandler;
import prh.types.intViewHash;


public class MainMenu extends ListView implements
    ArtisanEventHandler,
    View.OnClickListener
{
    private final static int NUM_MENU_ITEMS = 5;
    private final static int FIRST_DEVICE = 1;
    private final static int LAST_DEVICE = 3;

    private Artisan artisan;
    private intViewHash items = new intViewHash();


    public MainMenu(Context context,AttributeSet attrs)
    {
        super(context,attrs);
        artisan = (Artisan) getContext();
    }

    @Override public void onFinishInflate()
    {
        setAdapter(new mainMenuListAdapter());
        //setItemsCanFocus(true);
    }


    public class mainMenuListAdapter extends BaseAdapter
        // Caching of views WORKS if you disable recycling
    {
        private intViewHash my_items = new intViewHash();
            // cache of views

        // require BaseAdapater methods

        @Override public int getCount()
        {
            return NUM_MENU_ITEMS;
        }

        @Override public long getItemId(int position)
        {
            return position;
        }

        // implementing these two methods disables
        // view recycling by the ListView which
        // allows the cached items to work, by
        // essentially telling the list view that
        // all objects are of unique types and cannot
        // be recycled.

        @Override
        public int getViewTypeCount()
        {
            return getCount();
        }

        @Override
        public int getItemViewType(int position)
        {
            return position;
        }


        // View Getters
        // I still don't quite understand why caching did not work
        // without disabling view recycling.  I think it's because
        // the ListView removes any handlers (onClick) before it
        // "recylces" the view.  Also note that the BaseAdapter does
        // not receive the view being recycled (the API for
        // ArrayListAdapter is different.

        @Override public View getItem(int position)
        {
            return getView(position,null,null);
        }

        @Override
        public View getView(int position, View parent, ViewGroup view_group)
        {
            View item = items.get(position);
            if (item == null)
            {
                LayoutInflater inflater;
                switch (position)
                {
                    case (0):
                        inflater = LayoutInflater.from(artisan);
                        item = inflater.inflate(R.layout.main_menu_top_commands,view_group,false);
                        ((TextView)item.findViewById(R.id.menu_exit)).
                            setOnClickListener(MainMenu.this);
                        ((TextView)item.findViewById(R.id.clear_devices)).
                            setOnClickListener(MainMenu.this);
                        ((TextView)item.findViewById(R.id.refresh_devices)).
                            setOnClickListener(MainMenu.this);
                        ((ProgressBar)item.findViewById(R.id.ssdp_progress)).
                            setVisibility( artisan.getDeviceManager().searchInProgress() ?
                                View.VISIBLE : View.GONE );
                        break;
                    case (1):
                        item = createSelectDevice("Renderer",Device.deviceGroup.DEVICE_GROUP_RENDERER,Artisan.PAGE_PLAYING);
                        break;
                    case (2):
                        item = createSelectDevice("Library",Device.deviceGroup.DEVICE_GROUP_LIBRARY,Artisan.PAGE_LIBRARY);
                        break;
                    case (3):
                        item = createSelectDevice("PlaylistSource",Device.deviceGroup.DEVICE_GROUP_PLAYLIST_SOURCE,-1);
                        break;
                    case (4):
                        inflater = LayoutInflater.from(artisan);
                        item = inflater.inflate(R.layout.main_menu_settings_button,view_group,false);
                        item.setOnClickListener(MainMenu.this);
                        break;
                }
                items.put(position,item);
            }

            return item;
        }

    }   // class mainMenuListAdapter



    private SelectDevice createSelectDevice(String group_name, Device.deviceGroup group, int goto_page)
    {
        LayoutInflater inflater = LayoutInflater.from(artisan);
        SelectDevice sel = (SelectDevice) inflater.inflate(R.layout.select_device,this,false);
        sel.setup(this,group_name,group,goto_page);
        return sel;
    }


    @Override public void onClick(View v)
        // This handles non-SelectDevice menu item clicks.
        // SelectDevices handle their own clicks.
    {
        int id = v.getId();
        switch (id)
        {
            case R.id.menu_exit :
                artisan.finish();
                break;
            case R.id.clear_devices :
            case R.id.refresh_devices :
                if (!artisan.getDeviceManager().canDoDeviceSearch())
                    Toast.makeText(artisan,"There is already device\nsearch in progress",Toast.LENGTH_LONG).show();
                else
                    artisan.getDeviceManager().doDeviceSearch(
                        id == R.id.clear_devices ? true : false);
                break;
            case R.id.menu_settings:
                artisan.showPrefsPageModal();
                artisan.hideMainMenu();
                break;
        }
    }   // onItemClick()


    public void init()
    {
        View v = ((mainMenuListAdapter) getAdapter()).getItem(0);
        View progress = v.findViewById(R.id.ssdp_progress);
        progress.setVisibility( artisan.getDeviceManager().searchInProgress() ?
            View.VISIBLE : View.GONE );
        for (int i = FIRST_DEVICE; i <= LAST_DEVICE; i++)
        {
            SelectDevice sel = (SelectDevice)
                ((mainMenuListAdapter) getAdapter()).getItem(i);
            sel.init();
        }
    }


    // ARTISAN EVENTS

    @Override
    public void handleArtisanEvent(String event_id, Object data)
    {
        if (event_id.equals(EVENT_SSDP_SEARCH_STARTED) ||
            event_id.equals(EVENT_SSDP_SEARCH_FINISHED))
        {
            View v = ((mainMenuListAdapter) getAdapter()).getItem(0);
            View progress = v.findViewById(R.id.ssdp_progress);
            progress.setVisibility( event_id.equals(EVENT_SSDP_SEARCH_STARTED) ?
                View.VISIBLE : View.GONE );
        }
        else
        if (event_id.equals(EVENT_NEW_DEVICE) ||
            event_id.equals(EVENT_DEVICE_STATUS_CHANGED) ||
            event_id.equals(EVENT_LIBRARY_CHANGED) ||
            event_id.equals(EVENT_RENDERER_CHANGED) ||
            event_id.equals(EVENT_PLAYLIST_SOURCE_CHANGED))
        {
            for (int i = FIRST_DEVICE; i <= LAST_DEVICE; i++)
            {
                SelectDevice sel = (SelectDevice)
                    ((mainMenuListAdapter) getAdapter()).getItem(i);
                sel.handleArtisanEvent(event_id,data);
            }
        }
    }


}   // class MainMenu
