package prh.artisan;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import prh.base.ArtisanEventHandler;
import prh.base.ArtisanPage;
import prh.types.intList;
import prh.utils.Utils;


public class aPrefs extends PreferenceFragment implements
    ArtisanPage,
    View.OnClickListener,
    ArtisanEventHandler
    // SharedPreferences.OnSharedPreferenceChangeListener
{
    private static int dbg_aprefs = 1;

    private Artisan artisan = null;
    private TextView page_title = null;


    //@Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);

        if (pref instanceof ListPreference) {
            ListPreference listPref = (ListPreference) pref;
            pref.setSummary(listPref.getEntry());

            TextView text_view = (TextView) listPref.getView(null,null).findViewById(R.id.pref_value);
            if (text_view != null)
                text_view.setText (listPref.getEntry());

            /*
            int index = listPreference.findIndexOfValue(newValue.toString());
            if (index != -1) {
                textValue.setText(listPreference.getEntries()[index]);
            */
        }
    }

    //----------------------------------------------
    // LifeCycle
    //----------------------------------------------

    public void setArtisan(Artisan ma)
        // called immediately after construction
    {
        artisan = ma;
    }

    @Override
    public void onAttach(Activity activity)
    {
        Utils.log(dbg_aprefs,0,"aPrefs.onAttach() called");
        super.onAttach(activity);
    }

    @Override
    public void onDetach()
    {
        Utils.log(dbg_aprefs,0,"aPrefs.onDetach() called");
        super.onDetach();
    }

    @Override public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(artisan);
        //prefs.registerOnSharedPreferenceChangeListener(this);
    }


    //----------------------------------
    // artisanPage
    //----------------------------------

    @Override public void onSetPageCurrent(boolean current)
    {
        page_title = null;
        if (current)
        {
            page_title = new TextView(artisan);
            artisan.setArtisanPageTitle(page_title);
            updateTitleBar();
        }
    }


    private void updateTitleBar()
    {
        page_title.setText(getTitleBarText());
    }


    private String getTitleBarText()
    {
        PreferenceScreen screen = this.getPreferenceScreen();
        String title = screen.getTitle().toString();
        return title.isEmpty() ? "Preferences" : title;
    }


    @Override public intList getContextMenuIds()
    {
        return new intList();
    }



    //------------------------------------------------
    // onClick
    //------------------------------------------------
    // currently not used

    @Override
    public void onClick(View v)
    {
        int id = v.getId();
    }

    @Override public boolean onMenuItemClick(MenuItem item)
    {
        return true;
    }


    //------------------------------------------------
    // Artisan Event Handling
    //------------------------------------------------

    @Override public void handleArtisanEvent( String event_id, Object data )
    {
        /**********

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

            prefDefaultDevice dpref = (prefDefaultDevice) pref_list_adapter
                .getItem(default_position).getTag();

            dpref.populateDefault();
        }

         *********/

    }   // handleArtisanEvent()




}   // class aPrefs
