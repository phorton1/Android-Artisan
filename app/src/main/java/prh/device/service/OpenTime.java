package prh.device.service;


import org.w3c.dom.Document;

import prh.artisan.Artisan;
import prh.device.Device;
import prh.device.SSDPSearch;
import prh.device.Service;

public class OpenTime extends Service
{
    public OpenTime(
        Artisan artisan,
        Device device,
        SSDPSearch.SSDPService ssdp_service )
    {
        super(artisan,device,ssdp_service);
    }


    public OpenTime(
        Artisan artisan,
        Device device)
    {
        super(artisan,device);
    }

}
