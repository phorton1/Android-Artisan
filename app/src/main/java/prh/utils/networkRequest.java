package prh.utils;

// Network requests DO NOT SHOW ERRORS TO THE USER.
// They DO REPORT WARNINGS TO ME.
// Client errors are only on the final result, and
// returned via handleNetworkRequestResult().


import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import prh.types.stringHash;


public class networkRequest extends Thread
{
    private static int dbg_nr = 1;

    private static int NUM_RETRIES = 100;
    private static int WAIT_SLEEP_TIME = 120;
        // sleep for 100 milliseconds at a time waiting for
        // results. Fail after 10 seconds.

    // request

    public networkResponseHandler caller = null;     // passed in
    public Object caller_id = null;                 // passed in
    public String url = "";                         // the required request url
    public String content_type = "text/html";       // the content type (text/html or text/xml)
    public stringHash request_headers = null;       // the special request_headers, if any
    public String post_content = null;              // the post content, if any

    // reply

    public boolean ready = false;                   // state
    public String full_response = "";               // full response
    public Object the_result = null;                    // the parsed response
    public stringHash response_headers = null;      // the response headers
    public String error_reason = null;              // the reason if result is null


    public interface networkResponseHandler
        // a general Asynchronous network request handler
        // that can handle calls made to networkRequest.
    {
        // handleNetworkRequestResult() will be called with whatever
        // was passed in for caller and id, with the result as a String
        // or Document and the http headers in headers. The result will be null
        // if the request failed, and reason will be set to the displayable
        // error message.

        void handleNetworkResponse(networkRequest response);
    }


    public networkRequest(
        String the_content_type,
        networkResponseHandler the_caller,
        Object the_caller_id,
        String the_url,
        stringHash extra_headers,
        String content)
        // Private Constructor
    {
        caller = the_caller;
        caller_id = the_caller_id;
        url = the_url;
        request_headers = extra_headers;
        post_content = content;
        error_reason = "";
        content_type = the_content_type;
    }


    // public synchronous calls

    public static networkRequest httpRequest(String the_url, stringHash extra_headers, String content)
    {
        networkRequest rslt = new networkRequest("text/html",null,null,the_url,extra_headers,content);
        Thread run_it = new Thread(rslt);
        run_it.start();
        rslt.wait_for_result();
        return rslt;
    }

    public static networkRequest xmlRequest(String the_url, stringHash extra_headers, String content)
    {
        networkRequest rslt = new networkRequest("text/xml",null,null,the_url,extra_headers,content);
        Thread run_it = new Thread(rslt);
        run_it.start();
        rslt.wait_for_result();
        return rslt;
    }

    public void wait_for_result()
    {
        int count = 0;
        while (!ready && count++<NUM_RETRIES)
        {
            Utils.log(dbg_nr+2,0,"networkRequest(" + content_type + ").wait_for_result()");
            Utils.sleep(WAIT_SLEEP_TIME);
        }
        if (!ready)
        {
            Utils.warning(0,0,"Network request TIME_OUT(" + this.url + " ");
        }
    }



    // asynchronous clients call the public ctor directly,
    // create a thread with the returned object
    // and implement networkResponseHandler

    public void run()
    {
        String dbg_msg = " for " + content_type + " at " + url;
        Utils.log(dbg_nr,0,"networkRequest(" + (post_content==null?"GET":"POST") + ")" + dbg_msg);

        //url = Utils.server_uri + "/service/AVTransport1.xml";
        //url = Utils.server_uri + "/AVTransport/control";

        URL the_url = null;
        try
        {
            the_url = new URL(url);
        }
        catch (Exception e)
        {
            Utils.error("Setting url: " + e);
        }

        // Send the request

        boolean ok = false;
        Object result = null;
        InputStream is = null;
        HttpURLConnection connection = null;

        if (the_url != null)
        try
        {
            connection = (HttpURLConnection) the_url.openConnection();
            connection.setRequestMethod(post_content == null ? "POST" : "GET");
            connection.setRequestProperty("User-Agent",Utils.programName);
            connection.setRequestProperty("Content-Type",content_type);
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(post_content != null);

            for (String key: request_headers.keySet())
            {
                String value = request_headers.get(key);
                connection.setRequestProperty(key,value);
            }

            if (post_content != null)
            {
                connection.setRequestProperty("Content-Length","" + post_content.length());
                DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                wr.writeBytes(post_content);
                wr.flush();
                wr.close();
            }

            // Get the header fields
            // One reason we don't like this way of doing things is that when
            // BuP returns 501 Internal Server Error, it also returns some valuable
            // xml explaining the error, but HTTPUrlConnection bails on it ...

            Utils.log(dbg_nr+1,1,"Getting header fields");
            Map<String,List<String>> headers = connection.getHeaderFields();
            Utils.log(dbg_nr+1,1,"HEADER FIELDS");

            response_headers = new stringHash();
            for (String key:headers.keySet())
            {
                List<String> values = headers.get(key);
                for (String value : values)
                {
                    Utils.log(dbg_nr+1,2,"header(" + key + ")='" + value + "'");
                    response_headers.put(key,value);    // only the last one of each kept
                }
            }

            // Wah-ha-ha ... if it's not a 200, ask the HttpURLConnection
            // for the error stream!

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK)
            {
                ok = true;
                is = connection.getInputStream();
            }
            else
            {
                Utils.warning(0,0,"HTTP_FAILURE("+connection.getResponseCode()+") in networkRequest(" + (post_content==null?"GET":"POST") + ")" + dbg_msg);
                is = connection.getErrorStream();
            }

            // Read the Response
            // We're already at the content
            // Read it into full_response here,
            // and if we're debugging xml, switch
            // the stream to a byte_array stream

            InputStream use_stream = is;
            if (true || content_type.equals("text/plain"))
            {
                String line;
                BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                while ((line = rd.readLine()) != null)
                {
                    full_response += line + "\n";
                }
                rd.close();
                if (content_type.equals("text/plain"))
                    result = full_response;
                else // for debugging "text/xml"))
                    use_stream = new ByteArrayInputStream(full_response.getBytes());
            }

            if (content_type.equals("text/xml"))
            {
                try
                {
                    result = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(use_stream);
                }
                catch (Exception e)
                {
                    ok = false;
                    error_reason = e.toString();
                    Utils.warning(0,0,"Exception: " + e + " while trying to parseXML" + dbg_msg);
                }
            }
        }

        catch (Exception e)
        {
            ok = false;
            error_reason = e.toString();
            Utils.warning(0,0,"Exception: " + e + " in networkRequest(" + (post_content==null?"GET":"POST") + ")" + dbg_msg);
        }

        try { is.close(); } catch (Exception e) {}
        try { connection.disconnect(); } catch (Exception e) {}

        // return the results to the caller
        // always leave the full_response available for viewing
        // but if it's a bad 501 here, but there's a document, we can show it

        if (ok)
        {
            the_result = result;
        }
        else if (result instanceof Document)
        {
            Element ele = ((Document) result).getDocumentElement();
            String err = Utils.getTagValue(ele,"errorDescription");
            if (!err.isEmpty())
                error_reason = err;
            else
                error_reason = full_response;
        }

        ready = true;

        if (caller!= null)
            caller.handleNetworkResponse(this);

    }   // networkRequest::run()
}   // class networkRequest
