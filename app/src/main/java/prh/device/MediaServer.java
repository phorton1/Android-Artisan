package prh.device;

import prh.artisan.Artisan;
import prh.utils.Utils;

public class MediaServer extends Device
{

    public MediaServer(Artisan artisan, SSDPSearch.SSDPDevice ssdp_device)
    {
        super(artisan,ssdp_device);
        Utils.log(0,0,"new MediaServer(" + ssdp_device.getFriendlyName() + "," + ssdp_device.getDeviceType() + "," + ssdp_device.getDeviceUrl());
    }

    public MediaServer(Artisan artisan)
    {
        super(artisan);
    }
}
