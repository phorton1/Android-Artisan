package prh.artisan;

// ARCHITCTURAL NOTES
//
// Loops and Events
//
//      We studiously try to not introduce Timer Loops into the program.
//      There are currently four timer loops: two minor, one well behaved,
//      and simple, and one important and hard to manage.
//
//      LocalVolumeFixer has a timer loop once per second to reconcile MTC and
//      Android volumes only on the Car Stereo. The SSDPServer has a long timer,
//      like once every 30 seconds, to broadcast a Keep-Alive message.
//      The SSDP Search, which itself is a Thread Runnable, and called as such,
//      essentially has a blocking timer loop for the duration of the call to run(),
//      which entirely notifies Artisan of EVENT_NEW_DEVICE as it proceeds.
//
//      These are all low level "faceless" objects that do not make any direct
//      UI calls. There are NO TIMER LOOPS IN THE UI.  All low level objects that
//      may cause UI changes do so by dispatching ArtisanEvents to Artisan, who in
//      turn dispatches them to the appropriate UI activities. The above four cases
//      are relatively simple, and either don't need to dispatch events, or dispatch
//      a small set of well understood Artisan Events.
//
//      The main, and complex case of a Timer loop is in Renderers.
//
//      Otherwise, THE ENTIRE UI IS EVENT DRIVEN.
//
// Renderer Timer Loop, EVENT_IDLE, and UPnP Event Subscribers
//
//      Each Derived Renderer has a polling timer loop (to poll the state of the local
//      media_player or remote DLNA MediaServer) and dispatches a variety of Artisan
//      events as it notices changes.
//
//      This timer loop does what it needs to do, but also dispatches EVENT_IDLE
//      on each go-round.  The occurrence of the EVENT_IDLE artisan event is crucial
//      in the implementation, because it triggers the dispatching of UPnP Events
//      to subscribed clients.  Without a Renderer, no subscribed SSDP devices will
//      receive any SSDP Events. Yet not all subscribers are to a Renderer .. they
//      can also subscribe to the ContentServer which is logically independent
//      of the Renderer.  Yet, there MUST BE A RENDERER PRESENT for them to
//      get notfied.  This is just a fact of the implementation that should be known.
//
//      Although, logically, the dispatching of UPnP Events has nothing to do with
//      the UI thread, it is implemented in the Artisan.handleArtisanEvent() method,
//      as there is a significant correspondence between Artisan Events and UPnP
//      Events.  We use the Artisan Event to bump a count of changes on the UPnP
//      services (UpnpEventHandlers owned by the HTTPServer and registered with
//      the UpnpEventManager), and then call UpnpEventManager.send_events() on
//      each IDLE_EVENT.

// UI Thread and Network Requests
//
//      The UI Objects, like aPlaying, aPreferences, aPlaylist, etc, are free to
//      access each other and make directly method calls, as they all "live" in the
//      main UI thread.
//
//      The devices, services, servers, etc - all low level faceless objects - call
//      Artisan.handleArtisanEvent() to notify the UI of changes. The main dispatcher,
//      Artisan.handleArtisanEvent() uses the built in Android runOnUiThread() method
//      to block and wait for the UI thread to become available.   It should be the
//      only call to runOnUiThread() in the system.
//
//      The Android OS does not allow us to make network requests on the main UI Thread.
//
//      This results in the need to fire off threads, and have handlers, for anything that
//      hits the network ... like getting html or xml pages ... which in turn means for doing
//      anything interesting, particularly from the faceless objects, like issuing UPnP actions,
//      doing SSDP Searches and getting device descriptions, etc.
//
//      Therefore we generically implement a set Network Routines that can be used either
//      Asynchronously, by passing in a handler for the results, or emulating Synchronous
//      Network requests, by waiting for the reply ourselves, possibly on the UI thread.
//      This results in a lot of additional complexity.
//
//      This complexity resulting from the use of Asynchronous network requests should be
//      avoided in UI code.  It is nice if the UI code not only can be called safely from
//      anywhere in the main UI thread, but it is also nice if it does not set up it's own
//      asyncrhonous handlers.  If you find the need to setup an asynchronous handler in the
//      UI code, it probably means there should be a low level object and Event created.
//
//      Factoid: the image in Now Playing is loaded via an asynchronous network request
//      that is also RunOnUiThread().  This I think of as a local implementation of a
//      synchronous Network Request.
//
// Truly Asynchronous Network Events
//
//      Otherwise, Aynchronous Network requests are hidden from the system, and limited
//      to private usage in a single low level faceless object. That object then responds
//      to the completion and does what it needs to do, and usually sends out Artisan Events
//      as a result.


