package prh.device;

import prh.artisan.Artisan;
import prh.utils.Utils;

public class MediaServer extends Device
{

    public MediaServer(Artisan artisan, String friendlyName, String device_type, String device_url, String icon_url)
    {
        super(artisan,friendlyName,device_type,device_url,icon_url);
        Utils.log(0,0,"new MediaServer(" + friendlyName + "," + device_type + "," + device_url);
    }

    public MediaServer(Artisan artisan)
    {
        super(artisan);
    }
}
