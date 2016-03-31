package prh.device;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

import javax.xml.parsers.DocumentBuilderFactory;

import prh.artisan.Artisan;
import prh.artisan.myLoopingRunnable;
import prh.types.stringHash;
import prh.utils.Utils;

public class Subscription
{
    private static int dbg_sub = 0;


    private static int REQUESTED_TIME = 1800;
        // request 30 minute subscriptions
    private static int RESUBSCRIBE_PCT = 90;
        // The subscription will automatically renew
        // when it is over this percent expired.

    private Artisan artisan;
    myLoopingRunnable my_looper;

    private String sid;
    private Service service;
    private int expire_seconds;
    private boolean is_subscribed;

    public Service getService()     { return service; }
    public boolean isSubscribed()   { return is_subscribed; }

    String dbg_title()  { return " service(" + service.getServiceType() + "::" + service.getFriendlyName() +")"; }


    public Subscription(Service s)
    {
        sid = "";
        service = s;
        expire_seconds = 0;
        is_subscribed = false;
        Utils.log(dbg_sub+1,0,"new Subscription()" + dbg_title());
    }


    public boolean subscribe()
        // start or renew the subscription and
        // set a one-shot looper to do it again
        // when at RESUBSCRIBE_PCT expired
    {
        Utils.log(dbg_sub+1,0,"subscribe()" + dbg_title());

        // do the request

        do_request(true);

        // if the subscription worked

        if (is_subscribed)
        {
            if (my_looper == null)
            {
                Runnable restart = new Runnable()
                {
                    public void run() { subscribe(); }
                };
                my_looper = new myLoopingRunnable(
                    "subscribe(" + service.getServiceType() + ")",
                    restart,
                    expire_seconds * RESUBSCRIBE_PCT * 10);
            }
            my_looper.start();
        }

        Utils.log(dbg_sub,0,"subscribe("+ is_subscribed + ")" + dbg_title());
        return is_subscribed;
    }


    public void unsubscribe()
    {
        Utils.log(dbg_sub,0,"unsubscribe()" + dbg_title());

        // stop the looper

        if (my_looper != null)
            my_looper.stop(false);
        my_looper = null;

        // send the request

        if (is_subscribed)
            do_request(false);
        is_subscribed = false;
    }


    synchronized private void do_request(boolean subscribe)
    {
        Utils.log(dbg_sub+1,0,"start subscribe_request()" + dbg_title());

        doRequest doer = new doRequest(subscribe);
        Thread thread = new Thread(doer);
        thread.start();
        if (subscribe)
        {
            int count = 0;
            while (!doer.getDone())
            {
                if (count++ % 50 == 1)
                    Utils.log(dbg_sub+1,0,"waiting for subscription response");
                Utils.sleep(50);
            }
        }
        Utils.log(dbg_sub+1,0,"finished subscribe_request()" + dbg_title());
    }


    private class doRequest implements Runnable
    {
        boolean done = false;
        boolean subscribe = false;
        public boolean getDone() { return done; }

        public doRequest(boolean subscribe)
        {
            this.subscribe = subscribe;
        }

        public void run()
        {
            // build the message

            String method = subscribe ? "SUBSCRIBE" : "UNSUBSCRIBE";
            Device device = service.getDevice();
            String device_url = device.getDeviceUrl();
            String ip = Utils.ipFromUrl(device_url);
            String port = Utils.portFromUrl(device_url);
            String event_path = service.getEventPath();
            String callback_uri =
                Utils.server_uri +
                "/openCallback/" +
                device.getDeviceUUID() + "/" +
                service.getServiceType();

            String msg = method + " " + event_path + " HTTP/1.1\r\n";
            msg += "HOST: " + ip + ":" + port + "\r\n";
            if (!sid.isEmpty())
                msg += "SID: " + sid + "\r\n";

            if (subscribe)
            {
                msg += "NT: upnp:event\r\n";
                msg += "USER-AGENT: " + Utils.myUserAgent() + "\r\n";
                msg += "CALLBACK: <" + callback_uri + ">\r\n";
                msg += "TIMEOUT: Second-" + REQUESTED_TIME + "\r\n";
            }
            msg += "\r\n";

            // open the socket and send it

            try
            {
                Socket socket = new Socket(ip,Utils.parseInt(port));
                OutputStream ostream = socket.getOutputStream();
                byte bytes[] = msg.getBytes();
                ostream.write(bytes);
                ostream.flush();

                String first_line = null;
                String full_response = "";
                stringHash headers = new stringHash();
                InputStream istream = socket.getInputStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(istream));

                boolean ok = false;
                boolean done = false;
                String line = rd.readLine();
                while (!done && line != null)
                {
                    full_response += line + "\n";
                    int pos = line.indexOf(":");
                    if (first_line == null)
                    {
                        first_line = line;
                        ok = line.contains("200 OK");
                    }
                    else if (pos > 0)
                    {
                        String lval = line.substring(0,pos).toLowerCase();
                        String rval = line.substring(pos + 1);
                        rval = rval.replaceAll("^\\s","");
                        rval = rval.replaceAll("\\s$","");
                        headers.put(lval,rval);
                    }

                    if (!subscribe && first_line != null)
                        done = true;
                    else if (first_line != null &&
                         headers.get("sid") != null &&
                         headers.get("timeout") != null)
                         done = true;

                    if (!done)
                        line = rd.readLine();
                }

                // we only check the result on subscribe
                // and the read is optimized to stop once we've
                // got a first_line, sid, and timeout (or else
                // it waits for timeout on the readLine)

                if (!subscribe)
                    is_subscribed = false;
                else
                {
                    // a result is valid if it has a sid and timeout: second-

                    if (ok)
                    {
                        String try_sid = headers.get("sid");
                        String try_timeout = headers.get("timeout");
                        if (try_sid != null &&
                            try_timeout != null &&
                            !try_sid.isEmpty() &&
                            !try_timeout.isEmpty() &&
                            try_timeout.toLowerCase().contains("second-"))
                        {
                            String try_seconds = Utils.extract_re("-(\\d+)$",try_timeout);
                            if (try_seconds != null && Utils.parseInt(try_seconds) > 0)
                            {
                                is_subscribed = true;
                                Subscription.this.sid = try_sid;
                                expire_seconds = Utils.parseInt(try_seconds);
                                // expire_seconds = 30;  // testing
                            }
                            else
                                Utils.warning(0,0,method + " failed with timeout=" + try_timeout);
                        }
                        else
                            Utils.warning(0,0,method + " failed with sid=" + sid + " timeout=" + try_timeout);
                    }
                    else
                        Utils.warning(0,0,method + " failed with first_line=" + first_line);

                    rd.close();
                }
                socket.close();
            }
            catch (Exception e)
            {
                Utils.error("Could not send " + method + " request to " + ip + ":" + port + "/" + event_path);
            }
            done = true;

        }   // doRequest.run()
    }   // class doRequest
}   // class Subscription
