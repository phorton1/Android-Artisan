//----------------------------------------------
// OpenHome Volume Actions
//----------------------------------------------

package prh.server.http;

import org.w3c.dom.Document;

import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.artisan.Renderer;
import prh.artisan.Volume;
import prh.server.HTTPServer;
import prh.utils.DlnaUtils;


public class OpenVolume implements OpenEventHandler
{
    static private int dbg_volume = 0;

    private Artisan artisan;
    private HTTPServer http_server;

    public OpenVolume(Artisan ma, HTTPServer http)
    {
        artisan = ma;
        http_server = http;
    }


    public NanoHTTPD.Response volumeAction(
        NanoHTTPD.Response response,
        Document doc,
        String urn,
        String service,
        String action)
    {
        HashMap<String,String> hash = new HashMap<String,String>();
        Renderer renderer = artisan.getRenderer();
        Volume volume = renderer.getVolume();
        int values[] = volume == null ? new int[]{0,0,0,0,0,0,0,0} : volume.getValues();
        int max_values[] = volume == null ? new int[]{0,0,0,0,0,0,0,0} : volume.getMaxValues();

        boolean ok = true;
        boolean changed = false;

        if (action.equals("Characteristics"))
        {
            int max_vol = max_values[Volume.CTRL_VOL];
            int max_bal = max_values[Volume.CTRL_BAL] / 2;
            int max_fade = max_values[Volume.CTRL_FADE] / 2;
            hash.put("VolumeMax",Integer.toString(max_vol));
            hash.put("VolumeUnity",Integer.toString(max_vol));
            hash.put("VolumeSteps",Integer.toString(max_vol));
            hash.put("VolumeMilliDbPerStep",Integer.toString(0));
            hash.put("BalanceMax",Integer.toString(max_bal));
            hash.put("FadeMax",Integer.toString(max_fade));
        }
        else if (action.equals("Volume"))
            hash.put("Value",Integer.toString(values[Volume.CTRL_VOL]));
        else if (action.equals("Balance"))
            hash.put("Value",Integer.toString(values[Volume.CTRL_BAL]));
        else if (action.equals("Fade"))
            hash.put("Value",Integer.toString(values[Volume.CTRL_FADE]));

        else if (action.equals("SetVolume"))
        {
            int value = DlnaUtils.getXMLInt(doc,"Value",true);
            volume.setValue(Volume.CTRL_VOL,value);
            changed = true;
        }
        else if (action.equals("SetBalance"))
        {
            int value = DlnaUtils.getXMLInt(doc,"Value",true);
            volume.setValue(Volume.CTRL_BAL,value);
            changed = true;
        }
        else if (action.equals("SetFade"))
        {
            int value = DlnaUtils.getXMLInt(doc,"Value",true);
            volume.setValue(Volume.CTRL_FADE,value);
            changed = true;
        }

        else if (action.equals("VolumeDec"))
        {
            volume.incDec(Volume.CTRL_VOL,-1);
            changed = true;
        }
        else if (action.equals("BalanceDec"))
        {
            volume.incDec(Volume.CTRL_BAL,-1);
            changed = true;
        }
        else if (action.equals("FadeDec"))
        {
            volume.incDec(Volume.CTRL_FADE,-1);
            changed = true;
        }
        else if (action.equals("VolumeInc"))
        {
            volume.incDec(Volume.CTRL_VOL,1);
            changed = true;
        }
        else if (action.equals("BalanceInc"))
        {
            volume.incDec(Volume.CTRL_BAL,1);
            changed = true;
        }
        else if (action.equals("FadeInc"))
        {
            volume.incDec(Volume.CTRL_FADE,1);
            changed = true;
        }
        else
        {
            ok = false;
        }

        if (ok)
            response = DlnaUtils.hash_response(http_server,urn,service,action,hash);
        if (changed)
            incUpdateCount();
        return response;
    }

    //----------------------------------------
    // Event Dispatching
    //----------------------------------------

    UpdateCounter update_counter = new UpdateCounter();
    public int getUpdateCount()  { return update_counter.get_update_count(); }
    public int incUpdateCount()  { return update_counter.inc_update_count(); }
    public String eventHandlerName() { return "Volume"; };

    public String getEventContent()
        // maybe would be better called "getStateXML"
    {
        HashMap<String,String> hash = new HashMap<String,String>();
        Renderer renderer = artisan.getRenderer();
        Volume volume = renderer.getVolume();
        int values[] = volume == null ? new int[]{0,0,0,0,0,0,0,0} : volume.getValues();
        int max_values[] = volume == null ? new int[]{0,0,0,0,0,0,0,0} : volume.getMaxValues();

        int max_vol =  max_values[Volume.CTRL_VOL];
        int max_bal =  max_values[Volume.CTRL_BAL] / 2;
        int max_fade = max_values[Volume.CTRL_FADE] / 2;

        hash.put("Volume",Integer.toString(values[Volume.CTRL_VOL]));
        hash.put("Mute",Integer.toString(values[Volume.CTRL_MUTE]));
        hash.put("Fade",Integer.toString(values[Volume.CTRL_FADE]));
        hash.put("Balance",Integer.toString(values[Volume.CTRL_BAL]));

        hash.put("VolumeMax",Integer.toString(max_vol));
        hash.put("VolumeUnity",Integer.toString(max_vol));
        hash.put("VolumeSteps",Integer.toString(max_vol));
        hash.put("VolumeMilliDbPerStep",Integer.toString(0));
        hash.put("VolumeLimit",Integer.toString(max_vol));
        hash.put("BalanceMax",Integer.toString(max_bal));
        hash.put("FadeMax",Integer.toString(max_fade));

        return DlnaUtils.hashToXMLString(hash,true);
    }


}   // class OpenVolume