package prh.artisan;

//-------------------------------------
// Select Device
//-------------------------------------

import android.content.Context;
import android.graphics.Color;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import prh.device.Device;
import prh.device.DeviceManager;
import prh.base.ArtisanEventHandler;
import prh.utils.Utils;
import prh.utils.imageLoader;


public class SelectDevice extends RelativeLayout implements
    ArtisanEventHandler,
    View.OnTouchListener,
    View.OnLongClickListener,
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
        setOnTouchListener(this);
        //setOnClickListener(this);
        setOnLongClickListener(this);
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

        int color = 0xFFaaaaaa;   // unknown
            // unknown devices are grey
            // online devices are black
            // offline devices are red
        if (name.equals(selected_name))
        {
            ((TextView) findViewById(R.id.select_device_name))
                .setText(selected_name);
            color = Color.BLUE;
        }
        else if (device.getDeviceStatus() == Device.deviceStatus.OFFLINE)
            color = 0xFFcc2222;
        else if (device.getDeviceStatus() == Device.deviceStatus.ONLINE)
            color = Color.BLACK;

        list_name.setTextColor(color);
        list_name.setText(name);

        // fire-off asynch task to get the icon

        String icon_url = device.getIconUrl();
        if (!icon_url.isEmpty())
        {
            ImageView image_view = (ImageView) list_item.findViewById(
                R.id.select_device_item_icon);
            imageLoader.loadImage(artisan,image_view,icon_url);
        }

        list_item.setOnTouchListener(this);
        //list_item.setOnClickListener(this);
        list_item.setOnLongClickListener(this);
        device_list.addView(list_item);
    }



    public void onClick(View v)
        // Clear the highlight and do the command
    {
        int id = v.getId();
        Utils.log(0,0,"on_click v=" + v);
        in_touch = 0;

        // TOGGLE OPEN AND CLOSED

        if (id == R.id.select_device_layout)
        {
            list_open = !list_open;
            populateDevices();
            v.setBackgroundColor(Color.WHITE);
            v.invalidate();
        }

        // SELECT DEVICE

        else if (id == R.id.select_device_item_layout)
        {
            TextView selected_name = (TextView) v.findViewById(R.id.select_device_item_name);
            String name = selected_name.getText().toString();
            boolean b = artisan.setArtisanDevice(group,name);

            // highlighted while connecting

            v.setBackgroundColor(Color.WHITE);
            v.invalidate();

            if (b && goto_page != -1)
            {
                artisan.onBodyClicked();
                artisan.setCurrentPage(goto_page);
            }
        }
    }



    //------------------------------------------------------------------
    // Selected Item Highlighting
    //------------------------------------------------------------------

    private int dbg_touch = 0;

    private static int in_touch = 0;
    private static int COLOR_SELECTED = 0xffbbbbff;


    //@Override
    public boolean dont_onInterceptTouchEvent(MotionEvent event)
    {
        boolean rslt = in_touch>0 ? true : false;
        Utils.log(dbg_touch,0,"onInterceptTouchEvent() intouch=" + in_touch + " returning " + rslt + " this=" + this);
        return false;
    }


    @Override public boolean onTouch(View v, MotionEvent event)
        // on ACTION_DOWN set in_touch to 1 for the list_item,
        // or 2 for items within the list and highlight the item
    {
        boolean retval = false;
        int desired_level = v.getId() == R.id.select_device_layout ? 1 : 2;

        switch (event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                Utils.log(dbg_touch,0,"touch_down intouch=" + in_touch + " v=" + v);
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                v.setBackgroundColor(COLOR_SELECTED);
                v.invalidate();
                in_touch = desired_level;
                break;

            // On any UP or CANCEL if in_touch == desired_level, that is
            // if the touch event is for this view (list or item), call onClick().
            //
            // This has to be done on a separate thread for some reason.
            // If run on the same thread without these wrappers, it works, but
            // Artisan later crashes on some endless loop of touch events.
            //
            // But then because it's a separate thread, it has to in turn, wait
            // on the main UI thread because it touches the views.
            //
            // All of this to just call onClick() and highlight a button, sheesh.

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                final View fv = v;
                Utils.log(dbg_touch,0,"touch_up desired=" + desired_level + " intouch=" + in_touch + " v=" + v);
                if (in_touch-- == desired_level)
                {
                    Thread thread = new Thread( new Runnable() { public void run() {
                        artisan.runOnUiThread( new Runnable() { public void run() {
                            onClick(fv);
                        }});
                    }});
                    thread.start();
                }
                break;
        }

        return retval;
    }


    @Override public boolean onLongClick(View v)
        // Unhighlight the view and set intouch to -1
        // to prevent the touch from being acted upon
    {
        Utils.log(dbg_touch,0,"long_click intouch=" + in_touch + " v=" + v);
        v.setBackgroundColor(Color.WHITE);
        v.invalidate();
        in_touch = -1;
        return true;
    }


    //------------------------------------
    // Artisan Event Handling
    //------------------------------------

    public void handleArtisanEvent(String event_id, Object data)
        // called only with interesting events
    {
        // if (event_id.equals(EVENT_NEW_DEVICE) ||
        //     event_id.equals(EVENT_DEVICE_STATUS_CHANGED) ||
        //     event_id.equals(EVENT_LIBRARY_CHANGED) ||
        //     event_id.equals(EVENT_RENDERER_CHANGED) ||
        //     event_id.equals(EVENT_PLAYLIST_SOURCE_CHANGED))
        {
            populateDevices();
        }
    }

}   // class SelectDevicePref


