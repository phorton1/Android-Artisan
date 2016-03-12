package prh.device.service;


import org.w3c.dom.Document;

import prh.artisan.Artisan;
import prh.device.Device;
import prh.device.SSDPSearch;
import prh.device.Service;

public class RenderingControl extends Service
{
    public RenderingControl(
        Artisan artisan,
        Device device,
        String service_type,
        String control_url,
        String event_url,
        Document service_description )
    {
        super(artisan,device,service_type,control_url,event_url);
    }
}
