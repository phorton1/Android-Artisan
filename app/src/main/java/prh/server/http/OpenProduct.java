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
import prh.server.utils.UpnpEventSubscriber;
import prh.server.utils.httpRequestHandler;
import prh.server.utils.UpdateCounter;
import prh.server.utils.UpnpEventHandler;
import prh.utils.httpUtils;
import prh.utils.Utils;


public class OpenProduct extends httpRequestHandler implements UpnpEventHandler
{
    private static int dbg_product = 0;
    private static String source_name = "Playlist";
    private static String source_type = "Playlist";

    Artisan artisan;
    HTTPServer http_server;
    String urn;

    public OpenProduct(Artisan ma, HTTPServer http, String the_urn)
    {
        artisan = ma;
        http_server = http;
        urn = the_urn;
    }


    public void start()
    {
        http_server.getEventManager().RegisterHandler(this);
    }

    public void stop()
    {
        http_server.getEventManager().UnRegisterHandler(this);
    }

    public void notifySubscribed(UpnpEventSubscriber subscriber,boolean subscribe)
    {}



    public NanoHTTPD.Response response(
        NanoHTTPD.IHTTPSession session,
        NanoHTTPD.Response response,
        String unused_uri,
        String service,
        String action,
        Document doc,
        UpnpEventSubscriber unused_subscriber)
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
            int value = httpUtils.getXMLInt(doc,"Value",true);
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
            int value = httpUtils.getXMLInt(doc,"Index",true);
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
            int value = httpUtils.getXMLInt(doc,"Value",true);
        }
        else
        {
            ok = false;
        }

        if (ok)
            response = httpUtils.hash_response(http_server,urn,service,action,hash);
        return response;
    }



    //---------------------------------------
    // common to actions and events
    //---------------------------------------

    String sourceXML()
    {
        return httpUtils.encode_xml(
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
    public String getName() { return "Product"; };


    public String getEventContent(UpnpEventSubscriber unused_subscriber)
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

        return httpUtils.hashToXMLString(hash,true);
    }



}   // class OpenProduct
