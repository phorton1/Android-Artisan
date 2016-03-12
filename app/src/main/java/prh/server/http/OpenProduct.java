//----------------------------------------------
// OpenHome Product Actions
//----------------------------------------------

package prh.server.http;

import org.w3c.dom.Document;

import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.artisan.Prefs;
import prh.server.HTTPServer;
import prh.utils.DlnaUtils;
import prh.utils.Utils;


public class OpenProduct implements OpenEventHandler
{
    private static int dbg_product = 0;
    private static String source_name = "Playlist";
    private static String source_type = "Playlist";

    Artisan artisan;
    HTTPServer http_server;

    public OpenProduct(Artisan ma, HTTPServer http)
    {
        artisan = ma;
        http_server = http;
    }



    public NanoHTTPD.Response productAction(
        NanoHTTPD.Response response,
        Document doc,
        String urn,
        String service,
        String action)
    {
        HashMap<String,String> hash = new HashMap<String,String>();

        // all are constants
        // none generate events

        boolean ok = true;

        if (action.equals("Attributes"))
        {
            hash.put("Attributes","Playlist Info Time Volume");
        }
        else if (action.equals("Model"))
        {
            hash.put("Name",Utils.programName);
            hash.put("Info",Utils.modelInfo);
            hash.put("Url",Utils.modelUrl);
            hash.put("ImageUri",Utils.deviceIconUrl);
        }
        else if (action.equals("Product"))
        {
            hash.put("Room",Prefs.getString(Prefs.id.DEVICE_ROOM));
            hash.put("Name",Prefs.friendlyName()  + " (OpenHome");
            hash.put("Info","OpenHome Renderer");
            hash.put("Url",Utils.deviceWebUrl);
            hash.put("ImageUri",Utils.deviceIconUrl);
        }
        else if (action.equals("Standby"))
        {
            hash.put("Value","0");
        }
        else if (action.equals("SetStandby"))
        {
            int value = DlnaUtils.getXMLInt(doc,"Value",true);
        }
        else if (action.equals("SourceXml"))
        {
            hash.put("Value", sourceXML());
        }
        else if (action.equals("SourceIndex"))
        {
            hash.put("Value", "0");
        }
        else if (action.equals("Source"))
        {
            int value = DlnaUtils.getXMLInt(doc,"Index",true);
            hash.put("SystemeName", source_name);
            hash.put("Type", source_type);
        }
        else if (action.equals("Manufacturer"))
        {
            hash.put("Name",Utils.manufacturerName);
            hash.put("Info","Writing it all, one program at a time");
            hash.put("Url",Utils.manufacturerUrl);
            hash.put("ImageUri",Utils.deviceIconUrl);
        }
        else if (action.equals("SourceXmlChangeCount"))
        {
            hash.put("Value", "0");
        }
        else if (action.equals("SourceCount"))
        {
            hash.put("Value","1");  // the 0-based index of our only Source
        }
        else if (action.equals("SetSourceIndexByName"))
        {
            int value = DlnaUtils.getXMLInt(doc,"Value",true);
        }
        else
        {
            ok = false;
        }

        if (ok)
            response = DlnaUtils.hash_response(http_server,urn,service,action,hash);
        return response;
    }



    //---------------------------------------
    // common to actions and events
    //---------------------------------------

    String sourceXML()
    {
        return DlnaUtils.encode_xml(
            "<SourceList>\n" +
                "<Source>\n" +
                "<Name>Playlist</Name>\n" +
                "<Type>Playlist</Type>\n" +
                "<Visible>true</Visible>\n" +
                "</Source>\n" +
                "</SourceList>\n");
    }

    //----------------------------------------
    // Event Dispatching
    //----------------------------------------

    UpdateCounter update_counter = new UpdateCounter();
    public int getUpdateCount()  { return update_counter.get_update_count(); }
    public int incUpdateCount()  { return update_counter.inc_update_count(); }
    public String eventHandlerName() { return "Product"; };


    public String getEventContent()
        // Return the XML state table for the UPnP EVENT
        // This is static in my current implementation.
        // I may add multiple Sources (Radio Stations) later.
    {
        HashMap<String,String> hash = new HashMap<String,String>();

        hash.put("Standby","0");
        hash.put("Attributes","Playlist Info Time Volume");

        hash.put("SourceIndex","0");
        hash.put("SourceName",source_name);
        hash.put("SourceType",source_type);
        hash.put("SourceVisible","true");
        hash.put("SourceXml",sourceXML());
        hash.put("SourceXmlChangeCount","0");
        hash.put("SourceCount","1");

        hash.put("ProductRoom",Prefs.getString(Prefs.id.DEVICE_ROOM));
        hash.put("ProductName",Prefs.friendlyName() + " (OpenHome)");
        hash.put("ProductUrl",Utils.modelUrl);
        hash.put("ProductImageUri",Utils.deviceIconUrl);
        hash.put("ProductInfo",Utils.modelInfo);

        hash.put("ModelName",Utils.programName);
        hash.put("ModelInfo",Utils.modelInfo);
        hash.put("ModelUrl",Utils.modelUrl);
        hash.put("ModelImageUri",Utils.deviceIconUrl);
        hash.put("ManufacturerName",Utils.manufacturerName);
        hash.put("ManufacturerUrl",Utils.manufacturerUrl);
        hash.put("ManufacturerImageUri",Utils.deviceIconUrl);
        hash.put("ManufacturerInfo","Writing it all, one program at a time");

        return DlnaUtils.hashToXMLString(hash,true);
    }



}   // class OpenProduct
