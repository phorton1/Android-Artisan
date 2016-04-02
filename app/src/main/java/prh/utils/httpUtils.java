package prh.utils;


import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Prefs;
import prh.server.HTTPServer;


public class httpUtils
    // Utilities common the DLNA Renderer and Server for
    // XML / Soap / Didl
{
    private static int dbg_dlna_utils = 1;

    // I'm not sure if these are really urns,
    // I think they're schema names ...

    public static String upnp_urn = "schemas-upnp-org";
        // for AVTransport, RenderingControl, and ContentDirectory
    public static String open_service_urn = "av-openhome-org";
        // for Open Home Services
    public static String open_device_urn = "linn-co-uk";
        // for Open Home "Source" device


    public static String commonDeviceDescription(String extra)
    {
        return
            "<presentationURL>" + Utils.deviceWebUrl + "</presentationURL>\r\n" +
            "<friendlyName>" +  Prefs.friendlyName() + extra + "</friendlyName>\r\n" +
            "<manufacturer>" + Utils.manufacturerName + "</manufacturer>\r\n" +
            "<manufacturerURL>" + Utils.manufacturerUrl + "</manufacturerURL>\r\n" +
            "<modelDescription>" + Utils.modelInfo + "</modelDescription>\r\n" +
            "<modelName>" +  Utils.programName + "</modelName>\r\n" +
            "<modelURL>" + Utils.modelUrl + "</modelURL>\r\n" +
            "<modelNumber>" + Utils.modelNumber + "</modelNumber>\r\n" +
            "<serialNumber>" + Utils.serial_number + "</serialNumber>\r\n";
    }


    //-------------------------
    // encoding
    //-------------------------

    public static String encode_xml(String in)
        // encodes a full piece of didl for inclusion in xml
    {
        if (in==null) in = "";
        String out = in;
        out = out.replaceAll("&","&amp;amp;");
        out = out.replaceAll("\"","&quot;");
        out = out.replaceAll("<","&lt;");
        out = out.replaceAll(">","&gt;");
        return out;
    }

    public static String decode_xml(String in)
        // encodes a full piece of didl for inclusion in xml
        // should be called encode_didl
    {
        if (in==null) in = "";
        String out = in;
        out = out.replaceAll("&quot;","\"");
        out = out.replaceAll("&lt;","<");
        out = out.replaceAll("&gt;",">");
        out = out.replaceAll("&amp;amp;","&");
        return out;
    }


    public static String encode_value(String value)
        // url encodes a single value in the didl
    {
        //value = value.replaceAll("&","&amp;amp;");
        Pattern pattern = Pattern.compile("[^\\x20-\\x7f]");
        StringBuffer output = new StringBuffer();
        Matcher matcher = pattern.matcher(value);
        while (matcher.find())
        {
            String rep = matcher.group();
            rep = "&#" + ((int) rep.charAt(0)) + ";";
            matcher.appendReplacement(output, rep);
        }
        matcher.appendTail(output);
        return output.toString();
    }


    public static String decode_value(String value)
        // url decode a single value in the didl
    {
        Pattern pattern = Pattern.compile("&#(\\d+);");
        StringBuffer output = new StringBuffer();
        Matcher matcher = pattern.matcher(value);
        while (matcher.find())
        {
            String rep = matcher.group();
            char chr = (char) Utils.parseInt((rep));
            rep = "" + chr;
            matcher.appendReplacement(output, rep);
        }
        matcher.appendTail(output);
        value = output.toString();
        // value = value.replaceAll("&amp;amp;","&");
        return value;
    }


    //----------------------------------------------------------
    // response building
    //----------------------------------------------------------

    public static NanoHTTPD.Response ok_response(
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



    public static NanoHTTPD.Response raw_file_response(
        HTTPServer server,
        NanoHTTPD.Response response,
        String full_path)
        // return a file from the file system
    {
        Utils.log(dbg_dlna_utils,0,"raw_file_response(" + full_path + ")");
        String mime_type =
            full_path.matches(".*\\.jpg$") ? "image/jpeg" :
            full_path.matches(".*\\.m3u$") ? "text/plain" : // doesn't work:  "application/vnd.apple.mpegurl" :
            full_path.matches(".*\\.xspf$") ? "text/plain":  // works: application/xspf+xml" :
            "";

        if (mime_type.equals(""))
        {
            Utils.warning(0,0,"no mime type for " + full_path);
        }
        File ifile = new File(full_path);
        if (ifile.canRead())
        {
            try
            {
                InputStream stream = new FileInputStream(ifile);
                response = server.newChunkedResponse(NanoHTTPD.Response.Status.OK, mime_type, stream);
            }
            catch (Exception e)
            {
                Utils.error("could not open raw file=" + full_path + " exception=" + e);
            }
        }
        else
        {
            Utils.error("could not read raw file=" + full_path);
        }
        return response;
    }



    public static String start_didl()
    {
        return "<DIDL-Lite " +
            "xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:dlna=\"urn:schemas-dlna-org:metadata-1-0/\" " +
            "xmlns:sec=\"http://www.sec.co.kr/\" " + ">";
    }

    public static String end_didl()
    {
        return "</DIDL-Lite>";
    }


    public static String action_response_header(String urn, String service, String action)
    {
        String xml = "<?xml version=\"1.0\"?>" +
            "<s:Envelope " +
            "xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body>" +
            "<u:" + action + "Response xmlns:u=\"urn:" + urn + ":service:" + service + ":1\">";

        // open home does not have a wrapper tag
        // each command is different
        // dlna wraps it in <Result>

        if (!urn.equals(open_service_urn))
            xml += "<Result>";

        return xml;
    }


    public static String action_response_footer(String urn, String action, String extra)
    {
        String xml = "";
        if (!urn.equals(open_service_urn))
            xml += "</Result>";
        xml += extra +
            "</u:" + action + "Response>"+
            "</s:Body>"+
            "</s:Envelope>";
        return xml;
    }

    public static boolean dbg_hash_response = false;

    public static NanoHTTPD.Response hash_response(
        HTTPServer server,
        String urn,
        String service,
        String action,
        HashMap<String,String> hash)
    {
        String xml = action_response_header(urn,service,action);
        xml += hashToXMLString(hash,false);
        xml +=  action_response_footer(urn,action,"");
        if (dbg_hash_response)
            Utils.log(0,0,service + " " + action + "Response\n" + xml);
        return server.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,"text/xml", xml);
    }


    // public static NanoHTTPD.Response hash_with_metadata_response(
    //     HTTPServer server,
    //     String urn,
    //     String service,
    //     String action,
    //     HashMap hash,
    //     Renderer renderer)
    // {
    //     String xml = action_response_header(urn,service,action);
    //     xml = xml + hashToXMLString(hash,false);
    //     xml = xml + "<TrackMetaData>";
    //     xml = xml + start_didl();
    //     xml = xml + encode_xml(renderer.getgetDLNAMetadata());
    //     xml = xml + end_didl();
    //     xml = xml + "</TrackMetaData>";
    //     xml = xml + action_response_footer(urn,action,"");
    //     return server.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,"text/xml", xml);
    // }


    public static String hashToXMLString(HashMap<String,String> hash,boolean property_set)
    // property_set adds <e:property>..</e:property> around the elements
    {
        String rslt = "";
        for (String key : hash.keySet())
        {
            String part = "<" + key + ">" + hash.get(key) + "</" + key + ">";
            if (property_set)
                part = "<e:property>" + part + "</e:property>";
            rslt += part;
        }
        return rslt;
    }



    //---------------------------------------------------------
    // xml parsing
    //---------------------------------------------------------

    public static Document get_xml_from_post(NanoHTTPD.IHTTPSession session)
    // after the fact get POST body from session stream
    // and parse it into xml
    {
        InputStream input_stream = session.getInputStream();
        Integer content_length = Utils.parseInt(session.getHeaders().get("content-length"));

        int count = 0;
        int RETRIES = 20;
        int INTERVAL = 50;

        int offset = 0;
        int remaining = content_length;
        byte[] buf = new byte[content_length];

        while (count++<RETRIES && remaining > 0) try
        {
            int got = session.getInputStream().read(buf,offset,remaining);
            if (got < remaining)
            {
                Utils.log(2,0,"waiting for xml");
                Utils.sleep(INTERVAL);
            }
            offset += got;
            remaining -= got;
        }
        catch (Exception e)
        {
            Utils.warning(0,0,"Error reading xml: content_len=" + content_length + " offset=" + offset + " remaining=" + remaining + " exception:"+e);
            offset = 0;
        }

        Document doc = null;
        if (offset == content_length)   try
        {
            ByteArrayInputStream buf_stream = new ByteArrayInputStream(buf);
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(buf_stream);
            if (doc == null)
                Utils.error("document build returned a null document");
        }
        catch (Exception e)
        {
            Utils.error("Exception building xml document:" + e);
            // String test = new String(buf);
            // Utils.log(0,0,"buf=" + test);
        }
        return doc;
    }



    public static String getXMLString(Document doc, String field, boolean with_error)
    {
        String retval = "";
        if (doc == null)
        {
            if (with_error)
            {
                Utils.error("null doc in getXMLString(" + field + ")");
            }
            return "";
        }
        NodeList list = doc.getElementsByTagName(field);
        if (list.getLength() == 0)
        {
            if (with_error)
            {
                Utils.error("could not find field " + field + " in get_xml_params()");
            }
        }
        else
        {
            Node item = list.item(0);
            retval = item.getTextContent();
            // might need to get the content of the node
            // for SOAPENV namespace
        }
        return retval;
    }



    public static int getXMLInt(Document doc, String field, boolean with_error)
    {
        String s = getXMLString(doc,field,with_error);
        if (s==null || s.equals("")) return 0;
        return Utils.parseInt(s);
    }


    //-------------------------------------------------
    // dlna stuff
    //-------------------------------------------------

    public static String get_dlna_stuff(String type)
    {
        String contentfeatures = "";

        if (type.equals("mp4")) contentfeatures += "DLNA.ORG_PN=LPCM;";
        if (type.equals("wav")) contentfeatures += "DLNA.ORG_PN=LPCM;";
        if (type.equals("wma")) contentfeatures += "DLNA.ORG_PN=WMABASE;";
        if (type.equals("mp3")) contentfeatures += "DLNA.ORG_PN=MP3;";
        contentfeatures += "DLNA.ORG_OP=01;";
        contentfeatures += "DLNA.ORG_CI=0;";
        contentfeatures += "DLNA.ORG_FLAGS=01500000000000000000000000000000";
        return contentfeatures;
    }




}   // class httpUtils
