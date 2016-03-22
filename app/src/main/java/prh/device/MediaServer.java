package prh.device;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.ByteArrayInputStream;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import prh.artisan.Artisan;
import prh.artisan.Folder;
import prh.artisan.Library;
import prh.artisan.Track;
import prh.types.libraryBrowseResult;
import prh.types.objectHash;
import prh.types.recordList;
import prh.utils.Utils;
import prh.types.stringHash;

public class MediaServer extends Device implements Library
{
    // Tracks are only returned as sub-items of folders.
    // Therefore to support the getTrack() api, and generally
    // we cache the tree of folders and track that we get.
    // These might be better kept in a separate set of
    // database files, and they could be persistent between
    // sessions ....
    //
    // The getFolder("0") and one sub_item is loaded on start(),
    // or else it fails.  It is otherwise illegal to ask for
    // sub_items of folders who's parents have not yet been gotten.

    private class folderHash extends HashMap<String,FolderPlus> {}
    private class trackHash extends HashMap<String,Track> {}
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
        Utils.log(0,1,"new MediaServer(" + ssdp_device.getFriendlyName() + "," + ssdp_device.getDeviceType() + "," + ssdp_device.getDeviceUrl());
    }


    //----------------------------------------
    // Library Interface
    //----------------------------------------

    @Override public String getName()
    {
        return getFriendlyName();
    }


    @Override public boolean start()
    {
        tracks = new trackHash();
        folders = new folderHash();

        getFolder("0");
        libraryBrowseResult test = getSubItems("0",0,1,false);
        if (test.getTotalFound() == 0 ||
            test.getNumReturned() == 0)
        {
            Utils.error("Could not start MediaServer(" + getFriendlyName() + ")");
            return false;
        }

        return true;
    }


    @Override public void stop()
    {
        if (tracks != null)
            tracks.clear();
        tracks = null;

        if (folders != null)
            folders.clear();
        folders = null;
    }


    @Override public Track getTrack(String id)
    {
        Track track = null;
        if (tracks != null)
            track = tracks.get(id);
        return track;
    }


    @Override public FolderPlus getFolder(String id)
    {
        FolderPlus folder = null;
        if (folders != null)
            folder = folders.get(id);

        if (folder == null && id.equals("0"))
        {
            // MUST be the first call!
            objectHash params = new objectHash();
            params.put("id",id);
            params.put("dirtype","root");
            params.put("title","root");
            folder = new FolderPlus(new Folder(params));
            folders.put(id,folder);
        }
        return folder;
    }


    @Override public libraryBrowseResult getSubItems(String id,int start,int count, boolean meta_data)
    {
        FolderPlus folder = getFolder(id);
        libraryBrowseResult result;

        if (folder == null)
        {
            Utils.error("Unexpected forward reference to folder(" + id + " before it was gotten as a sub_item of it's parent folder");
            result = new libraryBrowseResult();
        }

        else
        {
            result = folder.getBrowseResult(start,count,meta_data);
        }
        return result;
    }



    //-------------------------------------------------
    // Implementation
    //-------------------------------------------------
    // Derived in memory folder that keeps it's children.

    private class FolderPlus extends Folder
        // it is expected that the records will be requested in order,
        // that is, a search will never start off with start>0
    {
        int num_found = 0;
        int update_id;

        recordList records = new recordList();


        public FolderPlus(objectHash params)
        {
            super(params);
        }


        @Override
        public int getNumElements()
        {
            return num_found;
        }


        public libraryBrowseResult getBrowseResult(int start,int count,boolean meta_data)
        {
            // this method can result in at one call to getRemoteItems()

            if (meta_data)
            {
                Utils.error("Does not support browse with metadata");
                return null;
            }

            if (start + count > records.size())
            {
                int num_to_get = start + count - records.size();
                getRemoteResult(num_to_get);
            }

            libraryBrowseResult result = new libraryBrowseResult();
            result.setTotalFound(num_found);
            result.setUpdateId(update_id);

            for (int pos=start; pos<records.size(); pos++)
            {
                result.addItem(records.get(pos));
            }

            return result;
        }


        public void getRemoteResult(int count)
            // start == num_gotten
        {
            // get the sub_items from the remote server

            stringHash args = new stringHash();
            args.put("InstanceID","0");
            args.put("ObjectID",getId());
            args.put("StartingIndex",Integer.toString(records.size()));
            args.put("RequestedCount",Integer.toString(count));
            args.put("Filter","*");
            args.put("SortCriteria","");
            args.put("BrowseFlag","BrowseDirectChildren");
            // not supported: "BrowseMetadata"

            Document doc = doAction(Service.serviceType.ContentDirectory,"Browse",args);
            if (doc == null)
                return;

            // parse library result contents
            // num_found and update_id always set to most recent search

            Element doc_ele = doc.getDocumentElement();
            String num_returned_str = Utils.getTagValue(doc_ele,"NumberReturned");
            int num_returned = Utils.parseInt(num_returned_str);
            num_found = Utils.parseInt(Utils.getTagValue(doc_ele,"TotalMatches"));
            update_id = Utils.parseInt(Utils.getTagValue(doc_ele,"UpdateID"));
            String didl = Utils.getTagValue(doc_ele,"Result");

            if (num_returned_str.isEmpty())
            {
                Utils.warning(0,1,"BrowseResult: NumberReturned is empty");
            }
            else if (num_returned == 0)
            {
                Utils.warning(0,1,"BrowseResult: NumberReturned is zero");
            }
            else if (didl.isEmpty())
            {
                Utils.warning(0,0,"BrowseResult: Didl is unexpectedly empty");
            }
            else
            {
                parseResultDidl(didl);
            }
        }


        private void parseResultDidl(String didl)
        {
            Document result_doc = null;
            ByteArrayInputStream stream = new ByteArrayInputStream(/*clean_*/didl.getBytes());
            try
            {
                DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                result_doc = builder.parse(stream);
            }
            catch (Exception e)
            {
                Utils.warning(0,0,"BrowseResult: Could not parse didl");
                return;
            }

            // Parse the result into folder/track subitems
            // By the way, this is a duplicated, and somewhat better
            // version of the Track(uri,didl) and unimplemented
            // Folder(uri,didl) code ...

            Element result_ele = result_doc.getDocumentElement();
            Node node = result_ele.getFirstChild();
            while (node != null)
            {
                Element node_ele = (Element) node;
                objectHash params = new objectHash();
                String node_class = Utils.getTagValue(node_ele,"upnp:class");
                boolean is_music = node_class.startsWith("object.item.audioItem");
                boolean is_track = node_class.startsWith("object.item.audioItem");  //.musicTrack");
                    // for now, see what happens creating a track
                    // from an arbitrary audioItem

                boolean is_album = node_class.startsWith("object.container.album.musicAlbum");
                    // object.container
                    // object.container.storageContainer
                    // object.container.album.musicAlbum
                    // object.item.audioItem
                    // object.item.audioItem.musicTrack

                // following more or less the same in my database for tracks and folders

                String item_id = getNodeAttr(node,"id");
                String parent_id = getNodeAttr(node,"parentID");
                String title = Utils.getTagValue(node_ele,"dc:title");
                String art_uri = Utils.getTagValue(node_ele,"upnp:albumArtURI");
                String artist = Utils.getTagValue(node_ele,"upnp:artist");
                String album_artist = Utils.getTagValue(node_ele,"upnp:albumArtist");
                String genre = Utils.getTagValue(node_ele,"upnp:genre");
                String year_str = Utils.getTagValue(node_ele,"dc:date");

                if (artist.isEmpty())
                    artist = album_artist;
                if (album_artist.isEmpty())
                    album_artist = artist;

                params.put("title",title);
                params.put("id",item_id);
                params.put("parent_id",parent_id);
                params.put("artist",artist);
                params.put("album_artist",album_artist);
                params.put("art_uri",art_uri);
                params.put("genre",genre);
                params.put("year_str",year_str);

                // retroactively change any container with
                // music_items into an "albums".

                if (is_music)
                    put("dirtype","album");

                // but only create tracks given the proper SSDP type
                // add additional fields for Tracks
                // and put them in the result AND the global hash

                if (is_track)
                {
                    params.put("album_title",Utils.getTagValue(node_ele,"upnp:album"));
                    params.put("tracknum",Utils.getTagValue(node_ele,"upnp:originalTrackNumber"));

                    // get size, duration, uri and "type" from the <res> element

                    Element res_ele = Utils.getTagElement(node_ele,"res");
                    if (res_ele == null)
                    {
                        Utils.warning(0,0,"Could not get <res> element for track " + title);
                    }
                    else
                    {
                        String path =res_ele.getTextContent();
                        int size = Utils.parseInt(getNodeAttr(res_ele,"size"));
                        int duration = Utils.stringToDuration(getNodeAttr(res_ele,"duration"));
                        String protocol = getNodeAttr(res_ele,"protocolInfo");
                        String mime_type = Utils.extract_re("^.*?:.*?:(.*?):",protocol);

                        if (path.isEmpty()) Utils.warning(0,0,"No path (uri) for track " + title);
                        if (size == 0) Utils.warning(0,0,"No size for track " + title);
                        if (duration == 0) Utils.warning(0,0,"No duration for track " + title);
                        if (protocol.isEmpty()) Utils.warning(0,0,"No protocol for track " + title);

                        params.put("path",path);
                        params.put("size",size);
                        params.put("duration",duration);
                        params.put("type",Track.extractType(protocol));
                    }

                    Track add_track = new Track(params);
                    records.add(add_track);
                    tracks.put(item_id,add_track);
                }

                // add Folder to result AND global hash

                else
                {
                    params.put("dirtype",is_album ? "album" : "folder");
                    FolderPlus add_folder = new FolderPlus(params);
                    records.add(add_folder);
                    folders.put(item_id,add_folder);
                }

                // next record

                node = node.getNextSibling();

            }   // for each node
        }   // parseResultDidl()


        private String getNodeAttr(Node node,String name)
        {
            NamedNodeMap attrs = node.getAttributes();
            Node found = attrs.getNamedItem(name);
            if (found != null)
                return found.getNodeValue();
            return "";
        }
    }


}   // class prh.device.MediaServer
