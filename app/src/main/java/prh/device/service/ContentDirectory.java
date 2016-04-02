package prh.device.service;


import org.w3c.dom.Document;

import prh.artisan.Artisan;
import prh.device.Device;
import prh.device.SSDPSearch;
import prh.device.SSDPSearchService;
import prh.device.Service;

public class ContentDirectory extends Service
{
    public ContentDirectory(
        Artisan artisan,
        Device device,
        SSDPSearchService ssdp_service )
    {
        super(artisan,device,ssdp_service);
    }


    public ContentDirectory(
        Artisan artisan,
        Device device)
    {
        super(artisan,device);
    }

}
