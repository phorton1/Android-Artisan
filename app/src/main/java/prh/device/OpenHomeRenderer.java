package prh.device;

import prh.artisan.Artisan;
import prh.utils.Utils;

public class OpenHomeRenderer extends Device
{

    public OpenHomeRenderer(Artisan artisan, String friendlyName, String device_type, String device_url)
    {
        super(artisan,friendlyName,device_type,device_url);
        Utils.log(0,0,"new OpenHomeRenderer(" + friendlyName + "," + device_type + "," + device_url);
    }


}
