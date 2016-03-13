package prh.device.service;


import org.w3c.dom.Document;

import prh.artisan.Artisan;
import prh.device.Device;
import prh.device.SSDPSearch;
import prh.device.Service;

public class OpenVolume extends Service
{
    public OpenVolume(
        Artisan artisan,
        Device device,
        String urn,
        String service_type,
        String control_url,
        String event_url,
        Document service_description )
    {
        super(artisan,device,urn,service_type,control_url,event_url);
    }
}

