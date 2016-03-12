//-------------------------------------------------------
// UpnpEventHandler - API called by UpnpEventManager
//-------------------------------------------------------

package prh.server.http;


import prh.artisan.EventHandler;


abstract public interface UpnpEventHandler
{
    String getName();

    public void start();
    public void stop();

    int getUpdateCount();
    int incUpdateCount();

    String getEventContent();

}   // inteface UpnpEventHandler
