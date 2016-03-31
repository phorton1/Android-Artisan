package prh.server.utils;

import org.w3c.dom.Document;

import fi.iki.elonen.NanoHTTPD;

public interface UpnpEventReceiver
{
    // Returns "" for OK,
    // Or an error string to be sent to the client

    String response(
        NanoHTTPD.IHTTPSession session,
        NanoHTTPD.Response response,
        String service,
        Document doc);

    int getEventCount();
}
