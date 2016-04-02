// DLNA Renderer

package prh.server.http;


import org.w3c.dom.Document;

import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.artisan.interfaces.Renderer;
import prh.artisan.interfaces.Volume;
import prh.server.HTTPServer;
import prh.server.utils.UpnpEventSubscriber;
import prh.server.utils.httpRequestHandler;
import prh.utils.httpUtils;
import prh.utils.Utils;


public class RenderingControl implements httpRequestHandler
{
    private static int dbg_rc = 0;
    private static String default_channel = "MASTER";

    private Artisan artisan;
    private HTTPServer http_server;
    String urn;

    public RenderingControl(Artisan ma, HTTPServer http, String the_urn)
    {
        artisan = ma;
        http_server = http;
        urn = the_urn;
    }

    //-----------------------------------------------------------
    // control_response (Actions)
    //-----------------------------------------------------------

    @Override
    public NanoHTTPD.Response response(
        NanoHTTPD.IHTTPSession unused_session,
        NanoHTTPD.Response response,
        String uri,
        String service,
        String action,
        Document doc ,
        UpnpEventSubscriber unused_subscriber)
    {
        // Only handles actions, expects doc != null, and never looks at uri
        // All actions get, and ignore, an InstanceID parameter
        // All actions get, and ignore a channel

        if (!uri.equals("control"))
            return response;

        Renderer renderer = artisan.getRenderer();
        Volume volume = renderer.getVolume();
        int values[] = volume == null ? new int[]{0,0,0,0,0,0,0,0} : volume.getValues();

        if (action.equals("SetVolume"))
        {
            response = SetRendererResponse(http_server,response,doc,urn,service,action,"DesiredVolume",Volume.CTRL_VOL);
        }
        else if (action.equals("GetVolume"))
        {
            response = GetRendererResponse(http_server,response,values,urn,service,action,"CurrentVolume",Volume.CTRL_VOL);
        }

        // mute and loudness

        else if (action.equals("SetMute"))
        {
            response = SetRendererResponse(http_server,response,doc,urn,service,action,"DesiredMute",Volume.CTRL_MUTE);
        }
        else if (action.equals("GetMute"))
        {
            response = GetRendererResponse(http_server,response,values,urn,service,action,"CurrentMute",Volume.CTRL_MUTE);
        }
        else if (action.equals("SetLoudness"))
        {
            response = SetRendererResponse(http_server,response,doc,urn,service,action,"DesiredLoudness",Volume.CTRL_LOUD);
        }
        else if (action.equals("GetLoudness"))
        {
            response = GetRendererResponse(http_server,response,values,urn,service,action,"CurrentLoudness",Volume.CTRL_LOUD);
        }

        // balance and fader

        else if (action.equals("SetBalance"))
        {
            response = SetRendererResponse(http_server,response,doc,urn,service,action,"DesiredBalance",Volume.CTRL_BAL);
        }
        else if (action.equals("GetBalance"))
        {
            response = GetRendererResponse(http_server,response,values,urn,service,action,"CurrentBalance",Volume.CTRL_BAL);
        }
        else if (action.equals("SetFade"))
        {
            response = SetRendererResponse(http_server,response,doc,urn,service,action,"DesiredFade",Volume.CTRL_FADE);
        }
        else if (action.equals("GetFade"))
        {
            response = GetRendererResponse(http_server,response,values,urn,service,action,"CurrentFade",Volume.CTRL_FADE);
        }

        // bass, mid, and high EQ

        else if (action.equals("SetEQLow"))
        {
            response = SetRendererResponse(http_server,response,doc,urn,service,action,"DesiredEQLow",Volume.CTRL_BASS);
        }
        else if (action.equals("GetEQLow"))
        {
            response = GetRendererResponse(http_server,response,values,urn,service,action,"CurrentEQLow",Volume.CTRL_BASS);
        }
        else if (action.equals("SetEQMid"))
        {
            response = SetRendererResponse(http_server,response,doc,urn,service,action,"DesiredEQMid",Volume.CTRL_MID);
        }
        else if (action.equals("GetEQMid"))
        {
            response = GetRendererResponse(http_server,response,values,urn,service,action,"CurrentEQMid",Volume.CTRL_MID);
        }
        else if (action.equals("SetEQHigh"))
        {
            response = SetRendererResponse(http_server,response,doc,urn,service,action,"DesiredEQHigh",Volume.CTRL_HIGH);
        }
        else if (action.equals("GetEQHigh"))
        {
            response = GetRendererResponse(http_server,response,values,urn,service,action,"CurrentEQHigh",Volume.CTRL_HIGH);
        }
        else
        {
            // We currently do not support ListPresets, SelectPreset
            // but maybe that can be shoehorned into selectPlaylist
            Utils.error("unknown/unsupported action(" + action + ") in RendererControl request");
        }
        return response;
    }



    //-------------------------------------------------------------
    // atomic responses
    //-------------------------------------------------------------
    // 402 invalid parameter
    // 501 action failed

    private NanoHTTPD.Response ok_response(
            HTTPServer server,
            String urn,
            String service,
            String action)
            // The default OK response is just an empty SSDP response (with soap body)
    {
        String xml = httpUtils.action_response_header(urn,service,action);
        xml = xml + httpUtils.action_response_footer(urn,action,"");
        return server.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,"text/xml", xml);
    }


    private NanoHTTPD.Response SetRendererResponse(
        HTTPServer server,
        NanoHTTPD.Response response,
        Document doc,
        String urn,
        String service,
        String action,
        String field,
        int ctrl_idx)
    {
        int val = httpUtils.getXMLInt(doc,field,true);
        Utils.log(dbg_rc,0,"SetRendererResponse(" + field + ")=" + val);

        Renderer renderer = artisan.getRenderer();
        Volume volume = renderer.getVolume();
        int max_values[] = volume == null ? new int[]{0,0,0,0,0,0,0,0} : volume.getMaxValues();
        if (val < 0 || val > max_values[ctrl_idx])
        {
            Utils.error("RenderingControl " + action + "(" + val + ") - value out of range");
        }
        else
        {
            volume.setValue(ctrl_idx,val);
            response = ok_response(server,urn,service,action);
        }
        return response;
    }


    private NanoHTTPD.Response GetRendererResponse(
        HTTPServer server,
        NanoHTTPD.Response response,
        int values[],
        String urn,
        String service,
        String action,
        String field,
        int ctrl_idx)
    {
        int val = values[ctrl_idx];
        Utils.log(dbg_rc+1,1,"returning " + field + "=" + val);
        HashMap<String,String> hash = new HashMap<String,String>();
        hash.put(field,Integer.toString(val));
        hash.put("Channel","MASTER");
        return httpUtils.hash_response(server,urn,service,action,hash);
    }


}   // class RenderingControl
