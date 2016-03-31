package prh.device.service;


import org.w3c.dom.Document;
import org.w3c.dom.Element;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.artisan.EventHandler;
import prh.device.Device;
import prh.device.SSDPSearch;
import prh.device.SSDPSearchService;
import prh.device.Service;
import prh.server.utils.UpnpEventReceiver;
import prh.utils.Utils;

public class OpenTime extends Service implements
    UpnpEventReceiver
{
    // state and accessors

    public int getElapsed()
    {
        return last_position;
    }

    public int getTotalTracksPlayed()
    {
        return total_tracks_played;
    }


    // Constructors

    public OpenTime(
        Artisan artisan,
        Device device)
    {
        super(artisan,device);
    }

    public OpenTime(
        Artisan artisan,
        Device device,
        SSDPSearchService ssdp_service )
    {
        super(artisan,device,ssdp_service);
    }


    //-------------------------------------
    // UpnpEventReceiver Interface
    //-------------------------------------

    private int event_count = 0;
    private int last_position = 0;
    private int total_tracks_played = 0;


    @Override public int getEventCount()
    {
        return event_count;
    }

    public String response(
        NanoHTTPD.IHTTPSession session,
        NanoHTTPD.Response response,
        String service,
        Document doc)
    {
        event_count++;
        Element doc_ele = doc.getDocumentElement();
        int new_position = Utils.parseInt(Utils.getTagValue(doc_ele,"Seconds"));
        total_tracks_played = Utils.parseInt(Utils.getTagValue(doc_ele,"TrackCount"));
        if (last_position != new_position)
        {
            last_position = new_position;
            artisan.handleArtisanEvent(EventHandler.EVENT_POSITION_CHANGED,last_position * 1000);
        }
        return "";
    }


}   // class device.service.OpenTime
