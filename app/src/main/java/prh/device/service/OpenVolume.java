package prh.device.service;


import org.w3c.dom.Document;
import org.w3c.dom.Element;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.artisan.interfaces.Volume;
import prh.device.Device;
import prh.device.SSDPSearchService;
import prh.device.Service;
import prh.server.utils.UpnpEventReceiver;

public class OpenVolume extends Service implements
    Volume,
    UpnpEventReceiver
{

    public OpenVolume(
        Artisan artisan,
        Device device,
        SSDPSearchService ssdp_service )
    {
        super(artisan,device,ssdp_service);
    }

    public OpenVolume(
        Artisan artisan,
        Device device)
    {
        super(artisan,device);
    }


    //-----------------------------------------
    // Volume Interface
    //-----------------------------------------

    @Override public void start() {}
    @Override public void stop() {}


    @Override public int[] getMaxValues()
    {
        return new int[0];
    }

    @Override public int[] getValues()
    {
        return new int[0];
    }

    @Override public int[] getUpdateValues()
    {
        return new int[0];
    }

    @Override public void setValue(int idx, int value)
    {
    }

    @Override public void incDecValue(int idx, int inc)
    {
    }

    //-------------------------------------
    // UpnpEventReceiver Interface
    //-------------------------------------
    private int event_count = 0;
    Element doc_ele = null;

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
        doc_ele = doc.getDocumentElement();
        return "";
    }


}   // class device.service.OpenVolume

