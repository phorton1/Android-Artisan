package prh.base;

import org.w3c.dom.Document;

import fi.iki.elonen.NanoHTTPD;
import prh.server.utils.UpnpEventSubscriber;

public interface HttpRequestHandler
{
    public abstract NanoHTTPD.Response response(
        NanoHTTPD.IHTTPSession session,
        NanoHTTPD.Response response,
        String uri,
        String service,
        String action,
        Document doc,
        UpnpEventSubscriber subscriber);

    // subscriber will be null, except for OpenPlaylist
    // subscribers that are using the EXPOSE_SCHEME
}
