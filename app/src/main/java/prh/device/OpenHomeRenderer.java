package prh.device;

import org.w3c.dom.Document;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.artisan.Playlist;
import prh.artisan.Renderer;
import prh.artisan.Track;
import prh.artisan.Volume;
import prh.device.service.OpenInfo;
import prh.device.service.OpenPlaylist;
import prh.device.service.OpenTime;
import prh.device.service.OpenVolume;
import prh.device.service.OpenProduct;
import prh.artisan.CurrentPlaylistExposer;
import prh.utils.Utils;


// This device is many things
// Be careful of name collisions between Renderer and Playlist

public class OpenHomeRenderer extends Device implements
    Renderer,
    Playlist
{
    // Service accessors

    private OpenTime     getOpenTime()      { return (OpenTime) services.get("OpenTime"); }
    private OpenVolume   getOpenVolume()    { return (OpenVolume) services.get("OpenVolume"); }
    private OpenInfo     getOpenInfo()      { return (OpenInfo) services.get("OpenInfo"); }
    private OpenPlaylist getOpenPlaylist()  { return (OpenPlaylist) services.get("OpenPlaylist"); }
    private OpenProduct  getOpenProduct()   { return (OpenProduct) services.get("OpenProduct"); }

    // member vars

    private boolean is_dirty = false;
    private boolean is_started = false;




    //---------------------------
    // Constructors
    //---------------------------

    public OpenHomeRenderer(Artisan artisan)
    {
        super(artisan);
    }


    public OpenHomeRenderer(Artisan artisan, SSDPSearchDevice ssdp_device)
    {
        super(artisan,ssdp_device);
        Utils.log(0,1,"new OpenHomeRenderer(" + ssdp_device.getFriendlyName() + "," + ssdp_device.getDeviceType() + "," + ssdp_device.getDeviceUrl());
    }


    //------------------------------------------------------
    // startRenderer() and stopRenderer()
    //------------------------------------------------------

    @Override public boolean startRenderer()
    {
        // Announce ourself to to the httpServer thru Artisan

        artisan.notifyOpenHomeRenderer(this,true);

        // SUBSCRIBE to the services.
        // If we fail to subscribe to any,
        // we UNSUNBSCRIBE from those already done.

        // Wait for a complete set of initial
        // events from the subscriptions.

        // Set the initial renderer state

        // Setup the playlist fetcher.

        // and away we go ...

        return false;
    }

    @Override public void stopRenderer(boolean wait_for_stop)
    {
    }


    //-------------------------------------------
    // Renderer Interface
    //-------------------------------------------


    @Override public void notifyPlaylistChanged()
    {

    }

    @Override public String getRendererName()   { return getFriendlyName();   }
    @Override public Volume getVolume()         { return (Volume) getOpenVolume(); }
    @Override public int getTotalTracksPlayed() { return getOpenTime().getTotalTracksPlayed(); }
    @Override public int getPosition()          { return getOpenTime().getElapsed(); }


    @Override public String getRendererState()
    {
        return null;
    }

    @Override public String getRendererStatus()
    {
        return null;
    }

    @Override public boolean getShuffle() { return false; }
    @Override public boolean getRepeat()  { return false; }
    @Override public void setRepeat(boolean value) {  }
    @Override public void setShuffle(boolean value) {  }

    @Override public void transport_pause() {  }
    @Override public void transport_play()  {  }
    @Override public void transport_stop()  {  }
    @Override public void incAndPlay(int offset)  {  }

    @Override public void seekTo(int progress)    {  }
    @Override public Track getRendererTrack() { return null; }
    @Override public void setRendererTrack(Track track, boolean interrupt_playlist)   {  }


    //-------------------------------------------------------
    // Playlist Interface
    //-------------------------------------------------------

    @Override public void startPlaylist() {}
    @Override public void stopPlaylist(boolean wait_for_stop)  {}

    @Override public boolean isStarted() { return is_started; }
    @Override public boolean isDirty()   { return is_dirty; }
    @Override public void setDirty(boolean b) { is_dirty = b; }

    // my playlist api - from database

    @Override public String getPlaylistName() { return ""; }
    @Override public int getPlaylistNum()     { return 0; }
    @Override public int getNumTracks()       { return 0; }
    @Override public int getCurrentIndex()    { return 0; }
    @Override public Track getCurrentTrack()  { return null; }
    @Override public int getMyShuffle()       { return 0; }
    @Override public String getQuery()        { return ""; }

    // my playlist api continued

    @Override public int getContentChangeId()     { return 0; }
    @Override public void saveIndex(int index)    {  }

    @Override public Track getPlaylistTrack(int index)    { return null; }
    @Override public Track incGetTrack(int inc)   { return null; }
    @Override public boolean removeTrack(Track track) { return false; }
    @Override public Track insertTrack(int position, Track track) { return null; }

    // OpenHome Support

    @Override public Track getByOpenId(int open_id) { return null; }
    @Override public Track insertTrack(int after_id, String uri, String metadata) { return null; }
    @Override public Track seekByIndex(int index) { return null; }
    @Override public Track seekByOpenId(int open_id) { return null; }
    @Override public Track insertTrack(Track track, int after_id) { return null; }
    @Override public boolean removeTrack(int open_id, boolean dummy_for_open_id) { return false; }

    @Override public String getIdArrayString(CurrentPlaylistExposer exposer)  { return ""; }
    @Override public int[] string_to_id_array(String id_string) { return null; }
    @Override public String id_array_to_tracklist(int ids[]) { return null; }


    //-------------------------------------------
    // httpRequestHandler Interface
    //-------------------------------------------
    // SSDP Event Callback from a Service on the remote
    // OpenHomeRenderer to which we have subscribed.
    //
    // The full path is /openCallback/device_uuid/Service/event
    // method below only receive the Service name


    public NanoHTTPD.Response response(
        NanoHTTPD.IHTTPSession session,
        NanoHTTPD.Response response,
        String service,
        Document doc)
    {
        boolean is_loop_action = service.equals("Time");

        String dbg_from = "OpenHomeRenderer Callback(" + service + ")";
        if (!is_loop_action)
            Utils.log(0,1,dbg_from);

        return response;
    }

}
