package prh.artisan;
// TODO the phone goes dark and locked after a timeout
// TODO library, probably playlist crash
// TODO
//    SSDPServer devices
//    Basic Prefs
//    Item Selection
//    Item Context Menus
//    Real write-thru image cache
//       lru, memory limit?
//    Memory usage, code cleanup
//
// Untestable OpenHome PlaylistManager?
//     - only does single song inserts
// RemotePlaylistSource?
//     Copy, Compare, whole playlists
//     inser, delete,t a bunch of records at once
//     Re-order ... shuffling in general
//     think it through, put it all together
// Niceties?
//     Library (or even Playlist) TreeView
//     Prefs Fragment becomes the
//          Database,PlaylistSource, and general
//          Playlist Mangaer ..
//     Or maybe even two more pages
//     Implement DLNA Search in http.ContentServer
//     Sorting, etc
//     Form factors (phone, tablet, LibraryGridView)
// Artisan Pure Perl Server incompatabilities (object.container types)
//      huge cleanup
// Artisan Windows
//      Playlist and Library synchronization
//      from USB or remote device
//      Android Media Scan & Self Sufficiency



import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;

import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;


import prh.device.Device;
import prh.device.DeviceManager;
import prh.device.LocalLibrary;
import prh.device.LocalPlaylistSource;
import prh.device.LocalRenderer;
import prh.server.HTTPServer;
import prh.server.LocalVolumeFixer;
import prh.server.SSDPServer;
import prh.server.utils.UpnpEventManager;
import prh.base.ArtisanEventHandler;
import prh.base.ArtisanPage;
import prh.base.EditablePlaylist;
import prh.base.Library;
import prh.base.Playlist;
import prh.base.PlaylistSource;
import prh.base.Renderer;
import prh.types.intList;
import prh.utils.Utils;
import prh.utils.loopingRunnable;



/*
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
*/

