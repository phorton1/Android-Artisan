//--------------------------------------------------------------
// OpenEventSubscriber - member of list on OpenEventManager
//--------------------------------------------------------------

package prh.server.http;


import java.util.Date;

import prh.server.SSDPServer;
import prh.utils.Utils;

public class OpenEventSubscriber
{
    private static int next_id = 0;
    private static int EXPIRE_DURATION = 1800;
        // short for testing

    private String url;
    private String ua;
    private String sid;
    private int expires;
    private OpenEventHandler handler;

    private int event_count = 0;
        // the post-increment SEQ "event key" for individual subscribers
    private int update_count = -1;
        // the update_count from the handler ..
        // if different from handler, event will be sent

    private String open_urn = "urn:av-openhome-org:service";


    public OpenEventSubscriber(OpenEventHandler the_handler, String notification_url, String user_agent)
    {
        handler = the_handler;
        url = notification_url;
        ua = user_agent;
        sid = SSDPServer.dlna_uuid[SSDPServer.IDX_OPEN_HOME] + "-" + String.format("%06d",next_id++);
        refresh();
    }


    public String getUrl()                  { return url; }
    public String getUserAgent()            { return ua; }
    public String getSid()                  { return sid; }
    public OpenEventHandler getHandler()    { return handler; }
    public int getDuration()                { return EXPIRE_DURATION; }

    public void refresh()                   { expires = Utils.now_seconds() + EXPIRE_DURATION; }
    public boolean expired()                { return Utils.now_seconds() > expires; }

    public int incEventNum()                { return event_count++; }
    public int getUpdateCount()             { return update_count; }
    public void setUpdateCount(int count)   { update_count = count; }

    public String getIp()
    {
        return Utils.ipFromUrl(url);
    }

    public String getPort()
    {
        return Utils.portFromUrl(url);
    }


}   // class OpenEventSubscriber
