package prh.utils;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import prh.artisan.Artisan;
import prh.artisan.R;


public class Utils {

    public static int global_debug_level = 0;
    // 1 = warnings
    // 2 = linear program flow
    // 3 = first level of loops, etc
    private static int dbg_utils = 1;

    // private static int iiiiii = debug_Build();

    public static String ID_CAR_STEREO = "JDQ39";
    // The car stereo is magic ... it supports extra
    // and runs the volume fixer

    // configuration variables

    public static String server_ip = null;
    // THE IP ADDRESS OF THE APP IS SET IN SetMainActivity() !!!
    // These are constants that I used to use:
    // device_name.equals("vbox86tp") ?  "192.168.0.115" : "192.168.0.103";
    public static int server_port = 8008;
    // port for the http server
    public static String server_uri = "";
    // http://server_ip:server_port


    // common device description

    public static String deviceIconUrl      = "";       // server_uri + "/icons/artisan.png";
    public static String deviceWebUrl       = "";       // server_uri + "/webui";
    public static String programName        = "Artisan";
    public static String modelUrl           = "http://www.phorton.com";
    public static String modelNumber        = "123456";
    public static String modelInfo          = "an Android DLNA Server, Renderer, and Library";
    public static String serial_number      = "123456";
    public static String manufacturerName   = "Patrick Horton";
    public static String manufacturerUrl    = modelUrl;

    // run-time variables
    // inited in context of a MainActivity

    private static Artisan artisan = null;
    public static File cache_dir = null;


    //-------------------------------------------------------------
    // static_init()
    //-------------------------------------------------------------
    // and de-initialize Utils at end of onDestroy (with null).
    // method called 'onUtilsError(error_handler_activity,msg)'
    // will be called. until then, errors will just go to the
    // logfile/logcat

    public static void static_init(Artisan a)
    {
        artisan = a;
        String msg = artisan == null ? "null" : "Artisan";
        log(0,0,"Utils.static_init(" + msg + ")");

        server_ip = null;
        server_uri = "";
        deviceIconUrl = "";
        deviceWebUrl = "";
        cache_dir = null;

        if (artisan != null)
        {
            cache_dir = artisan.getCacheDir();

            WifiManager wifiMan = (WifiManager) artisan.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInf = wifiMan.getConnectionInfo();
            int ipAddress = wifiInf.getIpAddress();
            if (ipAddress != 0)
            {
                String ip = String.format("%d.%d.%d.%d",(ipAddress & 0xff),(ipAddress >> 8 & 0xff),(ipAddress >> 16 & 0xff),(ipAddress >> 24 & 0xff));
                log(0,1,"Initial Wifi IP address = " + ip);
                server_ip = ip;
                server_uri = "http://" + server_ip + ":" + server_port;
                deviceIconUrl = server_uri + "/icons/artisan.png";
                deviceWebUrl = server_uri + "/webui";
            }
            else
            {
                error("No Wifi IP address found");
            }
        }
    }


    //-------------------------------------
    // output routines
    //-------------------------------------

    public static void log(int debug_level, int indent_level, String msg)
    {
        log(debug_level, indent_level, msg, 1);
    }

    public static void error(String msg)
    {
        log(0,0,"ERROR: " + msg, 1);
        if (true)   // show the error context
        {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (int level=4; level<stack.length; level++)
            {
                StackTraceElement e = stack[level];
                Log.d("prhcs","... from " + e.getClassName() + "::" + e.getMethodName() + "(" + e.getFileName() + ":" + e.getLineNumber() + ")");

                // optional .. only show one level past our package

                if (true && !e.getClassName().startsWith("com.prh.carstereo")) { break; }
            }
        }
        // show an error dialog
        // apparently this does not work from a thread,
        // like the http server?!?

        if (artisan != null)
        {
            artisan.onUtilsError(msg);
        }
    }

    public static void warning(int debug_level, int indent_level, String msg)
    {
        log(debug_level, indent_level, "WARNING: " + msg, 1);
    }

    protected static void log(int debug_level, int indent_level, String msg, int call_level)
    {

        if (debug_level <= global_debug_level)
        {
            // The debugging filter is by java filename
            // get the incremental level due to the call stack

            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            int level = 0;
            while (level+call_level+4 < stack.length &&
                stack[level+call_level+4].getClassName().startsWith("com.prh"))
            {
                level++;
            }
            // Log.d("prhcs","--- level ends up as " + level);

            indent_level += level;

            StackTraceElement caller = stack[call_level+3];
            String filename = caller.getFileName();
            // filename = filename.replaceAll("\\.java$", "");

            String indent = "";
            while (indent_level-- > 0)
            {
                indent += "   ";
            }

            Log.d("prhcs", pad("(" +filename + ":" + caller.getLineNumber() + ")", 27) + " " + indent + msg);
        }
    }


