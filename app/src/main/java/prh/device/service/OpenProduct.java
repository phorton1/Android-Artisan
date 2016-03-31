package prh.device.service;


import org.w3c.dom.Document;
import org.w3c.dom.Element;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.device.Device;
import prh.device.SSDPSearch;
import prh.device.SSDPSearchService;
import prh.device.Service;
import prh.server.utils.UpnpEventReceiver;

public class OpenProduct extends Service implements
    UpnpEventReceiver
{
    public OpenProduct(
        Artisan artisan,
        Device device,
        SSDPSearchService ssdp_service )
    {
        super(artisan,device,ssdp_service);
    }

    public OpenProduct(
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

}   // class device.service.OpenProduct
