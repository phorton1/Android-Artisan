package prh.utils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;


public class AsyncHTTPRequest extends Thread
        // fire and forget
{
    String m_url;
    HashMap<String,String> m_headers;
    String m_content;
    String m_result = null;

    public static AsyncHTTPRequest sendRequest(String url, HashMap<String,String> headers, String content)
    {
        AsyncHTTPRequest send = new AsyncHTTPRequest(url,headers,content);
        send.start();
        return send;
    }

    private AsyncHTTPRequest(String url,HashMap<String,String> headers,String content)
        // pass in null content for a GET, or something for a POST
    {
        m_url = url;
        m_headers = headers;
        m_content = content;
        Utils.log(0,0,"AsyncHTTPRequest() ctor called");
    }

    public void run()
    {
        Utils.log(0,0,"Sending " + (m_content==null?"GET":"POST") + " request to " + m_url);
        URL the_url = null;
        try
        {
            the_url = new URL(m_url);
        }
        catch (Exception e)
        {
            Utils.error("Setting url: " + e);
        }

        if (the_url != null)
        try
        {
            HttpURLConnection connection = (HttpURLConnection) the_url.openConnection();
            connection.setRequestMethod(m_content == null ? "POST" : "GET");
            connection.setRequestProperty("User-Agent",Utils.programName);
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(m_content != null);

            for (String key: m_headers.keySet())
            {
                String value = m_headers.get(key);
                connection.setRequestProperty(key,value);
            }

            if (m_content != null)
            {
                connection.setRequestProperty("Content-Length","" + m_content.length());
                DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                wr.writeBytes(m_content);
                wr.flush();
                wr.close();
            }

            //Get Response

            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuffer response = new StringBuffer();
            while((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            rd.close();

            Utils.log(0,1,"response=" + response.toString());
            m_result = response.toString();
        }

        catch (Exception e)
        {
            Utils.error("Error Sending Event POST Request to " + m_url + ":" + e.toString());
        }

    }   // AsyncHTTPRequest::run()


}   // class AsyncHTTPRequest
