package prh.artisan;

//-------------------------------------
// Select Device
//-------------------------------------

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import prh.device.Device;
import prh.device.DeviceManager;
import prh.utils.ImageLoader;
import prh.utils.Utils;


public class SelectDevice extends RelativeLayout implements
    EventHandler,
    View.OnClickListener
{
    private Artisan artisan;
    private MainMenu main_menu;

    private String group_name;
    private Device.deviceGroup group;
    private int goto_page;
    private boolean list_open = false;


    public SelectDevice(Context context,AttributeSet attrs)
    {
        super(context,attrs);
        artisan = (Artisan) context;
    }


        /****************************************************
         interesting, but unused, example, of how to pass
         attributes from XML to ctor. See /res/values/attr.xml
         for type definitions, and the unused_main_menu.xmlenu.xml layout
         for example implementation
         ****************************************************

        TypedArray a = context.getTheme().obtainStyledAttributes(
            attrs,R.styleable.SelectDeviceItem,0,0);
        try
        {
            group_name = a.getString(R.styleable.SelectDeviceItem_deviceGroupName);
            goto_page = a.getInt(R.styleable.SelectDeviceItem_onSelectedPage,-1);
            group = Device.deviceGroup.valueOf(a.getString(R.styleable.SelectDeviceItem_deviceGroup));
        }
        catch (Exception e)
        {
            Utils.error("Could not get SelectDevice attributes: " + e.toString());
        }
        a.recycle();

         *******************************************************/



    @Override public void onFinishInflate()
    {
    }


    public void setup(MainMenu menu, String name, Device.deviceGroup dg, int page)
    {
        main_menu = menu;
        group_name = name;
        group = dg;
        goto_page = page;

        TextView device_type = (TextView) findViewById(R.id.select_device_type);
        device_type.setText(group_name);
        setOnClickListener(this);
        populateDevices();
    }


    public void init()
    {
        list_open = false;
        populateDevices();
    }


    public void populateDevices()
    {
        Device current_device = artisan.getCurrentDevice(group);
        String selected_name = current_device == null ? "None" :
            current_device.getFriendlyName();

        ((TextView) findViewById(R.id.select_device_name))
            .setText(selected_name);

        LinearLayout device_list = (LinearLayout) findViewById(
            R.id.select_device_list);

        // clear out the list, and fill it back in if it's open

        device_list.removeAllViews();

        if (list_open)
        {
            device_list.setVisibility(View.VISIBLE);
            DeviceManager device_manager = artisan.getDeviceManager();
            if (device_manager != null)
            {
                Device devices[] = device_manager.getDevices(group);
                for (Device device : devices)
                    addDevice(device_list,device,selected_name);
            }
        }
        else
            device_list.setVisibility(View.GONE);

        //((MainMenu.mainMenuListAdapter)unused_main_menu.getAdapter()).
        //    notifyDataSetChanged();
    }



    private void addDevice(LinearLayout device_list, Device device, String selected_name)
    {
        LayoutInflater inflater = LayoutInflater.from(artisan);
        View list_item = inflater.inflate(R.layout.select_device_item,null,false);

        String name = device.getFriendlyName();
        TextView list_name = (TextView) list_item.findViewById(R.id.select_device_item_name);

        // set the color to blue if it's the selected renderer
        // and set the selected name if we just happened to find it

        if (name.equals(selected_name))
        {
            ((TextView) findViewById(R.id.select_device_name))
                .setText(selected_name);
            list_name.setTextColor(Color.BLUE);
        }
        else
            list_name.setTextColor(Color.BLACK);
        list_name.setText(name);

        // fire-off asynch task to get the icon

        String icon_url = device.getIconUrl();
        if (!icon_url.isEmpty())
        {
            ImageView image_view = (ImageView) list_item.findViewById(
                R.id.select_device_item_icon);
            ImageLoader.loadImage(artisan,image_view,icon_url);
        }

        list_item.setOnClickListener(this);
        device_list.addView(list_item);
    }



    public void onClick(View v)
    {
        int id = v.getId();

        // TOGGLE OPEN AND CLOSED

        if (id == R.id.select_device_layout)
        {
            list_open = !list_open;
            populateDevices();
        }

        // SELECT DEVICE

        else if (id == R.id.select_device_item_layout)
        {
            TextView selected_name = (TextView) v.findViewById(R.id.select_device_item_name);
            String name = selected_name.getText().toString();
            if (artisan.setArtisanDevice(group,name) &&
                goto_page != -1)
            {
                artisan.onBodyClicked();
                artisan.getViewPager().setCurrentItem(goto_page);
            }
        }
    }


    //------------------------------------
    // Artisan Event Handling
    //------------------------------------

    public void handleArtisanEvent(String event_id, Object data)
    {
        if (event_id.equals(EVENT_NEW_DEVICE) ||
            event_id.equals(EVENT_LIBRARY_CHANGED) ||
            event_id.equals(EVENT_RENDERER_CHANGED) ||
            event_id.equals(EVENT_PLAYLIST_SOURCE_CHANGED))
        {
            populateDevices();
        }
    }

}   // class SelectDevicePref


