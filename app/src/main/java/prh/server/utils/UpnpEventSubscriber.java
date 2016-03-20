//--------------------------------------------------------------
// UpnpEventSubscriber - member of list on UpnpEventManager
//--------------------------------------------------------------

package prh.server.utils;


import prh.server.SSDPServer;
import prh.server.utils.UpnpEventHandler;
import prh.utils.Utils;

public class UpnpEventSubscriber
{
    private static int next_id = 0;
    private static int EXPIRE_DURATION = 1800;
        // short for testing

    private String url;
    private String ua;
    private String sid;
    private int expires;
    private UpnpEventHandler handler;
    private int openPlaylist_exposure_mask = 0;
        // set intelligently to 1^0..31 if the subscriber
        // is to the OpenPlaylist and we want to use the
        // EXPOSE_SCHEME for the subscriber. This mask is
        // used to create (register) and destroy (unregister)
        // the (Local)PlaylistExposers on the OpenPlayList,
        // which in turn, are cleared and set into each
        // LocalPlaylist as it is started.

    private int event_count = 0;
        // the post-increment SEQ "event key" for individual subscribers
    private int update_count = -1;
        // the update_count from the handler ..
        // if different from handler, event will be sent

    private String open_urn = "urn:av-openhome-org:service";


    public UpnpEventSubscriber(UpnpEventHandler the_handler,String notification_url,String user_agent)
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
    public UpnpEventHandler getHandler()    { return handler; }
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



}   // class UpnpEventSubscriber
