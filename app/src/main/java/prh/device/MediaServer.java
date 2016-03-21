package prh.device;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import prh.artisan.Artisan;
import prh.artisan.Folder;
import prh.artisan.Library;
import prh.artisan.Record;
import prh.artisan.Track;
import prh.utils.Utils;
import prh.utils.httpUtils;
import prh.utils.stringHash;

public class MediaServer extends Device implements Library
{
    // Tracks are only returned as sub-items of folders.
    // Therefore to support the getTrack() api, and generally
    // we cache the tree of folders and track that we get.
    // These might be better kept in a separate set of
    // database files, and they could be persistent between
    // sessions ....
    //
    // The client must start at library item "0", and only
    // ask for folders and tracks it has already gotten the
    // parents of.
    //
    // Currently does not do any throttling .. it asks for
    // all the subitems of everything ...


    public class folderHash extends HashMap<String,FolderPlus> {}
    public class trackHash extends HashMap<String,Track> {}
    folderHash folders = null;
    trackHash tracks = null;


    //----------------------------------------
    // Device ctors
    //----------------------------------------

    public MediaServer(Artisan artisan)
    {
        super(artisan);
    }


    public MediaServer(Artisan artisan, SSDPSearch.SSDPDevice ssdp_device)
    {
        super(artisan,ssdp_device);
        Utils.log(0,0,"new MediaServer(" + ssdp_device.getFriendlyName() + "," + ssdp_device.getDeviceType() + "," + ssdp_device.getDeviceUrl());
    }


    //----------------------------------------
    // Library Interface
    //----------------------------------------

    public String getName()
    {
        return getFriendlyName();
    }


    public boolean start()
    {
        tracks = new trackHash();
        folders = new folderHash();
        return true;
    }


    public void stop()
    {
        if (tracks != null)
            tracks.clear();
        tracks = null;

        if (folders != null)
            folders.clear();
        folders = null;
    }


    public Track getTrack(String id)
    {
        Track track = null;
        if (tracks != null)
            track = tracks.get(id);
        return track;
    }

    public FolderPlus getFolder(String id)
    {
        FolderPlus folder = null;
        if (folders != null)
            folder = folders.get(id);

        if (folder == null && id.equals("0"))
        {
            HashMap<String,Object> params = new HashMap<>();
            params.put("id",id);
            params.put("dirtype","root");
            params.put("title","root");
            folder = new FolderPlus(new Folder(params));
            folders.put(id,folder);
        }
        return folder;
    }

    //-------------------------------------------------
    // Implementation
    //-------------------------------------------------
    // Derived in memory folder that keeps it's subitems

    private class FolderPlus extends Folder
    {
        List<Record> sub_items = null;
        List<Record> getSubItems() { return sub_items; };

        public FolderPlus(HashMap<String,Object> hash)
        {
            super(hash);
            putInt("num_elements",0);
        }

        public void addItem(Record record)
        {
            if (sub_items == null)
                sub_items = new ArrayList<Record>();
            sub_items.add(record);
            putInt("num_elements",sub_items.size());
        }
    }



    public List<Record> getSubItems(String unused_table,String id,int start,int count)
        // special case of 0 bootstraps the system
        // and gets the first root folders.
    {
        FolderPlus folder = getFolder(id);
        if (folder == null && !id.equals("0"))
        {
            Utils.error("Unexpected forward reference to folder(" + id + " before it was gotten as a sub_item of it's parent folder");
            return null;
        }

        // create the 0th folder


        if (folder.getSubItems() != null)
            return folder.getSubItems();

        // get the sub_items from the remote server

        stringHash args = new stringHash();
        args.put("InstanceID","0");
        args.put("ObjectID",id);
        args.put("StartingIndex","0");
        args.put("RequestedCount","999999");
        args.put("Filter","*");
        args.put("SortCriteria","");
        args.put("BrowseFlag","BrowseDirectChildren");
            // we don't use BrowseMetadata which gets the parent

        Document doc = doAction(Service.serviceType.ContentDirectory,"Browse",args);
        if (doc == null)
            return null;

        Element doc_ele = doc.getDocumentElement();
        String num_returned = Utils.getTagValue(doc_ele,"NumberReturned");
        String total_matches = Utils.getTagValue(doc_ele,"TotalMatches");
        String update_id = Utils.getTagValue(doc_ele,"UpdateID");
        String didl = Utils.getTagValue(doc_ele,"Result");

        if (num_returned.isEmpty())
        {
            Utils.error("NumberReturned is empty");
            return new ArrayList<Record>();
        }
        if (didl.isEmpty())
        {
            Utils.error("Result is empty");
            return new ArrayList<Record>();
        }

        Document result_doc = null;
        //String clean_didl = httpUtils.decode_xml(didl);
        ByteArrayInputStream stream = new ByteArrayInputStream(/*clean_*/didl.getBytes());
        try
        {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            result_doc = builder.parse(stream);
        }
        catch (Exception e)
        {
            Utils.error("Could not parse result");
            return new ArrayList<Record>();
        }

        // Parse the result into folder/track subitems

        boolean is_album = false;
        Element res_ele = result_doc.getDocumentElement();
        Node node = res_ele.getFirstChild();
        while (node != null)
        {
            Element node_ele = (Element) node;
            HashMap<String,Object> params = new HashMap<>();
            String node_class = Utils.getTagValue(node_ele,"upnp:class");
            boolean is_track = node_class.startsWith("object.item.audioItem");
            boolean node_is_album = node_class.startsWith("object.container.album");

                // object.container
                // object.container.storageContainer
                // object.container.album.musicAlbum
                // object.item.audioItem
                // object.item.audioItem.musicTrack

            // following more or less the same in my database for tracks and folders

            String item_id = getNodeAttr(node,"id");
            params.put("title",Utils.getTagValue(node_ele,"dc:title"));
            params.put("id",item_id);
            params.put("parent_id",getNodeAttr(node,"parentID"));
            params.put("artist",Utils.getTagValue(node_ele,"upnp:artist"));
            params.put("album_artist",Utils.getTagValue(node_ele,"upnp:albumArtist"));
            params.put("art_uri",Utils.getTagValue(node_ele,"upnp:albumArtURI"));
            params.put("genre",Utils.getTagValue(node_ele,"upnp:genre"));
            params.put("year_str",Utils.getTagValue(node_ele,"dc:date"));
            if (((String) params.get("artist")).isEmpty())
                params.put("artist",params.get("album_artist"));

            if (is_track)
            {
                folder.put("dirtype","album");
                params.put("album_title",Utils.getTagValue(node_ele,"upnp:album"));
                params.put("tracknum",Utils.getTagValue(node_ele,"upnp:originalTrackNumber"));
                    // still need size, duration, uri and "type"

                Track add_track = new Track(params);
                folder.addItem(add_track);
                tracks.put(item_id,add_track);
            }
            else
            {
                params.put("dirtype",node_is_album ? "album" : "folder");
                FolderPlus add_folder = new FolderPlus(params);
                folder.addItem(add_folder);
                folders.put(item_id,add_folder);
            }

            node = node.getNextSibling();
        }

        // return to the caller

        return folder.getSubItems();

    }   // prh.device.MediaServer.getSubItems()


    public static String getNodeAttr(Node node, String name)
    {
        NamedNodeMap attrs = node.getAttributes();
        Node found = attrs.getNamedItem(name);
        if (found != null)
            return found.getNodeValue();
        return "";
    }


}   // class prh.device.MediaServer
