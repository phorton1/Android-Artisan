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
import prh.utils.Fetcher;
import prh.types.libraryBrowseResult;
import prh.types.objectHash;
import prh.types.recordList;
import prh.utils.ImageLoader;
import prh.utils.Utils;
import prh.types.stringHash;

public class MediaServer extends Device implements
    Library
{
    private static final int dbg_ms = 1;    // mediaServer
    private static final int dbg_fp = 1;    // folder plus

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
    //
    // Supports Throttling and Prefetching to optimize UI behavior.

    
    private class folderHash extends HashMap<String,FolderPlus> {}
    private class trackHash extends HashMap<String,Track> {}
    folderHash folders = null;
    trackHash tracks = null;

    private FolderPlus current_fetcher_folder = null;


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
        Utils.log(dbg_ms,1,"new MediaServer(" + ssdp_device.getFriendlyName() + "," + ssdp_device.getDeviceType() + "," + ssdp_device.getDeviceUrl());
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
        libraryBrowseResult test = getSubItems("0",0,0,false);
        if (test.getTotalFound() == 0 ||
            test.getNumReturned() == 0)
        {
            Utils.error("Could not start MediaServer(" + getFriendlyName() + ")");
            return false;
        }

        return true;
    }


    @Override public void stop(boolean wait_for_stop)
    {
        if (tracks != null)
            tracks.clear();
        tracks = null;

        if (folders != null)
        {
            for (FolderPlus folder : folders.values())
            {
                Fetcher fetcher = folder.getFetcher();
                if (fetcher != null)
                    fetcher.stop(true,wait_for_stop);
            }
        }
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
        // it is illegal to call getFolder() for a
        // parent subitem that has not yet been loaded.
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
        // count==0 is a special flag meaning to get as many as possible.
        // if start==0 and records==null then the first INITIAL_FETCH_NUM records
        // otherwise, returns the number already fetched
    {
        FolderPlus folder = getFolder(id);

        if (folder == null)
        {
            Utils.error("Unexpected forward reference to folder(" + id + " before it was gotten as a sub_item of it's parent folder");
            return new libraryBrowseResult();
        }

        Utils.log(dbg_ms,0,"MediaServer.getSubItems(" + start + "," + count + ") for " + folder.getTitle());

        if (folder.getType().equals("unknown"))
        {
            Utils.error("Cannot get subItems of unknown folder type for " + folder.getTitle());
            return new libraryBrowseResult();
        }
        
        // stepping up the hierarchy

        if (current_fetcher_folder != null &&
            current_fetcher_folder != folder)
        {
            Utils.log(dbg_ms,0,"folder changed from (" + current_fetcher_folder.getTitle() + ") to (" + folder.getTitle() + ") in getSubItems()");
            Utils.log(dbg_ms,1,"Pausing OLD FETCHER");
            current_fetcher_folder.getFetcher().pause();
        }
        
        current_fetcher_folder = folder;
        return folder.getBrowseResult(start,count,meta_data);
    }


    
    @Override
    public void setCurrentFolder(Folder folder)
        // Called by aLibrary when going up or down the stack
    {
        Utils.log(dbg_fp,0,"MediaServer.setCurrentFolder("  + folder.getTitle() + ")");
        if (current_fetcher_folder != null)
            current_fetcher_folder.getFetcher().stop(false,false);

        current_fetcher_folder = (FolderPlus) folder;
        if (current_fetcher_folder.getFetcher() != null)
            current_fetcher_folder.getFetcher().start();
    }



    //-------------------------------------------------
    // Implementation
    //-------------------------------------------------
    // Derived in memory folder that keeps it's children.
    // In this case the 'client" of the fetcher is artisan
    // which will pass the the client calls down to aLibrary
    // a

    public class FolderPlus extends Folder implements Fetcher.FetcherSource
        // it is expected that the records will be requested in order,
        // that is, a search will never start off with start>0
    {
        int num_found = 0;
        int update_id;

        private Fetcher fetcher = null;
        public Fetcher getFetcher() { return fetcher; }
        public recordList getRecords() { return fetcher.getRecords(); }


        //---------------------------------------
        // FolderPlus API
        //---------------------------------------

        public FolderPlus(objectHash params)
        {
            super(params);
        }


        @Override
        public int getNumElements()
        {
            return num_found;
        }


        // Fetcher configuration constants

        private final static int FETCH_NUM = 80;
            // number gotten on fetch thread per hit
        private final static int FETCH_INITIAL_NUM = 20;
            // number gotten synchronously for initial hit


        public libraryBrowseResult getBrowseResult(int start,int count,boolean meta_data)
            // count==0 is a special flag meaning to use the fetcher scheme.
            // and we actually never expect a call without it ...
        {
            Utils.log(dbg_fp,1,"FolderPlus.getBrowseResult(" + start + "," + count + ")");
            libraryBrowseResult result = new libraryBrowseResult();

            if (meta_data)
            {
                Utils.error("Does not support browse with metadata");
                return result;
            }
            
            // in this case, we consider it bad call and just return whatever's available 
            // except, by chance, on the first call
            
            if (count != 0)
            {
                Utils.warning(0,2,"FolderPlus.getBrowseResult(" + getTitle() + ") called with a non-zero count: " + count + " using FETCH_INITIAL_NUM=" + FETCH_INITIAL_NUM + " instead");

            }
            
            // otherwise, do the only initial get if records==null
            // in MediaServer, the "client" is artisan itself, who
            // passes the calls onto aLibrary

            if (fetcher == null)
            {
                fetcher = new Fetcher(
                    artisan,
                    this,
                    artisan,
                    FETCH_INITIAL_NUM,
                    FETCH_NUM,
                    "MediaServer::" + getTitle());

                // calls back to our getFetchRecords() method
                // which sets our member variables num_found, update_id, etc

                Utils.log(dbg_fp,1,"FolderPlus.getBrowseResult(" + getTitle() + ") calling fetcher.start()");
                if (!fetcher.start())
                {
                    Utils.error("fetcher.start() return false in FolderPlus.getBrowseResult(" + getTitle() + ")");
                    return result;
                }
            }

            // return whatever's there
            // flesh out the return result
            // the result was empty, but we still have to
            // add the records one at a time, to work with
            // the start,count scheme, since, there is no
            // get_splice() method on an array list

            recordList records_ref = fetcher.getRecordsRef();
            int num_records = records_ref.size();
            int num_returned = num_records - start;
            if (num_returned < 0) num_returned = 0;

            result.setTotalFound(num_found);
            result.setNumReturned(num_returned);
            result.setUpdateId(update_id);

            if (num_returned > 0)
                result.addAll(start,records_ref.subList(start,num_records));

            // finished
            
            Utils.log(dbg_fp,1,"FolderPlus.getBrowseResult(" + getTitle() + ") returning " + result.getNumReturned() + " items");
            return result;
            
        }   // getBrowseResult()
        


        //------------------------------------------
        // private implementation parseResultDidl()
        //------------------------------------------

        private boolean parseResultDidl(String didl)
        {
            Document result_doc = null;
            Utils.log(dbg_fp+1,5,"FolderPlus.parseDidl() didl=" + didl);

            if (fetcher.stop_fetch())
                return true;

            ByteArrayInputStream stream = new ByteArrayInputStream(/*clean_*/didl.getBytes());
            try
            {
                DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                result_doc = builder.parse(stream);
            }
            catch (Exception e)
            {
                Utils.warning(0,6,"BrowseResult: Could not parse didl");
                return false;
            }

            if (fetcher.stop_fetch())
                return true;

            // Parse the result into folder/track subitems
            // By the way, this is a duplicated, and somewhat better
            // version of the Track(uri,didl) and unimplemented
            // Folder(uri,didl) code ...

            Element result_ele = result_doc.getDocumentElement();
            Node node = result_ele.getFirstChild();
            while (node != null)
            {
                if (fetcher.stop_fetch())
                    return true;

                Element node_ele = (Element) node;
                objectHash params = new objectHash();
                String node_class = Utils.getTagValue(node_ele,"upnp:class");
                boolean is_music = node_class.startsWith("object.item.audioItem");
                boolean is_track = node_class.startsWith("object.item.audioItem");  //.musicTrack");
                    // for now, see what happens creating a track
                    // from an arbitrary audioItem

                boolean is_container = node_class.startsWith("object.container");
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
                // music_items into an "albums". Don't know
                // what happens with unknown types

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

                    if (fetcher.stop_fetch())
                        return true;

                    Track add_track = new Track(params);
                    recordList records_ref = fetcher.getRecordsRef();
                    records_ref.add(add_track);
                    tracks.put(item_id,add_track);
                }

                // add Folder to result AND global hash

                else
                {
                    if (fetcher.stop_fetch())
                        return true;

                    String dirtype =
                        is_album ? "album" :
                        is_container ? "folder" :
                        "unknown";

                    if (dirtype.equals("unknown"))
                        Utils.warning(0,0,"Unknown folder type(" + node_class + ") for " + title);

                    params.put("dirtype",dirtype);

                    FolderPlus add_folder = new FolderPlus(params);
                    recordList records_ref = fetcher.getRecordsRef();
                    records_ref.add(add_folder);
                    folders.put(item_id,add_folder);
                }


                // start pre-fetching the image

                if (!art_uri.isEmpty())
                    ImageLoader.loadImage(artisan,null,art_uri);

                // next record

                node = node.getNextSibling();

            }   // for each node

            return true;

        }   // parseResultDidl()


        private String getNodeAttr(Node node,String name)
        {
            NamedNodeMap attrs = node.getAttributes();
            Node found = attrs.getNamedItem(name);
            if (found != null)
                return found.getNodeValue();
            return "";
        }



        //---------------------------------------
        // FetcherSource interface
        //---------------------------------------

        @Override public boolean isDynamicFetcherSource()
        {
            return false;
        }

        @Override public Fetcher.fetchResult getFetchRecords(Fetcher fetcher, boolean initial_fetch, int num)
        {
            // get the sub_items from the remote server

            int current_num = fetcher.getNumRecords();
            Utils.log(dbg_fp,3,"FolderPlus(" + getTitle() + ").getFetchRecords(" + initial_fetch + "," + num + ")  current_num=" + current_num + " getNumElements()=" + getNumElements());
            if (!initial_fetch && current_num >= getNumElements())
            {
                Utils.log(dbg_fp,3,"FETCHER.DONE at top of FolderPlus(" + getTitle() + ").getFetchRecords( " + num + ")  current_num=" + current_num);
                return Fetcher.fetchResult.FETCH_DONE;
            }


            stringHash args = new stringHash();
            args.put("InstanceID","0");
            args.put("ObjectID",getId());
            args.put("StartingIndex",Integer.toString(current_num));
            args.put("RequestedCount",Integer.toString(num));
            args.put("Filter","*");
            args.put("SortCriteria","");
            args.put("BrowseFlag","BrowseDirectChildren");
            // not supported: "BrowseMetadata"

            Document doc = doAction(Service.serviceType.ContentDirectory,"Browse",args);
            if (doc == null)
                return Fetcher.fetchResult.FETCH_ERROR;

            // if we are being forced to stop, don't go any further
            // otherwise, for a normal stop, we keep the results

            if (fetcher.stop_fetch())
                return Fetcher.fetchResult.FETCH_NONE;


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
                Utils.warning(0,4,"BrowseResult: NumberReturned is empty");
                return Fetcher.fetchResult.FETCH_ERROR;
            }
            else if (num_returned == 0)
            {
                Utils.warning(0,4,"BrowseResult: NumberReturned is zero");
                return Fetcher.fetchResult.FETCH_NONE;
            }
            else if (didl.isEmpty())
            {
                Utils.warning(0,4,"BrowseResult: Didl is unexpectedly empty");
                return Fetcher.fetchResult.FETCH_ERROR;
            }
            else if (!parseResultDidl(didl))
            {
                return Fetcher.fetchResult.FETCH_ERROR;
            }

            int new_num = fetcher.getNumRecords();
            Fetcher.fetchResult result =
                new_num >= getNumElements() ? Fetcher.fetchResult.FETCH_DONE :
                new_num > current_num ? Fetcher.fetchResult.FETCH_RECS :
                Fetcher.fetchResult.FETCH_NONE;
            Utils.log(dbg_fp,3,"FolderPlus(" + getTitle() + ").getFetchRecords(" + num + "/" + getNumElements() +") returning " + result + " with list containing " + new_num + " records");
            return result;

        }   // MediaServer.FolderPlus.getFetchRecords()


    }   // class FolderPlus
}   // class prh.device.MediaServer