    public static void noSongsMsg(String playlist_name)
    {
        String msg = "No supported songs types found in playlist '" + playlist_name + "'";
        Utils.error(msg);
        Toast.makeText(artisan.getApplicationContext(),msg,Toast.LENGTH_LONG).show();
    }


    public static String getNodeAttr(Node node,String name)
    {
        NamedNodeMap attrs = node.getAttributes();
        Node found = attrs.getNamedItem(name);
        if (found != null)
            return found.getNodeValue();
        return "";
    }


    //-------------------------------------
    // Utility Routines
    //-------------------------------------

    public static boolean supportedType(String type)
    // the emulator cannot play WMA files!
    {
        if (type.equalsIgnoreCase("MP3")) return true;
        if (type.equalsIgnoreCase("M4A")) return true;
        if (type.equalsIgnoreCase("WMA") && ID_CAR_STEREO.equals(Build.ID)) return true;
        return false;
    }

    public static int parseInt(String s)
    {
        int retval = 0;
        if (s != null && !s.equals(""))
        {
            try
            {
                s = s.replaceAll("\\.*","");
                retval = Integer.valueOf(s);
            }
            catch (Exception e)
            {
                warning(0,0,"parseInt(" + s + ") exception:" + e.toString());
            }
        }
        return retval;
    }

    public static int inc_wrap_int(int value)
    {
        if (value == Integer.MAX_VALUE)
            value = 1;
        else
            value++;
        return value;
    }


    public static void sleep(int millis)
    {
        try { Thread.sleep(millis); }
        catch (Exception e) {}
    }

    public static int now_seconds()
    {
        Date now = new Date();
        Long longTime = new Long(now.getTime() / 1000);
        return longTime.intValue();
    }


    public static String pad(String in, int len)
    {
        String out = in;
        while (out.length() < len) {out = out + " ";}
        return out;
    }

    public static String padTrailingZeros(String s, int len)
    {
        while (s.length() < len) s = s + "0";
        return s;
    }


