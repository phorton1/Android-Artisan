package prh.artisan;

import android.app.Activity;
import android.app.usage.UsageEvents;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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
import android.widget.Toast;


import prh.device.DeviceManager;
import prh.server.HTTPServer;
import prh.server.LocalVolumeFixer;
import prh.server.SSDPServer;
import prh.utils.Utils;


public class Artisan extends FragmentActivity implements
    EventHandler
{
    public static boolean START_SSDP_IN_HTTP = false;
        // if true, the ssdp_server member of Artisan will be ignored,
        // and the ssdp_server will be created, and started, in the
        // http server.  Otherwise it is created here.

    private static int NUM_PAGER_ACTIVITIES = 5;

    // system working variables

    private int current_page = -1;
    private boolean is_full_screen = false;
    private ViewPager view_pager = null;
    private pageChangeListener page_change_listener = null;
    private WifiManager.WifiLock wifi_lock = null;

    // child (fragment) activities

    private aPrefs    aPrefs    = new aPrefs();
    private aPlaying  aPlaying  = new aPlaying();
    private aPlaylist aPlaylist = new aPlaylist();
    private aLibrary  aLibrary  = new aLibrary();
    private aExplorer aExplorer = new aExplorer();

    // The volume control dialog (fragment activity)
    // The volume control itself is owned by the renderer

    private VolumeControl volume_control = null;

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

    // and finally, the device manager

    private DeviceManager device_manager = null;


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
        view_pager.setCurrentItem(1);
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

        // send the initial messages

        if (library != null)
        {
            Utils.log(0,0,"Local Library created ");
            handleEvent(EventHandler.EVENT_LIBRARY_CHANGED,library);
        }
        else
            Utils.warning(0,0,"Local Library not created");

        if (renderer != null)
        {
            Utils.log(0,0,"Local Renderer created ");
            renderer.startRenderer();
            handleEvent(EventHandler.EVENT_RENDERER_CHANGED,renderer);
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
                try
                {
                    http_server = new HTTPServer(this);
                    http_server.start();
                }
                catch (Exception e)
                {
                    Utils.error("Error starting http_server:" + e.toString());
                    http_server = null;
                }

                // The ssdp server can be owned by, and started from the http server to make
                // sure that the http server is properly setup before allowing any requests

                if (!START_SSDP_IN_HTTP && http_server != null)
                {
                    Utils.log(0,0,"starting ssdp_server ...");
                    SSDPServer ssdp_server = new SSDPServer(this);
                    Thread ssdp_thread = new Thread(ssdp_server);
                    ssdp_thread.start();
                    Utils.log(0,0,"ssdp_server started");
                }
            }
        }


        // 5 = finally, construct the DeviceManager and do an SSDP Search
        // we will be called back via handleEvent() for any Devices
        // in the cache, or found in the SSDP Search.

        device_manager = new DeviceManager(this);
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

        if (!START_SSDP_IN_HTTP)
        {
            if (ssdp_server != null)
                ssdp_server.shutdown();
            ssdp_server = null;
        }

        if (http_server != null)
            http_server.stop();
        http_server = null;

        if (volume_fixer != null)
            volume_fixer.stop();
        volume_fixer = null;


        // 3 = stop the current library and renderer

        if (renderer != null)
            renderer.stop();

        if (library != null)
            library.stop();
        library = null;

        if (local_renderer != null)
            local_renderer.stop();

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
    // Utilities
    //-------------------------------------------------------

    public class myPagerAdapter extends FragmentPagerAdapter
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
        }
    }




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



    void showFullScreen(boolean full)
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

    void setPlayList(String name)
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
                aPlaying.handleEvent(EventHandler.EVENT_PLAYLIST_CHANGED,playlist);
        }
   }



    public void handleEvent(final String action,final Object data)
        // run on UI async task to pass events to UI clients
    {
        if (!action.equals(EventHandler.EVENT_POSITION_CHANGED))
            Utils.log(1,0,"artisan.handleRendererEvent(" + action + ")");

        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {

                if (!action.equals(EventHandler.EVENT_POSITION_CHANGED) &&
                    !action.equals(EventHandler.EVENT_IDLE))
                    Utils.log(0,0,"----> " + action);

                if (volume_control != null &&
                    volume_control.isShowing() &&
                    (action.equals(EventHandler.EVENT_VOLUME_CHANGED) ||
                     action.equals(EventHandler.EVENT_VOLUME_CONFIG_CHANGED)))
                {
                    volume_control.handleEvent(action,data);
                }

                // dispatch open home events if IDL

                if (action.equals(EventHandler.EVENT_IDLE))
                {
                    if (http_server != null)
                        http_server.sendOpenHomeEvents();
                }

                // send all non-IDLE events to the aPlaying window

                else if (aPlaying != null && aPlaying.getView() != null)
                {
                    // most EVENTS have associcated possible
                    // openHome event senders that need to be bumped

                    if (http_server != null)
                    {
                        if (action.equals(EventHandler.EVENT_STATE_CHANGED) ||
                            action.equals(EventHandler.EVENT_PLAYLIST_CHANGED))
                            http_server.incUpdateCount("Playlist");

                        if (action.equals(EventHandler.EVENT_TRACK_CHANGED))
                            http_server.incUpdateCount("Info");

                        if (action.equals(EventHandler.EVENT_POSITION_CHANGED))
                            http_server.incUpdateCount("Time");

                        if (action.equals(EventHandler.EVENT_VOLUME_CHANGED))
                            http_server.incUpdateCount("Volume");

                    }

                    aPlaying.handleEvent(action,data);
                }

            }
        });
    }


    public void doVolumeControl()
    {
        Utils.log(0,0,"doVolumeControl()");
        if (volume_control != null &&
            renderer != null &&
            renderer.getVolume() != null)
            volume_control.show();
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


}   // class Artisan
