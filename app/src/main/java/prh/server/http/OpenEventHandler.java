//-------------------------------------------------------
// OpenEventHandler - API called by OpenEventManager
//-------------------------------------------------------

package prh.server.http;


abstract public interface OpenEventHandler
{
    String eventHandlerName();
    int getUpdateCount();
    int incUpdateCount();
    String getEventContent();

}   // inteface OpenEventHandler
