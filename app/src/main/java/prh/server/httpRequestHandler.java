package prh.server;

import org.w3c.dom.Document;

import fi.iki.elonen.NanoHTTPD;

public abstract class httpRequestHandler
{
    public abstract NanoHTTPD.Response response(
        NanoHTTPD.IHTTPSession session,
        NanoHTTPD.Response response,
        String uri,
        String service,
        String action,
        Document doc);
}