import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;

import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;


import prh.device.Device;
import prh.device.DeviceManager;
import prh.server.HTTPServer;
import prh.server.LocalVolumeFixer;
import prh.server.SSDPServer;
import prh.server.http.UpnpEventManager;
import prh.utils.Utils;



public class Artisan extends FragmentActivity implements
    EventHandler
{
    private static int NUM_PAGER_ACTIVITIES = 5;

    // system working variables

    private int current_page = -1;
    private boolean is_full_screen = false;
    private ViewPager view_pager = null;
    private pageChangeListener page_change_listener = null;
    private WifiManager.WifiLock wifi_lock = null;
    public ViewPager getViewPager() { return view_pager; };

    // child (fragment) activities

    private aPrefs    aPrefs    = new aPrefs();
    private aPlaying  aPlaying  = new aPlaying();
    private aPlaylist aPlaylist = new aPlaylist();
    private aLibrary  aLibrary  = new aLibrary();
    private aExplorer aExplorer = new aExplorer();

    // The volume control dialog (fragment activity)
    // The volume control itself is owned by the renderer

    private VolumeControl volume_control = null;
    public VolumeControl getVolumeControl() { return volume_control; }

    // Lifetime Local Objects

    private Database database = null;
    private Library local_library = null;
    private Renderer local_renderer = null;
    public Database getDatabase() { return database; }
    public Library getLocalLibrary() { return local_library; }
    public Renderer getLocalRenderer() { return local_renderer; }

    // The currently selected renderer and library

    private Library library = null;
    private Renderer renderer = null;
    public Library getLibrary() { return library; }
    public Renderer getRenderer() { return renderer; }

    // servers (no public access)

    private HTTPServer http_server = null;
    private LocalVolumeFixer volume_fixer = null;

    // this is only public to allow START_SSDP_IN_HTTP

    public SSDPServer ssdp_server = null;

    // and finally, the device manager and default renderer and library names

    private DeviceManager device_manager = null;
    public DeviceManager getDeviceManager() { return device_manager; }

    private String default_renderer_name = "";
    private String default_library_name = "";
        // if these are non-blank, we have to keep trying until the
        // SSDPSearch finishes to set the correct renderer ...

    //--------------------------------------------------------
    // onCreate()
    //--------------------------------------------------------


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Utils.log(0,0,"------ Artisan.onCreate() started ------");

        // 0 = static initialization

        Utils.static_init(this);
        Prefs.static_init(this);
        default_renderer_name = Prefs.getString(Prefs.id.DEFAULT_RENDERER);
        if (default_renderer_name.equals(Prefs.LAST_SELECTED))
            default_renderer_name = Prefs.getString(Prefs.id.SELECTED_RENDERER);
        if (!default_renderer_name.isEmpty())
            Utils.log(0,0,"DEFAULT_RENDERER(" + default_renderer_name +")");

        // 1 = low level initialization

        database = new Database();
        if (!database.start())
            database = null;

        if (Utils.server_ip != null &&
            Prefs.getBoolean(Prefs.id.KEEP_WIFI_ALIVE))
        {
            WifiManager wifiMan = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            wifi_lock = wifiMan.createWifiLock(getClass().toString());
            wifi_lock.acquire();
        }

        // 2. create views
        // create the main view and sub-views
        // set the main menu click handler

        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_artisan);

        View.OnClickListener show_listener = new View.OnClickListener()
        { public void onClick(View view) { showFullScreen(!is_full_screen); }};
        findViewById(R.id.artisan_main_menu).setOnClickListener(show_listener);

        // create the main "activity" fragments
        // and the pager, listener, and adapter

        aPrefs    = new aPrefs();
        aPlaying  = new aPlaying();
        aPlaylist = new aPlaylist();
        aLibrary  = new aLibrary();
        aExplorer = new aExplorer();

        page_change_listener = new pageChangeListener(this);
        myPagerAdapter my_pager_adapter = new myPagerAdapter(getSupportFragmentManager());
        view_pager = (ViewPager) findViewById(R.id.artisan_content);
        view_pager.setAdapter(my_pager_adapter);
        view_pager.addOnPageChangeListener(page_change_listener);

        // set the starging page
        // startup page == Now Playing

        volume_control = new VolumeControl(this);
        showFullScreen(true);
            // start off in full screen mode
            // with the menu showing


        // 3 = Create the local library and renderer
        // and set the default to them.  The renderer
        // should always construct correctly, but we
        // only construct the library if the database
        // is present.

        if (Prefs.getBoolean(Prefs.id.START_LOCAL_RENDERER))
            local_renderer = new LocalRenderer(this);

        if (database != null &&
            Prefs.getBoolean(Prefs.id.START_LOCAL_LIBRARY))
            local_library = new LocalLibrary(this);

        library = local_library;
        renderer = local_renderer;

        // start car stereo in now playing, emulator in prefs

        if (Build.ID.equals(Utils.ID_CAR_STEREO))
            current_page = 1;
        else
            current_page = 1;

        view_pager.setCurrentItem(current_page);
        setCurrentPageTitle();

        // send the initial messages

        if (library != null)
        {
            Utils.log(0,0,"Local Library created ");
            handleArtisanEvent(EventHandler.EVENT_LIBRARY_CHANGED,library);
        }
        else
            Utils.warning(0,0,"Local Library not created");

        if (renderer != null)
        {
            Utils.log(0,0,"Local Renderer created ");
            renderer.startRenderer();

            // this event is too early
            // the aPlaying view has not been initallized yet
            // When the aPlaying view contructs itself, it gets the current renderer
            //
            // handleArtisanEvent(EventHandler.EVENT_RENDERER_CHANGED,renderer);
        }
        else
            Utils.warning(0,0,"Local Renderer not created");


        // 4 = start servers

        if (Prefs.getBoolean(Prefs.id.START_VOLUME_FIXER) &&
            Utils.ID_CAR_STEREO.equals(Build.ID))
        {
            volume_fixer = new LocalVolumeFixer(this);
            volume_fixer.start();
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
                    http_server = new HTTPServer(this);
                }
                catch (Exception e)
                {
                    Utils.error("Error starting http_server:" + e.toString());
                    http_server = null;
                }

                // ssdp_server

                if (http_server != null)
                {
                    http_server.start();

                    Utils.log(0,0,"starting ssdp_server ...");
                    SSDPServer ssdp_server = new SSDPServer(this);
                    Thread ssdp_thread = new Thread(ssdp_server);
                    ssdp_thread.start();
                    Utils.log(0,0,"ssdp_server started");
                }
            }
        }


        // 5 = finally, construct the DeviceManager and do an SSDP Search
        // we will be called back via handleArtisanEvent() for any Devices
        // in the cache, or found in the SSDP Search.

        device_manager = new DeviceManager(this);

        // see if we can find the default renderer
        // clear the name, even if it fails to start
        // so we don't try again ...

        if (!default_renderer_name.isEmpty())
        {
            Device found = device_manager.getDevice(Device.DEVICE_MEDIA_RENDERER,default_renderer_name);
            if (found != null)
            {
                Utils.log(0,0,"STARTING DEFAULT RENDERER " + default_renderer_name);
                setRenderer(default_renderer_name);
                default_renderer_name = "";
            }
            else
            {
                Utils.log(0,0,"Could not find DEFAULT RENDERER " + default_renderer_name + " in onCreate() ... continuing to look ...");
            }
        }


        device_manager.doDeviceSearch();

        Utils.log(0,0,"------ Artisan.onCreate() finished ------");

    }   // onCreate()


    @Override
    public void onResume()
    {
        Utils.log(0,0,"Artisan.onResume()");
        super.onResume();
    }

    @Override
    public void onPause()
    {
        Utils.log(0,0,"Artisan.onPause()");
        super.onPause();
    }


    @Override
    public void onDestroy()
    {
        Utils.log(0,0,"Artisan.onDestroy() started ------");
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


        // 3 = stop the current library and renderer

        if (renderer != null)
            renderer.stopRenderer();
        renderer = null;

        if (library != null)
            library.stop();
        library = null;

        if (local_renderer != null)
            local_renderer.stopRenderer();

        if (local_library != null)
            local_library.stop();
        library = null;


        // 2 = unwind view pager and fragments

        if (volume_control != null)
            volume_control.dismiss();
        volume_control = null;

        if (view_pager != null)
            view_pager.removeOnPageChangeListener(page_change_listener);
        page_change_listener = null;
        view_pager = null;

        aPrefs     = null;
        aPlaying   = null;
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
        Utils.log(0,0,"Artisan.onDestroy() finished ------");
    }


    //-------------------------------------------------------
    // Paging
    //-------------------------------------------------------

    private class myPagerAdapter extends FragmentPagerAdapter
    {
        public myPagerAdapter(FragmentManager fm)       { super(fm);  }
        @Override public int getCount()                 { return NUM_PAGER_ACTIVITIES; }
        @Override public Fragment getItem(int position)
        {
            if (position==0) return aPrefs;
            if (position==1) return aPlaying;
            if (position==2) return aPlaylist;
            if (position==3) return aLibrary;
            if (position==4) return aExplorer;
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
            if (!is_full_screen && current_page >= 0)
                setCurrentPageFullscreenClickHandler(false);
            current_page = index;
            if (!is_full_screen && current_page >= 0)
                setCurrentPageFullscreenClickHandler(true);

            if (current_page >= 0)
                setCurrentPageTitle();

        }
    }


    private void setCurrentPageTitle()
    {
        myPagerAdapter adapter = (myPagerAdapter) view_pager.getAdapter();
        ArtisanPage page = (ArtisanPage) adapter.getItem(current_page);
        if (page.getName().equals("Now Playing"))
            aPlaying.update_whats_playing_message();
        else
            SetMainMenuText(page.getName(),page.getName());
    }


    private void setCurrentPageFullscreenClickHandler(boolean setit)
    {
        if (current_page >= 0)
        {
            myPagerAdapter adapter = (myPagerAdapter) view_pager.getAdapter();
            Fragment fragment = adapter.getItem(current_page);
            View view = fragment.getView();
            if (view != null)
                fragment.getView().setOnClickListener( !setit ? null :
                    new View.OnClickListener()
                    {
                        public void onClick(View view) { showFullScreen(!is_full_screen); }
                    });
        }
    }


    //-------------------------------------------------------
    // Utilities
    //-------------------------------------------------------


    public void onUtilsError(final String msg)
    {
        runOnUiThread( new Runnable()
        {
            @Override public void run()
            {
                Toast.makeText(getApplicationContext(),msg,Toast.LENGTH_LONG).show();
            }
        });
    }


    public void SetMainMenuText(String from,String text)
        // only accepts text from the current page
    {
        myPagerAdapter adapter = (myPagerAdapter) view_pager.getAdapter();
        ArtisanPage page = (ArtisanPage) adapter.getItem(current_page);
        if (page != null && page.getName().equals(from))
        {
            TextView title = (TextView) findViewById(R.id.main_menu_text);
            title.setText(text);
        }
    }


    private void showFullScreen(boolean full)
        // if fullscreen, then our menu disappears and
        // is replaced by the system StatusBar ..
        // otherwise, we show our menu as the same
        // height as the system StatusBar.
    {
        // show the status bar itself

        getWindow().setFlags(
            full ? WindowManager.LayoutParams.FLAG_FULLSCREEN : 0,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // display or hide the main_menu
        // main_menu shows when full
        // we get the status bar height
        // otherwise, set menu height to 0

        View main_menu = findViewById(R.id.artisan_main_menu);
        LinearLayout.LayoutParams params =
            (LinearLayout.LayoutParams) main_menu.getLayoutParams();

        params.height = 0;
        if (full)
        {
            int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0)
                params.height = getResources().getDimensionPixelSize(id);
            else
                params.height = 60; // bogus fallback
        }
        main_menu.setLayoutParams(params);

        // set/remove click listeners on the content view

        if (true)
        {
            setCurrentPageFullscreenClickHandler(!full);
        }

        is_full_screen = full;
    }



    //-------------------------------------------------------
    // Interactions
    //-------------------------------------------------------

    public boolean setRenderer(String name)
    {
        String cur_name = "";
        Utils.log(0,0,"--- setRenderer(" + name + ")");

        // bail if its the same renderer

        if (renderer != null)
            cur_name = renderer.getName();
        if (cur_name.equals(name))
        {
            Utils.log(0,0,"ignoring attempt to set to same renderer");
            return true;
        }

        // find the new renderer
        // bail if it's not found

        Renderer new_renderer = null;
        if (name.equals(Device.DEVICE_LOCAL_RENDERER))
            new_renderer = local_renderer;
        else
            new_renderer = (Renderer) device_manager.getDevice(Device.DEVICE_MEDIA_RENDERER,name);

        if (new_renderer == null)
        {
            Utils.error("Attempt to set Renderer to non-existing Device(" + name + ")");
            return false;
        }

        // try to start the new renderer
        // bail if it fails.

        if (!new_renderer.startRenderer())
        {
            Utils.error("Could not start renderer: " + name);
            return false;
        }

        // if that worked, stop the old one
        // save the selection to the prefs
        // and send out the renderer changed event

        if (renderer != null)
            renderer.stopRenderer();
        renderer = new_renderer;

        String use_name = name;
        if (use_name.equals(Device.DEVICE_LOCAL_RENDERER))
            use_name = "";
        Prefs.putString(Prefs.id.SELECTED_RENDERER,use_name);

        Utils.log(0,0,"--- setRenderer(" + name + ") finishing and dispatching EVENT_RENDERER_CHANGED(" + renderer.getName() + ")");
        handleArtisanEvent(EVENT_RENDERER_CHANGED,renderer);
        return true;

    }


    public void setPlayList(String name)
        // called from station button, we select the new
        // playlist and event it to any interested clients
    {
        Utils.log(0,0,"setPlaylist(" + name + ")");
        PlaylistSource playlist_source = renderer.getPlaylistSource();
        if (playlist_source != null)
        {
            // tell the renderer the playlist changed
            // it will event clients thru us
            // or directly event clients if there's no renderer

            Playlist playlist = playlist_source.getPlaylist(name);
            if (renderer != null)
                renderer.setPlaylist(playlist);
            else
                aPlaying.handleArtisanEvent(EventHandler.EVENT_PLAYLIST_CHANGED,playlist);
        }
   }



    public void doVolumeControl()
    {
        Utils.log(0,0,"doVolumeControl()");
        if (volume_control != null &&
            renderer != null &&
            renderer.getVolume() != null)
        {
            volume_control.show();
        }
    }


    public void onMainMenuNavigation(View v)
    {
        switch (v.getId())
        {
            case R.id.main_menu_icon:

                Utils.log(0,0,"onClickBack()");
                ImageView artisan_icon = (ImageView) findViewById(R.id.main_menu_icon);
                PopupMenu popup = new PopupMenu(this,artisan_icon);
                popup.getMenuInflater().inflate(R.menu.main_menu_left,popup.getMenu());
                popup.setOnMenuItemClickListener(
                    new PopupMenu.OnMenuItemClickListener()
                    {
                        public boolean onMenuItemClick(MenuItem item)
                        {
                            if (item.getItemId() == R.id.main_menu_exit)
                            {
                                finish();
                            }
                            return true;
                        }
                    } );
                popup.show();
                break;

            case R.id.main_menu_home_button:
                Utils.log(0,0,"onClickHome()");
                Intent i = new Intent();
                i.setAction(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_HOME);
                v.getContext().startActivity(i);
                break;

            case R.id.main_menu_context_button:
                break;
        }
    }



    //---------------------------------------------------------------------
    // EVENT HANDLING
    //---------------------------------------------------------------------

    public void handleArtisanEvent(final String event_id,final Object data)
    // run on UI async task to pass events to UI clients
    {
        if (!event_id.equals(EVENT_POSITION_CHANGED))
            Utils.log(1,0,"artisan.handleRendererEvent(" + event_id + ")");

        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                // selective debugging

                if (!event_id.equals(EVENT_POSITION_CHANGED) &&
                    !event_id.equals(EVENT_IDLE))
                    Utils.log(0,0,"----> " + event_id);

                // check NEW_DEVICES if still looking for a renderer

                if (event_id.equals(EVENT_NEW_DEVICE) &&
                    !default_renderer_name.isEmpty() )
                {
                    Device device = (Device) data;
                    String type = device.getDeviceType();
                    if (type.equals(Device.DEVICE_MEDIA_RENDERER) ||
                        type.equals(Device.DEVICE_OPEN_HOME))
                    {
                        if (default_renderer_name.equals(device.getFriendlyName()))
                        {
                            Utils.log(0,0,"FOUND DEFAULT RENDERER " + default_renderer_name);
                            setRenderer(default_renderer_name);
                            default_renderer_name = "";
                        }
                    }
                }

                // give an error on SSDP_SEARCH_FINISHED if we have not
                // found the default renderer ...

                if (event_id.equals(EVENT_SSDP_SEARCH_FINISHED) &&
                    !default_renderer_name.isEmpty() )
                {
                    Utils.error("SSDPSearch could not find DEFAULT RENDERER(" + default_renderer_name + ")");
                    default_renderer_name = "";
                }

                // Send all events non-IDLE to all known event handlers
                // prh = Could use this to get rid of other Timer Loops,
                // i.e. to hit LocalVolumeFixer and polling Volume objects.
                // Note the selective dispatch to the volumeControl only
                // if it's showing

                if (!event_id.equals(EVENT_IDLE) &&
                    !event_id.equals(EVENT_VOLUME_CHANGED))
                {
                    if (aPlaying != null &&
                        aPlaying.getView() != null)
                        aPlaying.handleArtisanEvent(event_id,data);

                    if (aPrefs != null && aPrefs.getView() != null)
                        aPrefs.handleArtisanEvent(event_id,data);

                }

                // only send volume changes if it's open,
                // but send config messages event if it's closed

                if (event_id.equals(EVENT_VOLUME_CHANGED))
                {
                    if (volume_control != null &&
                        volume_control.isShowing())
                    {
                        volume_control.handleArtisanEvent(event_id,data);
                    }
                }

                // UPnP Event dispatching .. if there's an http_server
                // it means therer are UPnP Services ... we bump the
                // use counts on the underlying objects, and dispatch
                // the UPnP events on EVENT_IDLE ..

                if (http_server != null)
                {
                    UpnpEventManager event_manager = http_server.getEventManager();

                    if (event_id.equals(EVENT_STATE_CHANGED) ||
                        event_id.equals(EVENT_PLAYLIST_CHANGED))
                        event_manager.incUpdateCount("Playlist");

                    else if (event_id.equals(EVENT_TRACK_CHANGED))
                        event_manager.incUpdateCount("Info");

                    else if (event_id.equals(EVENT_POSITION_CHANGED))
                        event_manager.incUpdateCount("Time");

                    else if (event_id.equals(EVENT_VOLUME_CHANGED))
                        event_manager.incUpdateCount("Volume");

                    else if (event_id.equals(EVENT_IDLE))
                        event_manager.send_events();
                }


            }   // run()
        }); // runOnUiThread()
    }   // handleArtisanEvent()



}   // class Artisan
