package prh.device.service;


import org.w3c.dom.Document;

import prh.artisan.Artisan;
import prh.artisan.Volume;
import prh.device.Device;
import prh.device.SSDPSearch;
import prh.device.SSDPSearchService;
import prh.device.Service;

public class OpenVolume extends Service implements Volume
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

}

