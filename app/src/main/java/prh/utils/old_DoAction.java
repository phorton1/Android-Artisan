package prh.utils;

import org.w3c.dom.Document;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilderFactory;

import prh.device.Service;

public class old_DoAction
    // I had so many problems with Device/Service doAction, that I decided to
    // move it into it's own class for clarity.
    //
    // This doAction is Synchronous.
    // It starts a thread for a networkRequest and waits for ready.
    // Returns the doc, or null.
    // Reports the error.
{
    static int dbg_da = 1;


    public static Document doAction(
        String url,
        String urn,
        String service,
        String action,
        stringHash args)
    // Initial implementation is synchronous
    // All parameters must be correct.
    // The urn passed in is the "longer" one (i.e. schemas-upnp-org)
    {
        // build the SOAP xml reply

        String xml = getSoapBody(urn,service,action,args);

        // create the thread and start the request

        stringHash headers = new stringHash();
        headers.put("soapaction","\"urn:" + urn + ":service:" + service + ":1#" + action + "\"");

        networkRequest request = new networkRequest("text/xml",null,null,url,headers,xml);
        Thread request_thread = new Thread(request);
        request_thread.start();
        request.wait_for_result();

        if (request.the_result == null)
            Utils.error("doAction(" + service + "," + action + ") failed: " + request.error_reason);

        return (Document) request.the_result;
    }


    private static String getSoapBody(String urn,String service,String action,stringHash args)
    {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n";
        xml += "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n";
        xml += "<s:Body>\r\n";
        xml += "<u:" + action + " xmlns:u=\"urn:" + urn + ":service:" + service + ":1\">\r\n";

        if (args != null)
        {
            for (String key : args.keySet())
            {
                String value = args.get(key);
                xml += "<" + key + ">" + value + "</" + key + ">\r\n";
            }
        }
        xml += "</u:" + action + ">\r\n";
        xml += "</s:Body>\r\n";
        xml += "</s:Envelope>\r\n";
        return xml;
    }


    //----------------------------------------------------------------------------
    // FUNKY WAY
    //----------------------------------------------------------------------------

    private static int TIME_OUT = 100;

    private static String readline(InputStream istream)
    {
        String retval = null;
        try
        {
            boolean done = false;
            while (!done)
            {
                int count = TIME_OUT;
                while (istream.available()<=0)
                {
                    Utils.sleep(200);
                    if (count-- == 0)
                    {
                        Utils.warning(0,0,"Timeout reading socket");
                        return  null;
                    }
                }
                int i = istream.read();
                if (i == -1 ||  i==10)
                    done = true;
                else if (i != 13)
                {
                    if (retval == null) retval = "";
                    retval += (char) i;
                }
            }
        }
        catch (Exception e)
        {
            Utils.warning(0,0,"Exception in readling: " + e);
            retval = null;
        }
        return retval;
    }



    public static Document doAction2(
        String url,
        String urn,
        String service,
        String action,
        stringHash args)
        // The FUNKY way
        // Threading handled by Device
    {
        String xml = getSoapBody(urn,service,action,args);

        // build the headers

        String ip = Utils.ipFromUrl(url);
        String port = Utils.portFromUrl(url);
        String path = url.replace("http://","");
        path = path.replaceAll("^.*?\\/","/");

        String text = "POST " + path + " HTTP/1.1\r\n";
        text += "HOST: " + ip + ":" + port + "\r\n";
        text += "Content-Type: text/xml; charset=\"utf-8\"\r\n";
        text += "Content-Length: " + xml.length() + "\r\n";
        text += "SOAPACTION: \"urn:" + urn + ":service:" + service + ":1#" + action + "\"\r\n";
        text += "\r\n";
        text += xml;

        // Send the request

        Socket sock = null;
        Document doc = null;
        InputStream i_stream = null;

        try
        {
            sock = new Socket(ip,Utils.parseInt(port));
            OutputStreamWriter osw = new OutputStreamWriter(sock.getOutputStream(),"UTF-8");
            osw.write(text);
            osw.flush();

            // Read the result

            String line;
            boolean ok = true;
            Utils.log(dbg_da,0,"GETTING REPLY to doAction(" + service + "," + action + ") to " + url);
            i_stream = new BufferedInputStream(sock.getInputStream());
            Utils.log(dbg_da,1,"GOT REPLY!!");

            line = readline(i_stream);
            if (line == null)
            {
                ok = false;
                Utils.warning(0,0,"No reply to doAction(" + service + "," + action + ") to " + url);
            }
            else
            {
                Utils.log(dbg_da,1,"first_line=" + line);
                if (!line.contains("200"))
                {
                    ok = false;
                    Utils.warning(0,0,"Bad reply to doAction(" + service + "," + action + ") to " + url);
                    Utils.warning(0,1,"first_line=" + line);
                }
            }

            // read the headers (and display them if !ok)

            int content_len = 0;
            //while ((line = reader.readLine()) != null && !line.isEmpty())
            while ((line = readline(i_stream) ) != null && !line.isEmpty())
            {
                Utils.log(!ok?0:dbg_da+1,2,"header_line=" + line);
                int pos = line.indexOf(":");
                if (pos > 1)
                {
                    String lval = line.substring(0,pos).toLowerCase();
                    String rval = pos < line.length() ? line.substring(pos+1) : "";
                    rval = rval.replaceFirst("^\\s*","");
                    if (lval.equals("content-length"))
                    {
                        content_len = Utils.parseInt(rval);
                    }
                }
            }

            ByteArrayInputStream bas = null;
            if (content_len != 0)
            {
                Utils.log(dbg_da,1,"reading " + content_len + " bytes of content");
                byte buf[] = new byte[content_len];
                i_stream.read(buf,0,content_len);
                Utils.log(!ok ? 0 : dbg_da + 1,2,"content=" + new String(buf));
                bas = new ByteArrayInputStream(buf);
            }
            else
            {
                ok = false;
                Utils.warning(dbg_da,1,"NO CONTENT LENGTH in response");
            }

            // build the document

            if (ok) try
            {
                doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bas);
            }
            catch (Exception e)
            {
                Utils.warning(0,0,"Exception(" + e + ") trying to parseXML doAction(" + service + "," + action + ") to " + url);
            }

            // can't pass a bufferedStreamReader to this ....
        }
        catch (Exception e)
        {
            Utils.warning(0,0,"Exception(" + e + ") trying to get doAction(" + service + "," + action + ") to " + url);
            doc = null;
        }

        try {i_stream.close();} catch(Exception e) {}
        try {i_stream.close();} catch(Exception e) {}

        return doc;
    }






    public static Document doAction3(
        String url,
        String urn,
        String service,
        String action,
        stringHash args)
    // The FUNKY way
    // Threading handled by Device
    {
        String xml = getSoapBody(urn,service,action,args);

        // build the headers

        String ip = Utils.ipFromUrl(url);
        String port = Utils.portFromUrl(url);
        String path = url.replace("http://","");
        path = path.replaceAll("^.*?\\/","/");

        String text = "POST " + path + " HTTP/1.1\r\n";
        text += "HOST: " + ip + ":" + port + "\r\n";
        text += "Content-Type: text/xml; charset=\"utf-8\"\r\n";
        text += "Content-Length: " + xml.length() + "\r\n";
        text += "SOAPACTION: \"urn:" + urn + ":service:" + service + ":1#" + action + "\"\r\n";
        text += "\r\n";
        text += xml;

        // Send the request

        Socket sock = null;
        Document doc = null;
        InputStream i_stream = null;

        try
        {
            sock = new Socket(ip,Utils.parseInt(port));
            OutputStreamWriter osw = new OutputStreamWriter(sock.getOutputStream(),"UTF-8");
            osw.write(text);
            osw.flush();

            // the connection actually happens here

            Utils.log(dbg_da,0,"GETTING REPLY to doAction(" + service + "," + action + ") to " + url);
            i_stream = new BufferedInputStream(sock.getInputStream());
            Utils.log(dbg_da,1,"GOT REPLY!!");

            // Read the result

            int MAX_BUF = 20000;
            int SLEEP_PER_RETRY = 100;
            int NUM_RETRIES = 40;

            boolean ok = false;
            int state = 0;
                // 0 = reading
                // 1 = processing content
                // 2 = finizhed

            int buffer_pos = 0;
            int buffer_len = 0;
            byte buf[] = new byte[MAX_BUF];
            boolean in_headers = true;
            int expected = 2;
                // expect at least \r\n
            int content_len = 0;
            String cur_line = "";

            int num_tries = 0;
            while (state < 3)
            {
                int available = i_stream.available();
                if (available > 0)
                {
                    if (buffer_len + available >= MAX_BUF)
                    {
                        Utils.warning(0,0,"Attempt to read reply bigger than " + MAX_BUF + " bytes. buf_len=" + buffer_len + " avail=" + available);
                        available = MAX_BUF - buffer_len;
                        if (state==0)
                            state = 1;
                    }
                    if (available > 0)  // recheck for exceded MAX_BUF
                    {
                        int num_read = i_stream.read(buf,buffer_len,available);
                        buffer_len += num_read;
                    }
                }
            }



            String line;

            line = readline(i_stream);
            if (line == null)
            {
                ok = false;
                Utils.warning(0,0,"No reply to doAction(" + service + "," + action + ") to " + url);
            }
            else
            {
                Utils.log(dbg_da,1,"first_line=" + line);
                if (!line.contains("200"))
                {
                    ok = false;
                    Utils.warning(0,0,"Bad reply to doAction(" + service + "," + action + ") to " + url);
                    Utils.warning(0,1,"first_line=" + line);
                }
            }

            // read the headers (and display them if !ok)

            //while ((line = reader.readLine()) != null && !line.isEmpty())
            while ((line = readline(i_stream) ) != null && !line.isEmpty())
            {
                Utils.log(!ok?0:dbg_da+1,2,"header_line=" + line);
                int pos = line.indexOf(":");
                if (pos > 1)
                {
                    String lval = line.substring(0,pos).toLowerCase();
                    String rval = pos < line.length() ? line.substring(pos+1) : "";
                    rval = rval.replaceFirst("^\\s*","");
                    if (lval.equals("content-length"))
                    {
                        content_len = Utils.parseInt(rval);
                    }
                }
            }

            ByteArrayInputStream bas = null;
            if (content_len != 0)
            {
                Utils.log(dbg_da,1,"reading " + content_len + " bytes of content");
                byte new_buf[] = new byte[content_len];
                i_stream.read(new_buf,0,content_len);
                Utils.log(!ok ? 0 : dbg_da + 1,2,"content=" + new String(buf));
                bas = new ByteArrayInputStream(new_buf);
            }
            else
            {
                ok = false;
                Utils.warning(dbg_da,1,"NO CONTENT LENGTH in response");
            }

            // build the document

            if (ok) try
            {
                doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bas);
            }
            catch (Exception e)
            {
                Utils.warning(0,0,"Exception(" + e + ") trying to parseXML doAction(" + service + "," + action + ") to " + url);
            }

            // can't pass a bufferedStreamReader to this ....
        }
        catch (Exception e)
        {
            Utils.warning(0,0,"Exception(" + e + ") trying to get doAction(" + service + "," + action + ") to " + url);
            doc = null;
        }

        try {i_stream.close();} catch(Exception e) {}
        try {i_stream.close();} catch(Exception e) {}

        return doc;
    }


}   // class doAction