    public static String extract_re(String re, String value)
        // apply an re to a string value
        // return "", or the first matching group
    {
        Pattern pattern = Pattern.compile(re,Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(value);
        if (matcher.find())
        {
            return matcher.group(1);
        }
        return "";
    }

    public static String pathOf(String full_path)
        // return the containing path for this full path
        // returns the empty string for a path with no slashes
    {
        String path = "";
        String parts[] = full_path.split("\\/");
        for (int i=0; i<parts.length-1; i++)
        {
            if (!path.equals(""))
                path += "/";
            path += parts[i];
        }
        return path;
    }

    public static String ipFromUrl(String url)
    {
        String ip = url.replace("http://","");
        ip = ip.replaceAll(":.*$","");
        Utils.log(9,5,"getIp(" + ip + ") from " + url);
        return ip;
    }

    public static String portFromUrl(String url)
    {
        String port = url.replace("http://","");
        port = port.replaceAll("^.*?:","");
        port = port.replaceAll("\\/.*$","");
        Utils.log(9,5,"getPort(" + port + ") from " + port);
        return port;
    }

    public static String pretty_size(int size)
    {
        double num = size;
        String unit = "B";
        if (num > 1024)
        {
            num = num / 1024;
            unit = "K";
        }
        if (num > 1024)
        {
            num = num / 1024;
            unit = "M";
        }
        if (num > 1024)
        {
            num = num / 1024;
            unit = "G";
        }
        String retval = "";
        if (unit.equals("B"))
        {
            retval = String.format("%dB",size);
        }
        else
        {
            retval = String.format("%.1f%s",num,unit);
        }
        return retval;
    }


    public static String pullTabPart(StringBuffer buffer)
        // pull one tab delimited field off the string
    {
        String result = "";
        int pos = buffer.indexOf("\t",0);
        if (pos>=0)
        {
            if (pos > 0)
                result = buffer.substring(0,pos);
            buffer.delete(0,pos+1);
        }
        else if (buffer.length() > 0)
        {
            result = buffer.toString();
            buffer.delete(0,buffer.length());
        }
        return result;
    }



    public static StringBuffer readBufferLine(StringBuffer buffer)
    {
        String retval = "";
        int pos = buffer.indexOf("\n");
        if (pos >= 0)
        {
            if (pos > 0)
                retval = buffer.substring(0,pos);
            buffer.delete(0,pos+1);
        }
        else if (buffer.length() > 0)
        {
            retval = buffer.toString();
            buffer.delete(0,buffer.length());
        }
        return new StringBuffer(retval);
    }



    //-------------------------------------
    // Duration Routines
    //-------------------------------------

    public enum how_precise {
        FOR_DISPLAY,
        FOR_SEEK,
        FOR_DIDL
    }

    public static String durationToString(int millis,  how_precise precise)
    // if precise, returns hours:minutes:seconds.millis
    // if not, it's for display, and returns as little as m:ss
    // precise times are used in dlna content, etc
    {
        int secs = millis / 1000;
        int mins = secs / 60;
        int hours = mins / 60;

        //mins = mins - (hours * 60);
        //secs = secs - (hours * 60 * 60) - (mins * 60);

        mins = mins % 60;
        secs = secs % 60;
        millis = millis % 1000;

        // That only took half a day ...
        // BubbleUp is very fussy with regards to what it accepts
        // as the reltime. It must be HH::MM:SS with no decimal millis.
        // I guess that's the DLNA standard for seek, but I also want
        // to communicate milli's by didle ... so there are three
        // possibilities.

        if (precise == how_precise.FOR_DIDL)
            return String.format("%02d:%02d:%02d.%03d",hours,mins,secs,millis);
        else if (precise == how_precise.FOR_SEEK)
            return String.format("%02d:%02d:%02d",hours,mins,secs);
        else    // FOR_DISPLAY
        {
            if (hours > 0)
                return String.format("%d:%02d:%02d",hours,mins,secs);
            return String.format("%d:%02d",mins,secs);
        }
    }


    public static int stringToDuration(String text)
    {
        int millis = 0;
        if (text == null) text = "";
        log(dbg_utils+1,1,"stringToDuration(" + text + ") started");
        if (!text.equals(""))
        {
            try
            {
                String dec_parts[] = text.split("\\.");
                String parts[] = dec_parts[0].split(":");

                for (int i = 0; i < parts.length; i++)
                {
                    millis = millis * 60;
                    log(dbg_utils + 1,2,"part(" + i + ")='" + parts[i] + "'   millis=" + millis);
                    millis += parseInt(parts[i]);
                }
                millis *= 1000;
                if (dec_parts.length>1)
                    millis += parseInt(padTrailingZeros(dec_parts[1],3));
            }
            catch (Exception e)
            {
                error("Exception in stringToDuration(" + text + ") " + e.toString());
            }
        }

        log(dbg_utils,1,"stringToDuration(" + text + ") returning " + millis);
        return millis;
    }


    //------------------------------------------------------------------
    // Encode and Encrypt
    //------------------------------------------------------------------

    public static String hex_encode(String s)
    {
        String rslt = "";
        byte bytes[] = s.getBytes();
        for (int i=0; i<s.length(); i++)
            rslt += String.format("%02x",bytes[i]);
        return rslt;
    }


    public static String MD5(String s)
    {
        String retval = null;
        try
        {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(s.getBytes("ASCII"));
            byte[] digest = md.digest();
            StringBuffer sb = new StringBuffer();
            for (byte b : digest)
            {
                sb.append(String.format("%02x", b & 0xff));
            }
            retval = sb.toString();
        }
        catch (Exception e)
        {
            error("Exception in MD5 " + e);
        }
        return retval;
    }


    public static String MD5File(String filename)
    {
        String retval = null;
        try
        {
            int numRead;
            byte[] buffer = new byte[1024];
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            InputStream stream =  new FileInputStream(filename);
            do
            {
                numRead = stream.read(buffer);
                if (numRead > 0)
                {
                    md5.update(buffer, 0, numRead);
                }
            } while (numRead != -1);

            stream.close();
            StringBuffer sb = new StringBuffer();
            for (byte b : md5.digest())
            {
                sb.append(String.format("%02x", b & 0xff));
            }
            retval = sb.toString();
        }
        catch (Exception e)
        {
            error("Exception in MD5File " + e);
        }
        return retval;
    }


    //------------------------------------------------
    // generalized Document (xml) reading
    //------------------------------------------------

    public static String getTagValue(Element parent, String tag)
    // return a single child tag element, or null
    {
        NodeList node_list = parent.getElementsByTagName(tag);
        if (node_list.getLength() > 0)
            return node_list.item(0).getTextContent();
        return "";
    }

    public static Element getTagElement(Element parent, String tag)
    // return a single child tag element, or null
    {
        NodeList node_list = parent.getElementsByTagName(tag);
        if (node_list.getLength() > 0)
            return (Element) node_list.item(0);
        return null;
    }


    public static Element getTagElementBySubTagValue(Element parent, String tag, String sub_tag, String value)
        // for all items of name "tag" (i.e. all actions)
        // return the element who has a child called "sub_tag" (i.e. "name") of value
    {
        NodeList node_list = parent.getElementsByTagName(tag);
        for (int i=0; i<node_list.getLength(); i++)
        {
            Element ele = (Element) node_list.item(i);
            NodeList inner = ele.getElementsByTagName(sub_tag);
            if (node_list.getLength() > 0)
                if (value.equals(((Element) inner.item(0)).getTextContent()))
                    return ele;
        }
        return null;
    }


    //------------------------------------------------------------------
    // Synchronous File Routines
    //------------------------------------------------------------------

    public static boolean writeTextFile(File out_file, String text)
    {
        if (false) // !out_file.canWrite())
        {
            Utils.error("Cannot write file: " + out_file.getAbsolutePath());
            return false;
        }
        try
        {
            FileOutputStream out_stream = new FileOutputStream(out_file);
            byte bytes[] = text.getBytes();
            out_stream.write(bytes,0,bytes.length);
            out_stream.close();
            return true;
        }
        catch (Exception e)
        {
            Utils.error("Exception building xml document:" + e);
        }
        return false;
    }

    public static String[] readTextLines(File in_file, boolean with_error)
    {
        String rslt[] = null;
        try
        {
            if (!in_file.canRead())
            {
                if (with_error)
                    Utils.error("Cannot read Text file: " + in_file.getAbsolutePath());
                return null;
            }
            Scanner sc = new Scanner(in_file);
            List<String> lines = new ArrayList<String>();
            while (sc.hasNextLine())
            {
                lines.add(sc.nextLine());
            }
            rslt = lines.toArray(new String[0]);
        }
        catch (Exception e)
        {
            Utils.error("Exception in readTextLines(" + in_file.getAbsolutePath() + "): " + e.toString());
            rslt = null;
        }
        return rslt;
    }

    public static Document documentFromFile(String filename)
    {
        File in_file = new File(filename);
        if (!in_file.canRead())
        {
            Utils.error("Cannot read XML file: " + filename);
            return null;
        }
        Document doc = null;
        try
        {
            FileInputStream in_stream = new FileInputStream(in_file);
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in_stream);
            in_stream.close();
            if (doc == null)
                Utils.error("documentBuilder returned a null document!");
        }
        catch (Exception e)
        {
            Utils.error("Exception building xml document:" + e);
        }
        return doc;
    }

