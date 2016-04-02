//-------------------------------------------------------
// UpnpEventHandler - API called by UpnpEventManager
//-------------------------------------------------------

package prh.base;


import prh.server.utils.UpnpEventSubscriber;

abstract public interface UpnpEventHandler
{
    String getName();

    public void start();
    public void stop();

    int getUpdateCount();
    int incUpdateCount();

    String getEventContent(UpnpEventSubscriber subscriber);
    void notifySubscribed(UpnpEventSubscriber subscriber,boolean subscribe);
        // subscribe==false means unsubscribe

}   // inteface UpnpEventHandler