public class Artisan extends Activity implements
    View.OnClickListener,
    ArtisanEventHandler
{
    private static int dbg_main = 0;

    private static int NUM_PAGER_ACTIVITIES = 5;
    public final static int PAGE_PREFS = 0;
    public final static int PAGE_PLAYING = 2;
    public final static int PAGE_PLAYLIST = 1;
    public final static int PAGE_LIBRARY = 3;
    public final static int PAGE_EXPLORER = 4;

    public final static int START_PAGE = Build.ID.equals(Utils.ID_CAR_STEREO) ?
        PAGE_PLAYING : PAGE_PLAYING;

    // system working variables

    private int current_page = -1;
    private boolean is_full_screen = false;
    private boolean artisan_created = false;

    // Main Fragments

    private prh.artisan.aPrefs aPrefs    = new aPrefs();
    private aRenderer aRenderer = new aRenderer();
    private aPlaylist aPlaylist = new aPlaylist();
    private aLibrary  aLibrary  = new aLibrary();
    private aExplorer aExplorer = new aExplorer();

    // Lifetime Local Objects

    private Database database = null;
    private LocalLibrary local_library = null;
    private LocalRenderer local_renderer = null;
    private LocalPlaylistSource local_playlist_source = null;

    public Database getDatabase() { return database; }
    public LocalLibrary getLocalLibrary() { return local_library; }
    public LocalRenderer getLocalRenderer() { return local_renderer; }
    public LocalPlaylistSource getLocalPlaylistSource() { return local_playlist_source; }

    // The current context for the Application
    // The current Library, Renderer, Playlist, and PlaylistSource

    private Library library = null;
    private Renderer renderer = null;
    private PlaylistSource playlist_source = null;
    private EditablePlaylist current_playlist = null;

    public Library getLibrary() { return library; }
    public Renderer getRenderer() { return renderer; }
    public PlaylistSource getPlaylistSource() { return playlist_source; }
    public EditablePlaylist getCurrentPlaylist() { return current_playlist; }

    // Servers (no public access)

    private HTTPServer http_server = null;
    private LocalVolumeFixer volume_fixer = null;
    private SSDPServer ssdp_server = null;
    public HTTPServer getHTTPServer() { return http_server; }
        // then give it all away

    // the Device Manager and default Device Names

    private DeviceManager device_manager = null;
    public DeviceManager getDeviceManager() { return device_manager; }

    private String default_renderer_name = "";
    private String default_library_name = "";
    private String default_playlist_source_name = "";
        // if these are non-blank, we have to keep trying until the
        // SSDPSearch finishes to set the correct renderer ...

    // Public Views

    private MainMenu main_menu = null;
    private MainMenuToolbar main_toolbar = null;
    public MainMenuToolbar getToolbar() { return main_toolbar; }

    private VolumeControl volume_control = null;
    public VolumeControl getVolumeControl() { return volume_control; }

    // Private Variables

    private int num_progress = 0;

    private ViewPager view_pager = null;
    private pageChangeListener page_change_listener = null;
    // public ViewPager getViewPager() { return view_pager; };

    private WifiManager.WifiLock wifi_lock = null;


    //--------------------------------------------------------
    // onCreate()
    //--------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Utils.log(-1,0,"------ Artisan.onCreate() started ------");

        // 0 = static initialization

        Utils.static_init(this);
        Prefs.static_init(this);

        default_renderer_name = getDefaultDeviceName(
            "RENDERER",Prefs.id.DEFAULT_RENDERER,Prefs.id.SELECTED_RENDERER);
        default_library_name = getDefaultDeviceName(
            "LIBRARY",Prefs.id.DEFAULT_LIBRARY,Prefs.id.SELECTED_LIBRARY);
        default_playlist_source_name = getDefaultDeviceName(
            "PLAYLIST_SOURCE",Prefs.id.DEFAULT_PLAYLIST_SOURCE,Prefs.id.SELECTED_PLAYLIST_SOURCE);

        // 1 = low level initialization

        Utils.log(dbg_main,0,"creating and starting Database");
        database = new Database();
        if (!database.start())
            database = null;
        else
            Utils.log(dbg_main,0,"Database created and started");

        if (Utils.server_ip != null &&
            Prefs.getBoolean(Prefs.id.KEEP_WIFI_ALIVE))
        {
            WifiManager wifiMan = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            wifi_lock = wifiMan.createWifiLock(getClass().toString());
            wifi_lock.acquire();
        }

        //-------------------------------------------
        // 2. create views
        //-------------------------------------------
        // create the main view and sub-views
        // set the main menu click handler

        Utils.log(dbg_main,0,"creating Views");

        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_artisan);

        // setup for Full Page behavior

        initTitleBarHeight();
        findViewById(R.id.artisan_title_bar).setOnClickListener(this);

        // create the main "activity" fragments
        // and the pager, listener, and adapter

        aPrefs    = new aPrefs();
        aRenderer = new aRenderer();
        aPlaylist = new aPlaylist();
        aLibrary  = new aLibrary();
        aExplorer = new aExplorer();

        aPrefs.setArtisan(this);
        aRenderer.setArtisan(this);
        aPlaylist.setArtisan(this);
        aLibrary.setArtisan(this);
        aExplorer.setArtisan(this);

        myPagerAdapter my_pager_adapter = new myPagerAdapter(getFragmentManager());
        page_change_listener = new pageChangeListener(this);
        view_pager = (ViewPager) findViewById(R.id.artisan_content);
        view_pager.setAdapter(my_pager_adapter);
        view_pager.addOnPageChangeListener(page_change_listener);

        // get a pointer to the main menu and toolbar
        // disappear the main menu

        main_toolbar = (MainMenuToolbar) findViewById(R.id.artisan_main_toolbar);
        main_menu = (MainMenu) findViewById(R.id.artisan_main_menu);
        hideMainMenu();

        Utils.log(dbg_main,0,"finished creating Views");

        // start the volume control

        Utils.log(dbg_main,0,"creating VolumeControl");
        volume_control = new VolumeControl(this);
        Utils.log(dbg_main,0,"VolumeControl created");

        // start off in full screen mode
        // with the menu showing

        showFullScreen(true);

        //-------------------------------------------
        // 3 = Create the Object Context
        //-------------------------------------------
        // Local Library, PlaylistSource,  Playlist,
        // and Renderer, as defaults

        if (database != null &&
            Prefs.getBoolean(Prefs.id.START_LOCAL_LIBRARY))
        {
            Utils.log(dbg_main,0,"creating and starting LocalLibrary");
            local_library = new LocalLibrary(this);
            if (!local_library.startLibrary())
                local_library = null;
            else
                Utils.log(dbg_main,0,"LocalLibrary created and started");
        }
        library = local_library;

        // The local_playlist_source SHALL ALWAYS CONSTRUCT
        // and START, even if it empty, or does not have write
        // privileges.

        Utils.log(dbg_main,0,"creating and starting LocalPlaylistSource");
        local_playlist_source = new LocalPlaylistSource(this);
        local_playlist_source.startPlaylistSource();
        Utils.log(dbg_main,0,"LocalPlaylistSource created and started");

        playlist_source = local_playlist_source;

        // The current_playlist is a EditablePlaylist.
        // Here we initialize it with a PlaylistWrapper
        // around an empty Playlist as given by the
        // LocalPlaylistSource

        Utils.log(dbg_main,0,"creating and starting empty current_playlist");
        Playlist empty = local_playlist_source.createEmptyPlaylist();
        current_playlist = new PlaylistWrapper(this,empty);
        current_playlist.startPlaylist();
        Utils.log(dbg_main,0,"current_playlist created and started");

        // Now the Renderer can be started with the CURRENT_PLAYLIST
        // as a Queue ... It ALSO *should* also always construct,
        // but might be null based on client pref.

        if (Prefs.getBoolean(Prefs.id.START_LOCAL_RENDERER))
        {
            Utils.log(dbg_main,0,"creating and starting LocalRenderer");
            local_renderer = new LocalRenderer(this);
            local_renderer.startRenderer();
            Utils.log(dbg_main,0,"LocalRenderer created and started");
        }
        renderer = local_renderer;

        //-------------------------------------------
        // set the start page
        //-------------------------------------------
        // we have to call setPageSelected() because
        // view_pager.setCurrentItem() does not trigger
        // the onPageChange listener ...

        setCurrentPage(START_PAGE);

        //-------------------------------------------
        // 4 = start servers
        //-------------------------------------------

        if (Prefs.getBoolean(Prefs.id.START_VOLUME_FIXER) &&
            Utils.ID_CAR_STEREO.equals(Build.ID))
        {
            Utils.log(dbg_main,0,"creating and starting LocalVolumeFixer");
            volume_fixer = new LocalVolumeFixer(this);
            volume_fixer.start();
            Utils.log(dbg_main,0,"LocalVolumeFixer created and started");
        }

        if (Utils.server_ip  != null)
        {
            if (Prefs.getBoolean(Prefs.id.START_HTTP_MEDIA_SERVER) ||
                Prefs.getBoolean(Prefs.id.START_HTTP_MEDIA_RENDERER) ||
                Prefs.getBoolean(Prefs.id.START_HTTP_OPEN_HOME_SERVER))
            {
                // http_server

                try
                {
                    Utils.log(dbg_main,0,"creating and starting HTTP server ...");
                    http_server = new HTTPServer(this);
                }
                catch (Exception e)
                {
                    Utils.error("Error creating HTTP server:" + e.toString());
                    http_server = null;
                }

                // ssdp_server

                if (http_server != null)
                {
                    http_server.start();
                    Utils.log(dbg_main,0,"HTTP server created and started");

                    Utils.log(dbg_main,0,"creating and starting SSDP server thread...");
                    ssdp_server = new SSDPServer(this,http_server);
                    Thread ssdp_thread = new Thread(ssdp_server);
                    ssdp_thread.start();
                    Utils.log(dbg_main,0,"SSDP server created and thread started");
                }
            }
        }


        // 5 =  construct the DeviceManager and add the
        // local items to it, then see if we can find the
        // default items (if they're not local)

        Utils.log(dbg_main,0,"creating DeviceManager ...");
        device_manager = new DeviceManager(this);
        if (local_library != null)
            device_manager.addDevice(local_library);
        device_manager.addDevice(local_renderer);
        device_manager.addDevice(local_playlist_source);
        Utils.log(dbg_main,0,"DeviceManager created");

        // see if we can find the default devices and start them
        // clear the names if found, even if it fails
        // to start, so we don't try again ... it won't be
        // the local device (cuz that's ""), so stop the local
        // devices if they exist before setting the new ones

        // Changing the PlaylistSource may create a dangling
        // Named current_playlist, which SHALL BE CONSIDERED DIRTY
        // in the context of the new PlaylistSource ...

        if (findAndStartDefaultDevice(Device.deviceGroup.DEVICE_GROUP_LIBRARY,default_library_name))
            default_library_name = "";
        if (findAndStartDefaultDevice(Device.deviceGroup.DEVICE_GROUP_RENDERER,default_renderer_name))
            default_renderer_name = "";
        if (findAndStartDefaultDevice(Device.deviceGroup.DEVICE_GROUP_PLAYLIST_SOURCE,default_playlist_source_name))
            default_playlist_source_name = "";

        // Do the initial SSDP Search.  Artisan.handleArtisanEvent()
        // EVENT_NEW_DEVICE will be called for any devices not in
        // the initial cache.

        if (Utils.server_ip != null)
        {
            Utils.log(dbg_main,0,"calling DeviceManager.doDeviceSearch()");
            device_manager.doDeviceSearch(false);
        }
        else
        {
            if (!default_library_name.isEmpty())
                Utils.error("Could not find DEFAULT LIBRARY(" + default_library_name + ")");
            if (!default_renderer_name.isEmpty())
                Utils.error("Could not find DEFAULT RENDERER(" + default_renderer_name + ")");
            if (!default_playlist_source_name.isEmpty())
                Utils.error("Could not find DEFAULT PLAYLIST_SOURCE(" + default_playlist_source_name + ")");
                default_library_name = "";
                default_renderer_name = "";
                default_playlist_source_name = "";
        }

        Utils.log(dbg_main-1,0,"------ Artisan.onCreate() finished ------");
        artisan_created = true;

    }   // onCreate()


    @Override
    public void onResume()
    {
        Utils.log(dbg_main+1,0,"Artisan.onResume()");
        super.onResume();
    }

    @Override
    public void onPause()
    {
        Utils.log(dbg_main+1,0,"Artisan.onPause()");
        super.onPause();
    }


    @Override
    public void onDestroy()
    {
        Utils.log(dbg_main-1,0,"Artisan.onDestroy() started ------");
        super.onDestroy();

        // 5 = stop the device manager
        // it is up to it to wait until any pending SSDP Searches are finished

        if (device_manager != null)
            device_manager.stop();

        // 4 = stop servers

        if (ssdp_server != null)
            ssdp_server.shutdown();
        ssdp_server = null;

        if (http_server != null)
            http_server.stop();
        http_server = null;

        if (volume_fixer != null)
            volume_fixer.stop();
        volume_fixer = null;


        // 3 = stop the current and local objects

        if (library != null)
            library.stopLibrary(true);
        library = null;

        if (local_library != null)
            local_library.stopLibrary(true);
        local_library = null;

        if (renderer != null)
            renderer.stopRenderer(true);
        renderer = null;

        if (local_renderer != null)
            local_renderer.stopRenderer(true);
        local_renderer = null;

        if (current_playlist != null)
            current_playlist.stopPlaylist(true);
        current_playlist = null;

        if (playlist_source != null)
            playlist_source.stopPlaylistSource(true);
        playlist_source = null;

        if (local_playlist_source != null)
            local_playlist_source.stopPlaylistSource(true);
        local_playlist_source = null;

        // 2 = unwind view pager and fragments

        if (volume_control != null)
            volume_control.dismiss();
        volume_control = null;

        /*
        if (view_pager != null)
            view_pager.removeOnPageChangeListener(page_change_listener);
        page_change_listener = null;
        view_pager = null;
        */

        aPrefs     = null;
        aRenderer = null;
        aPlaylist  = null;
        aLibrary   = null;
        aExplorer  = null;

        // 1 = undo low level initialization

        if (wifi_lock != null)
            wifi_lock.release();
        wifi_lock = null;

        if (database != null)
            database.stop();
        database = null;

        // 0 = undo static initializations

        Prefs.static_init(null);
        Utils.static_init(null);
        Utils.log(dbg_main-1,0,"Artisan.onDestroy() finished ------");
    }



    //-------------------------------------------------------
    // Paging
    //-------------------------------------------------------

    public void setCurrentPage(int index)
    {
        view_pager.setCurrentItem(index);
        //if (current_page == -1)
         //   onPageSelected(index);
    }


    public void setArtisanPageTitle(View page_title)
        // shall only be called by pages while they
        // are the current page
    {
        LinearLayout title_area = (LinearLayout) findViewById(
            R.id.artisan_title_bar_text_area);
        title_area.removeAllViews();
        title_area.addView(page_title);
    }


    private class myPagerAdapter extends FragmentPagerAdapter
    {
        public myPagerAdapter(FragmentManager fm)       { super(fm);  }
        @Override public int getCount()                 { return NUM_PAGER_ACTIVITIES; }
        @Override public Fragment getItem(int position)
        {
            switch (position)
            {
                case PAGE_PREFS : return aPrefs;
                case PAGE_PLAYING : return aRenderer;
                case PAGE_PLAYLIST : return aPlaylist;
                case PAGE_LIBRARY : return aLibrary;
                case PAGE_EXPLORER : return aExplorer;
            }
            return null;
        }
    }

    public class pageChangeListener extends ViewPager.SimpleOnPageChangeListener
    {
        private Artisan artisan;
        public pageChangeListener(Artisan a) {artisan = a;}

        @Override
        public void onPageSelected(int index)
        {
            artisan.onPageSelected(index);
        }
    }


    private void onPageSelected(int index)
    {
        myPagerAdapter adapter = (myPagerAdapter) view_pager.getAdapter();
        main_toolbar.initButtons();

        // unselect the old page

        setBodyClickListener(false);
        if (current_page >= 0)
        {
            ArtisanPage page = (ArtisanPage) adapter.getItem(current_page);
            page.onSetPageCurrent(false);
        }

        // select the new page

        current_page = index;
        setBodyClickListener(true);
        ArtisanPage page = (ArtisanPage) adapter.getItem(current_page);
        page.onSetPageCurrent(true);
    }



    //-------------------------------------------------------
    // FullScreen Behavior
    //-------------------------------------------------------
    // Our title bar gets a regular onClick() handler.
    //
    // All page fragments get a generic onBodyClicked() handler.
    // The click handlers have to be set on the paged activities
    // each time the come into view, and removed when they go out
    // of view, presumably because the View Pager removes
    // onClickHandlers when changing pages.
    //
    // Additionally, most fragment and other onClick() handlers
    // should call back to onBodyClicked().
    //
    // To begin with, we are full screen, our title bar shows at 0,0;
    //
    // When our title bar is clicked we stop being full screen,
    // disappear the title bar, and let the Android status bar show.
    //
    // When they click on the body of a fragment, or a properly
    // implemented control, it calls back to onBodyClicked(), which
    // changes us back to full screen, and shows our title bar.
    //
    // The onBodyClicked() handler also hides the main menu,
    // and returns TRUE if it did either, so that the event
    // can be eaten by the particular onClick() handler


    private void initTitleBarHeight()
        // The whole scheme hinges on having our title bar
        // exactly replace the status bar.  This code sets
        // our title bar's height to that of the Android
        // system StatusBar
    {
        View title_bar = findViewById(R.id.artisan_title_bar);
        ViewGroup.LayoutParams params = title_bar.getLayoutParams();
        int id = getResources().getIdentifier("status_bar_height","dimen","android");
        if (id > 0)
            params.height = getResources().getDimensionPixelSize(id);
        else
            params.height = 60; // bogus fallback
        title_bar.setLayoutParams(params);
    }


    private void showFullScreen(boolean full)
        // if fullscreen, then our title bar disappears and
        // the system StatusBar is allowed to show, otherwise
        // we show our title bar over the system StatusBar
    {
        // set Window Mode

        getWindow().setFlags(full ?
            WindowManager.LayoutParams.FLAG_FULLSCREEN : 0,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // display or hide the title bar
        // title bar shows when full

        View title_bar = findViewById(R.id.artisan_title_bar);
        title_bar.setVisibility(full ? View.VISIBLE : View.GONE );
        is_full_screen = full;
    }


    private void setBodyClickListener(boolean setit)
        // The current_page is coming into view (setit),
        // or going out of view (!setit). Remove or
        // set the onBodyClicked() listener.
    {
        /*
        if (current_page >= 0)
        {
            myPagerAdapter adapter = (myPagerAdapter) view_pager.getAdapter();
            Fragment fragment = adapter.getItem(current_page);
            View view = fragment.getView();
            if (view != null)
                fragment.getView().setOnClickListener( !setit ? null :
                    createBodyClickListener());
        }
        */
    }


    private View.OnClickListener createBodyClickListener()
        // Return a new instance of a fullPageClickListener,
    {
        View.OnClickListener show_listener = new View.OnClickListener()
        {
            public void onClick(View view)
            {
                onBodyClicked();
            }
        };
        return show_listener;
    }


    public boolean onBodyClicked()
        // Hide the main menu if it's showing, and/or
        // switch back to full screen mode if we're not.
        // Return true in either case so clients can skip
        // the event.
        //
        // called liberally throughout the code by controls
        // within pages (that don't call have a onBodyClickListener()
    {
        boolean retval = hideMainMenu();
        if (!is_full_screen)
        {
            retval = true;
            showFullScreen(true);
        }
        return retval;
    }



    //-------------------------------------------------------
    // Miscellaneous Public API
    //-------------------------------------------------------
    // starting with limited exact access to http_server


    public void onUtilsError(final String msg)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                Toast.makeText(getApplicationContext(),msg,Toast.LENGTH_LONG).show();
            }
        });
    }



    public void doVolumeControl()
    {
        Utils.log(dbg_main,0,"doVolumeControl()");
        if (volume_control != null &&
            renderer != null &&
            renderer.getVolume() != null)
        {
            volume_control.show();
        }
    }


    public void showArtisanProgressIndicator(final boolean show_it)
    {
        runOnUiThread(new Runnable() { public void run() {
            num_progress += show_it ? 1 : -1;
            if (num_progress == 0 || num_progress == 1)
            {
                ProgressBar progress = (ProgressBar) findViewById(R.id.artisan_progress);
                progress.setVisibility(show_it ? View.VISIBLE : View.INVISIBLE);
            }
        }});
    }



    //------------------------------------------------
    // Playlist
    //------------------------------------------------

    public boolean setPlaylist(String name)
        // called from station button, we select the new
        // playlist and event it to any interested clients
    {
        Utils.log(dbg_main,0,"setPlaylist(" + name + ")");
        Playlist new_playlist = playlist_source.getPlaylist(name);
        if (new_playlist == null)
        {
            Utils.error("Could not find playlist(" + name + ")");
            return false;
        }
        return setPlaylist(new PlaylistWrapper(this,new_playlist));
    }


    public boolean setPlaylist(EditablePlaylist new_playlist)
    // called from station button, we select the new
    // playlist and event it to any interested clients
    {
        Utils.log(dbg_main,0,"setPlaylist(" + new_playlist.getName() + ")");
        if (new_playlist == current_playlist)
            return true;
        if (new_playlist.startPlaylist())
        {
            if (current_playlist != null)
                current_playlist.stopPlaylist(false);
            current_playlist = new_playlist;
            handleArtisanEvent(EVENT_PLAYLIST_CHANGED,current_playlist);

            // when Artisan is satisfied that it has a new playlist
            // with some songs in it, it tells the actual renderer
            // to start playing the current song. This does NOT stop
            // the renderer on a new empty playlist ...

            if (current_playlist.getNumTracks() > 0)
                renderer.incAndPlay(0);

            return true;
        }
        return false;    // whatever
    }



    //-------------------------------------------------------
    // Device Management
    //-------------------------------------------------------


    private String getDefaultDeviceName(String what, Prefs.id pref_id, Prefs.id selected_id)
    {
        String default_name = Prefs.getString(pref_id);
        if (default_name.equals(Prefs.LAST_SELECTED))
            default_name = Prefs.getString(selected_id);
        if (default_name.equals(Prefs.LOCAL))
            default_name = "";
        if (!default_name.isEmpty())
            Utils.log(dbg_main,0,"DEFAULT_" + what + "(" + default_name +")");
        return default_name;
    }


    public Device getCurrentDevice(Device.deviceGroup group)
    {
        Device device = null;
        if (group.equals(Device.deviceGroup.DEVICE_GROUP_LIBRARY))
            device = (Device) library;
        if (group.equals(Device.deviceGroup.DEVICE_GROUP_RENDERER))
            device = (Device) renderer;
        if (group.equals(Device.deviceGroup.DEVICE_GROUP_PLAYLIST_SOURCE))
            device = (Device) playlist_source;
        return device;
    }


    private boolean findAndStartDefaultDevice(Device.deviceGroup group, String name)
    {
        // if the name is empty, we're already done

        if (name.isEmpty())
            return false;

        Device device = device_manager.getDevice(group,name);
        String dbg_thing = group.toString().replace("DEVICE_GROUP_","");

        if (device != null)
        {
            Utils.log(dbg_main,0,"STARTING DEFAULT " + dbg_thing +"(" + name + ") artisan_created=" + artisan_created);
            setArtisanDevice(group,name);
                // ignore false == could_not_start function value
            return true;
        }

        if (!artisan_created)
            Utils.log(dbg_main,0,"Could not find DEFAULT " + dbg_thing + "(" + name + ") in onCreate() ... continuing to look ...");
        return false;
    }



    public boolean setArtisanDevice(Device.deviceGroup group, String name)
        // Called with a "Library", "Renderer", or "PlaylistSource",
        // and a name, sets the current Renderer, Library, or PlaylistSource,
        // stopping the old one if any, and starting the new one.
    {
        // Setup
        // the name of local items is "" in prefs

        Prefs.id prefs_id = null;
        String thing = "";
        String cur_name = "";
        String event_name = "";
        String prefs_name = name;

        if (group == Device.deviceGroup.DEVICE_GROUP_LIBRARY)
        {
            thing = "Library";
            cur_name = library == null ? "" : library.getLibraryName();
            prefs_id = Prefs.id.SELECTED_LIBRARY;
            event_name = ArtisanEventHandler.EVENT_LIBRARY_CHANGED;
            if (prefs_name.equals(Device.deviceType.LocalLibrary))
                prefs_name = "";
        }
        else if (group == Device.deviceGroup.DEVICE_GROUP_RENDERER)
        {
            thing = "Renderer";
            cur_name = renderer == null ? "" : renderer.getRendererName();
            prefs_id = Prefs.id.SELECTED_RENDERER;
            event_name = ArtisanEventHandler.EVENT_RENDERER_CHANGED;
            if (prefs_name.equals(Device.deviceType.LocalRenderer))
                prefs_name = "";
        }
        else if (group == Device.deviceGroup.DEVICE_GROUP_PLAYLIST_SOURCE )
        {
            thing = "PlaylistSource";
            cur_name = playlist_source == null ? "" : playlist_source.getPlaylistSourceName();
            prefs_id = Prefs.id.SELECTED_PLAYLIST_SOURCE;
            event_name = ArtisanEventHandler.EVENT_PLAYLIST_SOURCE_CHANGED;
            if (prefs_name.equals(Device.deviceType.LocalPlaylistSource))
                prefs_name = "";
        }

        // bail if its the same device

        if (cur_name.equals(name))
        {
            Utils.log(dbg_main,0,"setArtisanDevice(" + thing + "," + name + ") ignoring attempt to set to same " + thing);
            return true;
        }
        Utils.log(dbg_main,0,"setArtisanDevice(" + thing + "," + name + ")");

        // find the new Device
        // bail if it's not found

        Device device = device_manager.getDevice(group,name);
        if (device == null)
        {
            Utils.error("Attempt to set " + thing + " to non-existing Device(" + name + ")");
            return false;
        }

        // try to start the new Device, stop the old one,
        // and assign the new object if it works
        // bail if it fails.

        boolean started = false;

        if (thing.equals("Library") && ((Library) device).startLibrary())
        {
            if (library != null)
                library.stopLibrary(false);
            library = (Library) device;
            started = true;
        }
        if (thing.equals("Renderer") && ((Renderer) device).startRenderer())
        {
            if (renderer != null)
                renderer.stopRenderer(false);
            renderer = (Renderer) device;
            started = true;
        }
        if (thing.equals("PlaylistSource") && ((PlaylistSource) device).startPlaylistSource())
        {
            if (playlist_source != null)
                playlist_source.stopPlaylistSource(false);
            playlist_source = (PlaylistSource) device;
            started = true;
        }

        if (!started)
        {
            Utils.error("setArtisanDevice(" + name + ") " + thing + ".start() failed");
            device.setDeviceStatus(Device.deviceStatus.OFFLINE);
            return false;
        }

        // We started it, and if we are out of Artisan.onCreate(),
        // we set the selected item into the preferences, and send out
        // an artisan EVENT_XXX_CHANGED event

        if (artisan_created)
        {
            Prefs.putString(prefs_id,prefs_name);
            Utils.log(dbg_main+1,0,"setArisanDevice(" + thing + "(" + name + ") finishing and dispatching " + event_name + "(" + name + ")");
            handleArtisanEvent(event_name,device);
        }

        return true;
    }


    //----------------------------------------------------
    // onClick
    //----------------------------------------------------
    // Handles MainMenu and MainToolBars

    private boolean hideMainMenu()
    {
        boolean retval = false;
        if (main_menu.getAlpha() != 0F)
        {
            main_menu.animate()
                .translationX(-main_menu.getWidth())
                .alpha(0.0f);
            findViewById(R.id.artisan_content).setAlpha(1.0F);
            retval = true;
        }
        return retval;
    }

    private void showMainMenu()
    {
        // first time
        if (main_menu.getVisibility() == View.GONE)
            main_menu.setVisibility(View.VISIBLE);

        findViewById(R.id.artisan_content).setAlpha(0.4F);
        main_menu.init();
        main_menu.animate()
            .translationX(0)
            .alpha(1.0f);
    }


    public void onClick(View v)
    {
        int id = v.getId();
        switch (id)
        {
            case R.id.artisan_title_bar:
                if (!onBodyClicked())
                    showFullScreen(false);
                break;

            case R.id.artisan_title_bar_icon:
                if (!onBodyClicked())
                    showMainMenu();
                break;


            // all other commands come in from MenuButtons
            // who already call onBodyClicked()

            case R.id.command_home :
                Utils.log(dbg_main,0,"onClickHome()");
                Intent i = new Intent();
                i.setAction(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_HOME);
                v.getContext().startActivity(i);
                break;

            case R.id.command_back :
                onBackPressed();
                break;

            case R.id.command_context :
                /*
                myPagerAdapter adapter = (myPagerAdapter) view_pager.getAdapter();
                ArtisanPage page = (ArtisanPage) adapter.getItem(current_page);

                PopupMenu context_menu = new PopupMenu(this,v);
                context_menu.setOnMenuItemClickListener(page);

                Menu menu = context_menu.getMenu();
                intList res_ids = page.getContextMenuIds();
                for (int res_id:res_ids)
                    menu.add(0,res_id,0,res_id);
                context_menu.show();
                */
                break;

            case R.id.command_playlist_albums :
                aPlaylist.setAlbumMode(true);
                break;
            case R.id.command_playlist_tracks :
                aPlaylist.setAlbumMode(false);
        }
    }



    //--------------------------------------------------
    // Support (and disabling) of Hardware Back Button
    //--------------------------------------------------

    @Override
    public void onBackPressed()
    {
        // do whatever is needed here
        if (current_page == 3)
            aLibrary.doBack();
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (// Integer.parseInt(android.os.Build.VERSION.SDK) > 5 &&
            keyCode == KeyEvent.KEYCODE_BACK &&
            event.getRepeatCount() == 0)
        {
            Utils.log(dbg_main+1,0,"onKeyDown(KEYCODE_BACK) Called");
            onBackPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    //---------------------------------------------------------------------
    // EVENT UTILITIES
    //---------------------------------------------------------------------
    // delay OpenHome events

    boolean defer_openhome_events = false;
    loopingRunnable open_home_delayer;

    private class OpenHomeEventDelayer implements Runnable
    {
        public void run()
        {
            defer_openhome_events = false;
        }
    }

    public void setDeferOpenHomeEvents()
    {
        defer_openhome_events = true;
        if (open_home_delayer != null)
            open_home_delayer.stop(false);
        open_home_delayer = new loopingRunnable(
            "open_home_event_delayer",
            new OpenHomeEventDelayer(),
            1200);
        open_home_delayer.start();
    }


    //------------------------------------------
    // restart the device search
    //------------------------------------------
    // stop everything and restart defaults

    public void restartDeviceSearch()
    {
        if (library != null && !((Device)library).isLocal())
        {
            library.stopLibrary(false);
            library = local_library;
            if (library != null)
                library.startLibrary();
            handleArtisanEvent(EVENT_LIBRARY_CHANGED,library);
        }
        if (renderer != null && !((Device)renderer).isLocal())
        {
            renderer.stopRenderer(false);
            renderer = local_renderer;
            if (renderer != null)
                renderer.startRenderer();
            handleArtisanEvent(EVENT_RENDERER_CHANGED,renderer);
        }
        if (playlist_source != null && !((Device)playlist_source).isLocal())
        {
            playlist_source.stopPlaylistSource(false);
            playlist_source = local_playlist_source;
            if (playlist_source != null)
                playlist_source.startPlaylistSource();
            handleArtisanEvent(EVENT_PLAYLIST_SOURCE_CHANGED,playlist_source);
        }

        default_renderer_name = getDefaultDeviceName(
            "RENDERER",Prefs.id.DEFAULT_RENDERER,Prefs.id.SELECTED_RENDERER);
        default_library_name = getDefaultDeviceName(
            "LIBRARY",Prefs.id.DEFAULT_LIBRARY,Prefs.id.SELECTED_LIBRARY);
        default_playlist_source_name = getDefaultDeviceName(
            "PLAYLIST_SOURCE",Prefs.id.DEFAULT_PLAYLIST_SOURCE,Prefs.id.SELECTED_PLAYLIST_SOURCE);
    }


    private void checkCurrentDeviceOffline(Device device)
    {
        boolean changed = false;
        if (device == library)
        {
            Utils.log(dbg_main,0,"Library OFFLINE(" + device.getFriendlyName() + ")");
            library.stopLibrary(false);
            library = local_library;
            if (library != null)
                library.startLibrary();
            handleArtisanEvent(EVENT_LIBRARY_CHANGED,library);
        }
        if (device == renderer)
        {
            Utils.log(dbg_main,0,"Renderer OFFLINE(" + device.getFriendlyName() + ")");
            renderer.stopRenderer(false);
            renderer = local_renderer;
            if (renderer != null)
                renderer.startRenderer();
            handleArtisanEvent(EVENT_RENDERER_CHANGED,renderer);
        }
        if (device == playlist_source)
        {
            Utils.log(dbg_main,0,"PlaylistSource OFFLINE(" + device.getFriendlyName() + ")");
            playlist_source.stopPlaylistSource(false);
            playlist_source = local_playlist_source;
            if (playlist_source != null)
                playlist_source.startPlaylistSource();
            handleArtisanEvent(EVENT_PLAYLIST_SOURCE_CHANGED,playlist_source);
        }
    }


    //---------------------------------------------------------------------
    // THE MAIN EVENT HANDLER
    //---------------------------------------------------------------------
    // TODO Need to change whole architecture .. don't like loopers in devices
    // Could use EVENT_IDLE (outside of UI thread) to get rid of other Timer Loops,
    // Call Renderer, LocalVolumeFixer, Volume etc to let them dispatch Artisan Events
    //
    // TODO Fix Volume Event Dispatching
    // Currently Changes made outside of Artisan to the underlying stream/mtc volume
    // are not evented back to Artisan, except when the VolumeControl window is open
    // and the device is hit during the RENDERER'S loop!

    @Override
    public void handleArtisanEvent(final String event_id,final Object data)
    // run on UI async task to pass events to UI clients
    {
        String dbg_data2 = data == null ? "null" : data.toString();
        dbg_data2 = dbg_data2.replaceFirst("\t|\n","==");
        dbg_data2 = dbg_data2.replaceAll("[\t|\n].*","");
        final String dbg_data = dbg_data2;

        int use_dbg = dbg_main+1;
        if (event_id.equals(EVENT_IDLE) ||
            event_id.equals(EVENT_POSITION_CHANGED))
            use_dbg++;

        Utils.log(use_dbg,0,"artisan.handleRendererEvent(" + event_id + ") " + dbg_data);

        // We do not wait for the UI thread to handle EVENT_DEVICE_STATUS_CHANGED
        // for a current device.  If a device goes offline, we call checkDeviceOffline(),
        // which sees if it is a current active device, and if so, shuts it down,
        // finds, and sets the local device, and issues the LIBRARY_CHANGED,
        // RENDERER_CHANGED, or PLAYLIST_SOURCE_CHANGED event

        if (event_id.equals(EVENT_DEVICE_STATUS_CHANGED))
        {
            Device device = (Device) data;
            if (device.getDeviceStatus() == Device.deviceStatus.OFFLINE)
            {
                checkCurrentDeviceOffline(device);
            }
        }



        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                int use_dbg = dbg_main;
                if (event_id.equals(EVENT_IDLE) ||
                    event_id.equals(EVENT_POSITION_CHANGED))
                    use_dbg++;
                Utils.log(use_dbg,0,"----> " + event_id + "(" + dbg_data + ")");

                //----------------------------------------------
                // Command Events
                //----------------------------------------------

                if (event_id.equals(COMMAND_EVENT_PLAY_TRACK))
                {
                    Track track = (Track) data;
                    if (renderer != null)
                        renderer.setRendererTrack(track,false);
                    return;
                }

                //----------------------------------------------------------
                // Device Events
                //----------------------------------------------------------
                // check NEW_DEVICES if still looking for a Default Devices

                if (event_id.equals(EVENT_NEW_DEVICE))
                {
                    Device device = (Device) data;
                    if (device.getDeviceGroup() == Device.deviceGroup.DEVICE_GROUP_LIBRARY &&
                        default_library_name.equals(device.getFriendlyName()) &&
                        findAndStartDefaultDevice(device.getDeviceGroup(),default_library_name))
                    {
                        default_library_name = "";
                    }
                    if (device.getDeviceGroup() == Device.deviceGroup.DEVICE_GROUP_RENDERER &&
                        default_renderer_name.equals(device.getFriendlyName()) &&
                        findAndStartDefaultDevice(device.getDeviceGroup(),default_renderer_name))
                    {
                        default_renderer_name = "";
                    }
                    if (device.getDeviceGroup() == Device.deviceGroup.DEVICE_GROUP_PLAYLIST_SOURCE &&
                        default_playlist_source_name.equals(device.getFriendlyName()) &&
                        findAndStartDefaultDevice(device.getDeviceGroup(),default_playlist_source_name))
                    {
                        default_playlist_source_name = "";
                    }
                }

                // give an error on SSDP_SEARCH_FINISHED if we have not
                // found the default devices ...

                if (event_id.equals(EVENT_SSDP_SEARCH_FINISHED))
                {
                    if (!default_library_name.isEmpty())
                        Utils.error("SSDPSearch could not find DEFAULT LIBRARY(" + default_library_name + ")");
                    if (!default_renderer_name.isEmpty())
                        Utils.error("SSDPSearch could not find DEFAULT RENDERER(" + default_renderer_name + ")");
                    if (!default_playlist_source_name.isEmpty())
                        Utils.error("SSDPSearch could not find DEFAULT PLAYLIST_SOURCE(" + default_playlist_source_name + ")");

                    default_library_name = "";
                    default_renderer_name = "";
                    default_playlist_source_name = "";
                }

                //----------------------------------------------------------
                // Global Events to main Views
                //----------------------------------------------------------
                // Send all events non-IDLE to all known event handlers
                // Note the selective dispatch to the volumeControl only
                // if it's showing

                if (!event_id.equals(EVENT_IDLE))
                {
                    if (main_menu != null &&
                        main_menu.getAlpha() != 0F &&
                        main_menu.getVisibility() == View.VISIBLE)
                        main_menu.handleArtisanEvent(event_id,data);

                    if (aRenderer != null &&
                        aRenderer.getView() != null)
                        aRenderer.handleArtisanEvent(event_id,data);

                    if (aPlaylist != null &&
                        aPlaylist.getView() != null)
                        aPlaylist.handleArtisanEvent(event_id,data);

                    if (aLibrary != null &&
                        aLibrary.getView() != null)
                        aLibrary.handleArtisanEvent(event_id,data);

                    if (aPrefs != null &&
                        aPrefs.getView() != null)
                        aPrefs.handleArtisanEvent(event_id,data);

                    if (volume_control != null &&
                        volume_control.isShowing())
                    {
                        volume_control.handleArtisanEvent(event_id,data);
                    }

                }   // Non-IDLE events


                //-----------------------------------------------------
                // UPnP Event dispatching
                //------------------------------------------------
                // if there's an http_server it means there are UPnP
                // Services ... we bump the use counts on the underlying
                // objects, and dispatch the UPnP events on EVENT_IDLE ..
                //
                // This should NOT be done except if the LocalRenderer is active

                if (http_server != null && renderer == local_renderer)
                {
                    UpnpEventManager event_manager = http_server.getEventManager();

                    if (event_id.equals(EVENT_STATE_CHANGED))
                    {
                        event_manager.incUpdateCount("Playlist");
                        event_manager.incUpdateCount("AVTransport");
                    }
                    else if (
                        event_id.equals(EVENT_PLAYLIST_CHANGED) ||
                        event_id.equals(EVENT_PLAYLIST_CONTENT_CHANGED) ||
                        event_id.equals(EVENT_PLAYLIST_TRACKS_EXPOSED))
                        event_manager.incUpdateCount("Playlist");

                    else if (event_id.equals(EVENT_TRACK_CHANGED))
                    {
                        event_manager.incUpdateCount("AVTransport");
                        event_manager.incUpdateCount("Info");
                    }
                    else if (event_id.equals(EVENT_POSITION_CHANGED))
                        event_manager.incUpdateCount("Time");

                    else if (event_id.equals(EVENT_VOLUME_CHANGED))
                    {
                        event_manager.incUpdateCount("Volume");
                        event_manager.incUpdateCount("RenderingControl");
                    }

                    else if (event_id.equals(EVENT_VIRTUAL_FOLDER_CHANGED))
                    {
                        event_manager.incUpdateCount("ContentDirectory");
                    }

                    else if (!defer_openhome_events &&
                        event_id.equals(EVENT_IDLE))
                        event_manager.send_events();
                }

            }   // run()
        }); // runOnUiThread()
    }   // handleArtisanEvent()



}   // class Artisan