    public static String http_request(String orig_url)
    // synchronous
    {
        String retval = null;
        try
        {
            URL url = new URL(orig_url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            InputStream i_stream = new BufferedInputStream(connection.getInputStream());
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(i_stream, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null)
            {
                sb.append(line);
            }
            i_stream.close();
            retval = sb.toString();
        }
        catch (Exception e)
        {
            error("Exception in http_request: " + e);
        }
        return retval;
    }

    public static Document xml_request(String url)
    {
        Document doc = null;
        InputStream inputStream = null;
        HttpURLConnection connection = null;
        try
        {
            connection = (HttpURLConnection) new URL(url).openConnection();
            inputStream = connection.getInputStream();
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
        }
        catch (Exception e)
        {
            Utils.warning(0,0,"Could not readXMLDocument("+url+"):" + e);
            // e.printStackTrace();
        }
        try { inputStream.close(); } catch (Exception e) {}
        try { connection.disconnect(); } catch (Exception e) {}
        return doc;
    }



    //----------------------------------------------
    // Asynchronous File Routines
    //----------------------------------------------

    public interface httpResultHandler
    {
        void handleHTTPGetResult(String id, String rslt);
    }

    public static void threadedHTTPGet(
        httpResultHandler handler,
        String id,
        String url)
    // do an http get, and either wait, or not, for the result
    {
        threadedHTTPGetter getter = new threadedHTTPGetter(handler,id,url);
        Thread runner = new Thread(getter);
        runner.start();
    }


    private static class threadedHTTPGetter implements Runnable
    {
        private String id;
        private String url;
        private httpResultHandler handler;

        public threadedHTTPGetter(
            httpResultHandler the_handler,
            String the_id,
            String the_url)
        {
            id = the_id;
            url = the_url;
            handler = the_handler;
        }
        public void run()
        {
            String result = http_request(url);
            if (handler != null)
                handler.handleHTTPGetResult(id,result);
        }
    }


    public interface xmlResultHandler
    {
        void handleXMLGetResult(String id, Document doc);
    }

    public static void threadedXMLGet(
        xmlResultHandler handler,
        String id,
        String url)
    // do an http get, and either wait, or not, for the result
    {
        threadedXMLGetter getter = new threadedXMLGetter(handler,id,url);
        Thread runner = new Thread(getter);
        runner.start();
    }


    private static class threadedXMLGetter implements Runnable
    {
        private String id;
        private String url;
        private xmlResultHandler handler;

        public threadedXMLGetter(
            xmlResultHandler the_handler,
            String the_id,
            String the_url)
        {
            id = the_id;
            url = the_url;
            handler = the_handler;

        }
        public void run()
        {
            Document result = xml_request(url);
            if (handler != null)
                handler.handleXMLGetResult(id,result);
        }
    }



    //--------------------------------------------------
    // View Utilities
    //--------------------------------------------------

    private static int convertDipToPixels(Context context, float dips)
    {
        return (int) (dips * context.getResources().getDisplayMetrics().density + 0.5f);
    }


    public static View setViewSize(Context context, View parent, int id, Float height, Float width)
    // use null to not set one or the other
    // returns the view
    {
        View v = parent.findViewById(id);
        return setViewSize(context,v,height,width);
    }

    public static View setViewSize(Context context, View v, Float height, Float width)
    // use null to not set one or the other
    // returns the view
    {
        ViewGroup.LayoutParams params = v.getLayoutParams();
        if (height != null) params.height = convertDipToPixels(context,height);
        if (width != null) params.width = convertDipToPixels(context,width);
        v.setLayoutParams(params);
        return v;
    }

    public static void setText(View parent, int id, String text)
    {
        ((TextView) parent.findViewById(id)).setText(text);
    }


    //------------------------------------------------
    // Debugging
    //-------------------------------------------------

    public void debugScreenSize(Activity activity)
    {
        // So here we go.
        // Trying to set the emulator(s) to look like the car stereo

        Display display = activity.getWindowManager().getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();
        Utils.log(0,1,"display.getWidth=" + width + "  display.getHeight=" + height);
        // the car stereo returns 800 x 480
        // setting the GenyMotion emulator to: 863x480 at 240dpi
        //     causes this to return 800 x 480

        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        float density = activity.getResources().getDisplayMetrics().density;
        float dpHeight = outMetrics.heightPixels / density;
        float dpWidth = outMetrics.widthPixels / density;

        Utils.log(0,1,"density=" + density + "  dpWidth=" + dpWidth + "  dpHeight=" + dpHeight);
        // car stereo returns 1.5  = 533.33333 , 320.0
        // emulator returns same values when setup as 863x480 at 240 dpi

    }

    private static int debug_Build()
    {
        log(0,0,"BUILD");
        log(0,1,"BOARD 	              = " + Build.BOARD 	    );
        log(0,1,"BOOTLOADER 	      = " + Build.BOOTLOADER 	);
        log(0,1,"BRAND 	              = " + Build.BRAND         );
        log(0,1,"CPU_ABI 	          = " + Build.CPU_ABI 	    );
        log(0,1,"CPU_ABI2 	          = " + Build.CPU_ABI2 	    );
        log(0,1,"DEVICE 	          = " + Build.DEVICE 	    );
        log(0,1,"DISPLAY 	          = " + Build.DISPLAY 	    );
        log(0,1,"FINGERPRINT 	      = " + Build.FINGERPRINT 	);
        log(0,1,"HARDWARE 	          = " + Build.HARDWARE      );
        log(0,1,"HOST                 = " + Build.HOST          );
        log(0,1,"ID 	              = " + Build.ID 	        );
        log(0,1,"MANUFACTURER 	      = " + Build.MANUFACTURER 	);
        log(0,1,"MODEL 	              = " + Build.MODEL	        );
        log(0,1,"PRODUCT 	          = " + Build.PRODUCT 	    );
        log(0,1,"RADIO 	              = " + Build.RADIO         );
        log(0,1,"SERIAL 	          = " + Build.SERIAL 	    );
        //log(0,1,"SUPPORTED_ABIS 	  = " + Build.SUPPORTED_ABIS );
        // level 22
        log(0,1,"TAGS 	              = " + Build.TAGS 	        );
        log(0,1,"TIME                 = " + Build.TIME          );
        log(0,1,"TYPE 	              = " + Build.TYPE 	        );
        log(0,1,"USER                 = " + Build.USER          );
        // log(0,1,"SUPPORTED_32_BIT_ABI = " + Build.SUPPORTED_32_BIT_ABIS);
        // log(0,1,"SUPPORTED_64_BIT_ABI = " + Build.SUPPORTED_64_BIT_ABIS);
        // level 22
        return 0;
    }


}   // class Utils

