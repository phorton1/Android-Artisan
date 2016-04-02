package prh.device.service;


import org.w3c.dom.Document;
import org.w3c.dom.Element;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.base.ArtisanEventHandler;
import prh.artisan.Track;
import prh.device.Device;
import prh.device.SSDPSearchService;
import prh.device.Service;
import prh.base.UpnpEventReceiver;
import prh.utils.Utils;

public class OpenInfo extends Service implements
    UpnpEventReceiver
{
    public OpenInfo(
        Artisan artisan,
        Device device,
        SSDPSearchService ssdp_service )
    {
        super(artisan,device,ssdp_service);
    }

    public OpenInfo(
        Artisan artisan,
        Device device)
    {
        super(artisan,device);
    }



    //-------------------------------------
    // UpnpEventReceiver Interface
    //-------------------------------------

    private int event_count = 0;
    Element doc_ele = null;
    String last_track_uri = "";
    Track last_track = null;


    @Override public int getEventCount()
    {
        return event_count;
    }

    public Track getTrack()
    {
        return last_track;
    }



    public synchronized String response(
        NanoHTTPD.IHTTPSession session,
        NanoHTTPD.Response response,
        String service,
        Document doc)
    {
        event_count++;
        doc_ele = doc.getDocumentElement();

        // not parsing Duration, CodecName,
        // DetailsCount, MetatextCount, BitRate,
        // BitDepth, SampleRate, Lossless, or MetaText

        String new_uri = Utils.getTagValue(doc_ele,"Uri");
        if (!new_uri.equals(last_track_uri))
        {
            last_track = null;
            if (!new_uri.isEmpty())
            {
                String didl = Utils.getTagValue(doc_ele,"Metadata");
                last_track = new Track(new_uri,didl);
            }
            last_track_uri = new_uri;
            artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_TRACK_CHANGED,last_track);
        }

        return "";
    }


}   // class device.service.OpenInfo
