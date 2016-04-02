// DLNA Server

package prh.server.http;

import org.w3c.dom.Document;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.artisan.Folder;
import prh.artisan.interfaces.Library;
import prh.artisan.Record;
import prh.artisan.Track;
import prh.device.LocalLibrary;
import prh.server.HTTPServer;
import prh.server.utils.UpnpEventSubscriber;
import prh.server.utils.httpRequestHandler;
import prh.utils.httpUtils;
import prh.utils.Utils;


public class ContentDirectory implements httpRequestHandler
    // The DLNA Server serves the LOCAL LIBRARY
    // It is NOT a pass-thru server to remote libraries!
{
    private static int dbg_dlna = 1;
        // 0 = one time calls (get content, etc)
        // +1 = details (details of one time calls)
        // +2 = things that can happen often (get device descriptions, etc)
        // +3 = details of things that can happen often (get device descriptions, etc)
    private static int dbg_stream = 1;
        // separate variable for debugging the guts of streams

    private Artisan artisan;
    private HTTPServer http_server;
    private String urn;

    public ContentDirectory(Artisan ma, HTTPServer http, String the_urn)
    {
        artisan = ma;
        http_server = http;
        urn = the_urn;
    }

    // not a UpnpEventHandler (yet)
    // so there are no start() or stop() methods


    //----------------------------------------------
    // responses
    //----------------------------------------------

    @Override
    public NanoHTTPD.Response response(
        NanoHTTPD.IHTTPSession session,
        NanoHTTPD.Response response,
        String uri,
        String service,
        String action,
        Document doc,
        UpnpEventSubscriber unused_subscriber)
    {
        // this is currently the only server that both handles
        // actions AND uris.  Everybody else are just actions
        // the /ContentDirectory/ portion of the url has already
        // been removed.

        Library local_library = artisan.getLocalLibrary();
        if (local_library == null)
        {
            Utils.error("No LocalLibrary found in dlna_server_response()");
        }


        //------------------------------------
        // URI Handlers
        //------------------------------------
        // Select Playlist pseudo url
        // the 404 is ignored by bubble up, and the pseudo meta data shows

        if (uri.startsWith("select_playlist/"))
        {
            String name = uri.replace("select_playlist/","");
            name = name.replace(".mp3","");
            Utils.log(dbg_dlna,0,"dlnaServer request to select playlist(" + name + ")");
            artisan.setPlaylist(name,true);
        }

        // Album Art /ContentDirectory/folder_id/folder.jpg

        else if (uri.matches("(.*)/folder.jpg"))
        {
            Utils.log(dbg_dlna,0,"dlnaServer request for " + uri);
            uri = uri.replaceAll("/folder.jpg$", "");
            response = folder_jpg_response(http_server,response,uri);
        }

        // Stream request /ContentDirectory/media/track_id.ext

        else if (uri.matches("media/.*"))
        {
            uri = uri.replaceAll("^media/","");
            uri = uri.replaceAll("\\..*?$", "");
            response = stream_response(http_server, session, response, uri);
        }

        //------------------------------------
        // Action requests
        //------------------------------------
        // Currently only supports Browse
        // HTTPServer has already parsed the action and doc

        else if (uri.equals("control"))
        {
            if (action.equals("Browse"))
               response = browse_response(http_server,response,doc,urn);
            else if (action.equals("Search"))
                ;
            else
                Utils.error("Unsupported action: " + action + " in ContentServer1");
        }

        return response;
    }


    //------------------------------------------
    // responses
    //------------------------------------------

    private NanoHTTPD.Response stream_response(
        HTTPServer server,
        NanoHTTPD.IHTTPSession session,
        NanoHTTPD.Response response,
        String id)
    {
        Utils.log(dbg_stream+1,0,"stream_response(" + id + ")");
        LocalLibrary local_library = artisan.getLocalLibrary();
        if (local_library == null)
        {
            Utils.error("No LocalLibrary found in DLNAServer.stream_response()");
            return response;
        }

        Map<String,String> headers = session.getHeaders();
        for (String key: headers.keySet())
        {
            Utils.log(dbg_stream+1,1,"header("+key+")="+headers.get(key));
        }

        Track track = local_library.getLibraryTrack(id);
        if (track == null)
        {
            Utils.error("could not get track in stream_response(" + id + ")");
            return response;
        }

        String local_uri = track.getLocalUri();
        if (!local_uri.startsWith("file://"))
        {
            Utils.error("unexpected local_uri: " + local_uri );
            return response;
        }

        String path = local_uri.replace("file://","");
        int size = track.getSize();
        String mime_type = track.getMimeType();
        Utils.log(dbg_stream,1,session.getMethod() + " stream file(" + size + "," + mime_type + ")=" + path);

        if (session.getMethod().equals("HEAD"))
        {
            response = server.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mime_type, "");
        }
        else
        {
            int start_range = 0;
            int end_range = size - 1;
            boolean is_ranged = false;
            NanoHTTPD.Response.Status status = NanoHTTPD.Response.Status.OK;

            String range = headers.get("range");
            if (range == null) range = "";
            Utils.log(dbg_stream+1,2,"range=" + range);

            if (range.startsWith("bytes="))
            {
                is_ranged = true;
                range = range.replaceAll("^bytes=","");
                String[] parts = range.split("-");
                if (parts.length>0 && parts[0] != null) start_range = Utils.parseInt(parts[0]);
                if (parts.length>1 && parts[1] != null) end_range = Utils.parseInt(parts[1]);
                Utils.log(dbg_stream,2,"RANGED_RESPONSE start=" + start_range + " end=" + end_range);
                status = NanoHTTPD.Response.Status.PARTIAL_CONTENT;
            }

            File ifile = new File(path);
            if (ifile.canRead())
            {
                try
                {
                    InputStream stream = new FileInputStream(ifile);

                    if (start_range != 0)
                    {
                        Utils.log(dbg_stream+1, 2, "skipping to byte " + start_range);
                        long result = stream.skip(start_range);
                        if (result != start_range)
                        {
                            Utils.error("Could only skip " + result + " of " + start_range + " bytes");
                        }
                    }
                    response = server.newFixedLengthResponse(status, mime_type, stream, end_range-start_range+1);

                    // only add the Content-range header if we are ranged
                    // and it's not a HEAD request!

                    if (is_ranged)
                    {
                        response.addHeader("Content-Range", "bytes " + start_range + "-" + end_range + "/" + size);
                    }
                }
                catch (Exception e)
                {
                    Utils.error("could not create stream or ChunkedResponse file=" + path + " exception=" + e);
                }
            }
            else
            {
                Utils.error("could not read raw file=" + path);
            }
        }   // method presumably == GET

        // add the dlna headers to all responses

        response.addHeader("Accept-Ranges", "bytes");
        response.addHeader("contentFeatures.dlna.org",
            httpUtils.get_dlna_stuff(track.getType()));
        response.addHeader("transferMode.dlna.org","Streaming");

        // had to add public getHeaders() method to NanoHTTPD for this

        Utils.log(dbg_stream+1, 2, "Returning final stream_response");
        if (dbg_stream+1 <= Utils.global_debug_level)
        {
            Map<String,String> dbg_headers = response.getHeaders();
            for (String key : dbg_headers.keySet())
            {
                Utils.log(dbg_stream+1, 3, "return_header(" + key + ")=" + dbg_headers.get(key));
            }
        }
        return response;

    }


    private NanoHTTPD.Response folder_jpg_response(
        HTTPServer server,
        NanoHTTPD.Response response,
        String id)
        // the id is the id of the folder, from which we get the path and retrieve the folder.jpg
    {
        Utils.log(dbg_dlna,0,"folder_jpg_response(" + id + ")");
        LocalLibrary local_library = artisan.getLocalLibrary();
        if (local_library == null)
        {
            Utils.error("No LocalLibrary found in folder_jpg_response()");
            return response;
        }

        Folder folder = local_library.getLibraryFolder(id);
        if (folder == null)
        {
            Utils.error("No folder(" + id + ") found in folder_jpg_response");
            return response;
        }

        String local_uri = folder.getLocalArtUri();
        if (!local_uri.startsWith("file://"))
        {
            Utils.error("unexpected localArtUri: " + local_uri );
            return response;
        }

        String path = local_uri.replace("file://","");
        Utils.log(dbg_dlna+1,1,"opening art full_path=" + path);
        response = httpUtils.raw_file_response(server,response,path);
        if (response.getStatus() == NanoHTTPD.Response.Status.NOT_FOUND)
        {
            response = server.asset_file_response(response,"icons/no_image.png");
        }

        return response;
    }


    private NanoHTTPD.Response browse_response(
        HTTPServer server,
        NanoHTTPD.Response response,
        Document doc,
        String urn)
    {
        String id = httpUtils.getXMLString(doc,"ObjectID",true);
        int start = httpUtils.getXMLInt(doc,"StartingIndex",true);
        int count = httpUtils.getXMLInt(doc,"RequestedCount",true);
        String flag = httpUtils.getXMLString(doc,"BrowseFlag",true);
        Utils.log(dbg_dlna,0,"browse_response(" + id + "," + start + "," + count + "," + flag + ")");

        // error checking and parameter munging

        if (!flag.equals("BrowseDirectChildren") &&
                !flag.equals("BrowseMetadata"))
        {
            Utils.error("BrowseFlag: " + flag + " is NOT supported");
            return response;
        }

        LocalLibrary local_library = artisan.getLocalLibrary();
        Folder folder = local_library.getLibraryFolder(id);
        if (folder == null)
        {
            Utils.error("could not get folder(1)");
            return response;
        }

        if (flag.equals("BrowseMetadata"))
        {
            // this should just get the object itself

            id = folder.getParentId();
            folder = local_library.getLibraryFolder(id);
            if (folder == null)
            {
                Utils.error("could not get folder(2)");
                return response;
            }
        }

        // build the xml soap response

        if (count == 0) count = 10;

        // assumed to be a non-metadata request for a folder,
        // not metadata or subitems for a track

        List<Record> subitems = local_library.getSubItems(id,start,count,false);

        int num_items = subitems.size();
        Utils.log(dbg_dlna,1,"building http response for " + num_items + " items");

        String response_text = httpUtils.action_response_header(urn,"ContentDirectory","Browse");

        String didl = httpUtils.start_didl();
        for (Record rec: subitems)
        {
            String part;
            if (rec instanceof Track)
                part = getTrackMetadata((Track)rec);
            else
                part = ((Folder) rec).getMetadata();
            didl += part;
        }
        didl += httpUtils.end_didl();
        response_text += httpUtils.encode_xml(didl);

        response_text += content_response_footer(
            urn,
            "Browse",
            num_items,
            folder.getNumElements());
        Utils.log(dbg_dlna,1,"Done with browse_response(" + id + ")");

        response = server.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "text/xml",
                response_text);

        return response;
    }



    //-----------------------------------------------------------
    // static xml text
    //-----------------------------------------------------------


    private static String content_response_footer(String urn, String action, int num_actual, int num_total)
    {
        return httpUtils.action_response_footer(
            urn,
            action,
            "<UpdateID>12345</UpdateID>\n" +
                "<NumberReturned>" + num_actual + "</NumberReturned>\n" +
                "<TotalMatches>" + num_total + "</TotalMatches>\n");
    }


    public static String getTrackMetadata(Track track)
        // supports virtual select_playlist_ items
    {
        String id = track.getId();
        Utils.log(dbg_dlna + 2,0,"starting xml_item(" + id + ")");

        // virtual items

        if (id.startsWith("select_playlist_"))
        {
            String name = id.replace("select_playlist_","");
            String text =
                    "<item id=\"" + id + "\" parentID=\"" + track.getParentId() + "\" restricted=\"1\">" +
                    "<dc:title>" + track.getTitle() + "</dc:title>" +
                    "<upnp:class>object.item.audioItem</upnp:class>" +
                    "<res protocolInfo=\"http-get:*:audio/mpeg:*:\">" +
                    Utils.server_uri + "/dlna_server/select_playlist/" + name + ".mp3" +
                    "</res>" +
                    "</item>";
            Utils.log(dbg_dlna + 1,0,"VIRTUAL xml_item(" + name + ")\n" + text);
            return text;
        }

        return track.getMetadata();
    }


}   // class DLNAServer
