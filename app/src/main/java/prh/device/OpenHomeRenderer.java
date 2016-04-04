package prh.device;

import org.w3c.dom.Document;

import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.base.Renderer;
import prh.artisan.Track;
import prh.base.Volume;
import prh.device.service.OpenInfo;
import prh.device.service.OpenPlaylist;
import prh.device.service.OpenTime;
import prh.device.service.OpenVolume;
import prh.device.service.OpenProduct;
import prh.server.HTTPServer;
import prh.base.UpnpEventReceiver;
import prh.types.stringHash;
import prh.utils.Utils;


// A class that presents a remote OpenHome device as a Renderer.
// Because an OpenHome device is also has a playlist, there is
// a tight binding between this object (the device.OpenPlaylist)
// and the tempEditablePlaylist as seen by the rest of the system.

// Unlike the other Renderers, the OpenHome device does not have
// a looper, and does not call Artisan.handleArtisanEvent(EVENT_IDLE).
// (which should really only be called by the LocalRenderer anyways).
// It is entirely Event Driven, notifying the UI via explicit Artisan
// Events whenever anything changes.

// TODO: Implement to use Pref to NOT use remote OpenHomeRenderer events

public class OpenHomeRenderer extends Device implements
    Renderer
{
    static int dbg_ohr = 0;

    // Service accessors

    private OpenTime getOpenTime()          { return (OpenTime)     services.get(Service.serviceType.OpenTime); }
    private OpenVolume getOpenVolume()      { return (OpenVolume)   services.get(Service.serviceType.OpenVolume); }
    private OpenInfo getOpenInfo()          { return (OpenInfo)     services.get(Service.serviceType.OpenInfo); }
    private OpenPlaylist getOpenPlaylist()  { return (OpenPlaylist) services.get(Service.serviceType.OpenPlaylist); }
    private OpenProduct getOpenProduct()    { return (OpenProduct)  services.get(Service.serviceType.OpenProduct); }

    private class subscriptionHash extends HashMap<Service.serviceType,Subscription> {}
    private subscriptionHash subscriptions;


    //---------------------------
    // Constructors
    //---------------------------

    private HTTPServer http_server;


    public OpenHomeRenderer(Artisan artisan)
    {
        super(artisan);
    }


    public OpenHomeRenderer(Artisan artisan,SSDPSearchDevice ssdp_device)
    {
        super(artisan,ssdp_device);
        Utils.log(dbg_ohr,1,"new OpenHomeRenderer(" + ssdp_device.getFriendlyName() + "," + ssdp_device.getDeviceType() + "," + ssdp_device.getDeviceUrl());
    }


    //------------------------------------------------------
    // startRenderer() and stopRenderer()
    //------------------------------------------------------

    private void unsubscribeAll()
    {
        for (Subscription subscription : subscriptions.values())
        {
            subscription.unsubscribe();
        }
    }


    private boolean addSubscription(Service.serviceType service_type)
    {
        boolean ok;
        synchronized (http_server)
        {
            Subscription subscription = new Subscription(services.get(service_type));
            ok = subscription.subscribe();
            if (ok)
                subscriptions.put(service_type,subscription);
        }
        return ok;
    }


    @Override
    public boolean startRenderer()
    {
        Utils.log(dbg_ohr,0,"OpenHomeRenderer.startRenderer()");

        http_server = artisan.getHTTPServer();
        if (http_server == null)
        {
            Utils.error("OpenHomeRenderer requires http_server to be running");
            return false;
        }

        // Set ourself into the httpServer
        // Will need a getter to support start-before-stop

        http_server.setOpenHomeRenderer(this);

        // SUBSCRIBE to the services.
        // If we fail to subscribe to any,
        // we UNSUBSCRIBE from those already done.

        boolean ok = true;
        subscriptions = new subscriptionHash();
        ok = ok && addSubscription(Service.serviceType.OpenTime);
        ok = ok && addSubscription(Service.serviceType.OpenVolume);
        ok = ok && addSubscription(Service.serviceType.OpenInfo);
        ok = ok && addSubscription(Service.serviceType.OpenPlaylist);
        ok = ok && addSubscription(Service.serviceType.OpenProduct);

        // Wait for All Five Initial Events

        Utils.log(dbg_ohr,0,"Waiting for initial events");

        int count = 0;
        int RETRIES = 30;
        int WAIT_INTERVAL = 200;
        int total_event_count = 0;
        while (count++ < RETRIES &&
            total_event_count < 5)
        {
            total_event_count += getOpenTime().getEventCount() > 0 ? 1 : 0;
            total_event_count += getOpenVolume().getEventCount() > 0 ? 1 : 0;
            total_event_count += getOpenInfo().getEventCount() > 0 ? 1 : 0;
            total_event_count += getOpenPlaylist().getEventCount() > 0 ? 1 : 0;
            total_event_count += getOpenProduct().getEventCount() > 0 ? 1 : 0;
            if (total_event_count < 5)
            {
                if (count % 10 == 1)
                    Utils.log(0,0,"Waiting for initial events");
                Utils.sleep(WAIT_INTERVAL);
            }
        }
        if (total_event_count < 5)
        {
            Utils.warning(0,0,"Only got " + total_event_count + " of 5 event responses");
            ok = false;
        }

        // Finished

        if (ok)
        {
            // Attach our Playlist to the tempEditablePlaylist
        }
        else
        {
            // clear ourself rom the http_server
            // and wipe out any partial subscriptions
            http_server.setOpenHomeRenderer(null);
            unsubscribeAll();
        }

        Utils.log(dbg_ohr,0,"OpenHomeRenderer.startRenderer() returning " + ok);
        return ok;

    }   // startRenderer()


    @Override
    public void stopRenderer(boolean wait_for_stop)
        // stop the http server from sending us more stuff
        // stop the tempEditablePlaylist from accessing us, and
        // cancel any subscriptions
    {
        Utils.log(dbg_ohr,0,"OpenHomeRenderer.stopRenderer(" + wait_for_stop + ")");
        if (http_server != null)
            http_server.setOpenHomeRenderer(null);
        unsubscribeAll();
    }


    //-------------------------------------------
    // Renderer Interface
    //-------------------------------------------

    @Override
    public String getRendererName() { return getFriendlyName(); }

    @Override
    public Volume getVolume() { return (Volume) getOpenVolume(); }

    @Override
    public int getTotalTracksPlayed() { return getOpenTime().getTotalTracksPlayed(); }

    @Override
    public int getPosition() { return 1000 * getOpenTime().getElapsed(); }

    @Override
    public String getRendererState() { return getOpenPlaylist().getTransportState(); }

    @Override
    public String getRendererStatus() { return "OK"; }

    @Override
    public boolean getShuffle() { return getOpenPlaylist().getShuffle(); }

    @Override
    public boolean getRepeat() { return getOpenPlaylist().getRepeat(); }

    @Override
    public void setRepeat(boolean value) { getOpenPlaylist().setRepeat(value); }

    @Override
    public void setShuffle(boolean value) { getOpenPlaylist().setShuffle(value); }

    @Override
    public Track getRendererTrack() { return getOpenInfo().getTrack(); }

    @Override
    public int getRendererTrackNum() { return getOpenPlaylist().getCurrentIndex(); }

    @Override
    public int getRendererNumTracks() { return getOpenPlaylist().getNumTracks(); }


    // actions

    @Override
    public void transport_pause()
    {
        doAction(Service.serviceType.OpenPlaylist,"Pause",null);
    }

    @Override
    public void transport_play()
    {
        doAction(Service.serviceType.OpenPlaylist,"Play",null);
    }

    @Override
    public void transport_stop()
    {
        doAction(Service.serviceType.OpenPlaylist,"Stop",null);
    }

    @Override
    public void incAndPlay(int offset)
    {
        if (offset > 0)
            doAction(Service.serviceType.OpenPlaylist,"Next",null);
        else if (offset < 0)
            doAction(Service.serviceType.OpenPlaylist,"Previous",null);
        else
            doAction(Service.serviceType.OpenPlaylist,"Play",null);
    }

    @Override
    public void seekTo(int progress)
    {
        stringHash args = new stringHash();
        args.put("Value",Integer.toString(progress / 1000));
        doAction(Service.serviceType.OpenPlaylist,"SeekSecondAbsolute",args);
    }




    @Override
    public void setRendererTrack(Track track,boolean interrupt_playlist)
        // not sure this CAN be implemented
    {
    }


    //-------------------------------------------
    // HttpRequestHandler Interface
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
        // synchronized (http_server)
        {
            boolean is_loop_action = service.equals("OpenTime");
            String dbg_from = "OpenHomeRenderer Callback(" + service + ")";
            if (!is_loop_action)
                Utils.log(0,1,dbg_from);

            Service.serviceType service_type = null;
            try
            {
                service_type = Service.serviceType.valueOf(service);
            }
            catch (Exception e)
            {
                Utils.warning(0,0,"Bad Service Type: " + service);
            }

            UpnpEventReceiver receiver = (UpnpEventReceiver) services.get(service_type);
            if (receiver != null)
            {
                String result = receiver.response(session,response,service,doc);
                if (result.isEmpty())
                {
                    response = http_server.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK,
                        NanoHTTPD.MIME_PLAINTEXT,"OK");
                }
                else
                {
                    String msg = "ERROR - Bad Request to Service(" + service + ")\n" +
                        "Device: " + ((Service) receiver).getDevice().getDeviceUUID() + "\n" +
                        "Name:   " + ((Service) receiver).getDevice().getFriendlyName() + "\n" +
                        "Reason: " + result;

                    response = http_server.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.INTERNAL_ERROR,
                        NanoHTTPD.MIME_PLAINTEXT,msg);
                }
            }
        }
        return response;
    }



}// Class device.OpenHomeRenderer
