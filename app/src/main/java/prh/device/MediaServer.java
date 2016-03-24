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
import prh.artisan.EventHandler;
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
    private static final int dbg_ms = 0;
    private static final int dbg_fp = 0;
    private static final int dbg_fetcher = 0;

    // Fetcher configuration constants

    private final static int FETCH_NUM = 80;
        // number gotten on fetch thread per hit
    private final static int FETCH_INITIAL_NUM = 20;
        // number gotten synchronously for initial hit
    private final static int SLEEP_FETCH_RETRIES = 20;
    private final static int SLEEP_FETCH_MILLIS = 240;
        // wait loop for a fetcher to stop when we have a
        // new request to physically fetch records
    private final static int SLEEP_FETCH_INTERNAL_MILLIS = 0;
        // In the fetcher thread itself this is the delay between
        // subsequent synchronized calls ...

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
    private int num_fetchers_running = 0;


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


    @Override public void stop()
    {
        // artisan.showArtisanProgressIndicator(false);

        if (tracks != null)
            tracks.clear();
        tracks = null;

        if (folders != null)
        {
            for (FolderPlus folder : folders.values())
            {
                FolderPlus.Fetcher fetcher = folder.getFetcher();
                if (fetcher != null)
                    fetcher.stop(true);
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
            Utils.log(dbg_ms,1,"STOPPING OLD FETCHER");
            current_fetcher_folder.getFetcher().stop(false);
        }
        
        current_fetcher_folder = folder;
        return folder.getBrowseResult(start,count,meta_data);
    }


    
    @Override
    public void setCurrentFolder(Folder folder)
        // Called by aLibrary when going up or down the stack
    {
        Utils.log(dbg_fetcher,0,"MediaServer.setCurrentFolder("  + folder.getTitle() + ")");
        if (current_fetcher_folder != null)
            current_fetcher_folder.getFetcher().stop(false);
        current_fetcher_folder = (FolderPlus) folder;
        if (current_fetcher_folder.getFetcher() != null)
            current_fetcher_folder.getFetcher().start();
    }



    //-------------------------------------------------
    // Implementation
    //-------------------------------------------------
    // Derived in memory folder that keeps it's children.

    public class FolderPlus extends Folder
        // it is expected that the records will be requested in order,
        // that is, a search will never start off with start>0
    {
        int num_found = 0;
        int update_id;

        private recordList records = null;
        private Fetcher fetcher = null;
        public Fetcher getFetcher() { return fetcher; }
        public recordList getRecords() { return records; }
        private FolderPlus self;
        

        public FolderPlus(objectHash params)
        {
            super(params);
            self = this;
        }


        @Override
        public int getNumElements()
        {
            return num_found;
        }


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
            
            if (records == null)
            {
                fetcher = new Fetcher();
                records = new recordList();
                Utils.log(dbg_fetcher,1,"FolderPlus.getBrowseResult(" + getTitle() + ") calling synchGetItems(" + start + "," + count + ")");
                if (!fetcher.synchGetItems(start,FETCH_INITIAL_NUM,false))
                {
                    Utils.error("synchGetItems(" + start + "," + count + ") failed for " + getTitle());
                    return result;
                }
            }

            // return whatever's there
            // flesh out the return result

            result.setTotalFound(num_found);
            result.setUpdateId(update_id);
            int use_count = records == null ? 0 : records.size();
            for (int pos=start; pos<use_count; pos++)
            {
                result.addItem(records.get(pos));
            }
            
            // fire off the fetcher if there's more
            
            if (records.size() < getNumElements())
            {
                Utils.log(dbg_fetcher,2,"FolderPlus.getBrowseResult(" + getTitle() + ") calling STARTING_FETCHER");
                fetcher.start();
            }
            
            // finished
            
            Utils.log(dbg_fp,1,"FolderPlus.getBrowseResult(" + getTitle() + ") returning " + result.getNumReturned() + " items");
            return result;
            
        }   // getBrowseResult()
        

        //------------------------------------------------------------------
        // FETCHER CLASS
        //------------------------------------------------------------------

        private class Fetcher implements Runnable
        {
            int state = 0;
                // 0 = idle
                // 1 = running
                // 2 = stopping (keep results)
                // 3 = force_stop (dont keep result)

            public boolean getRunning() 
            { 
                return state > 0; 
            }


            public void start()
            {
                Utils.log(dbg_fetcher,1,"FETCHER.start(" + getTitle() + ") called");
                if (getRunning())
                {
                    Utils.warning(0,3,"Fetcher is already running");
                    return;
                }
                if (records.size() >= getNumElements())
                {
                    Utils.warning(0,3,"Fetcher.start() - nothing to do");
                    return;
                }

                // assert state == 0
                // change to state == 1 (running);

                state = 1;
                Thread fetcher_thread = new Thread(this);
                fetcher_thread.start();

            }


            public void stop(boolean force)
                // stop it if it's running
                // client must wait for current fetch to finish
            {
                Utils.log(dbg_fetcher,1,"FETCHER.stop(" + getTitle() + ") called");
                if (state != 0)
                    state = force ? 3 : 2;
            }


            public void run()
            {
                Utils.log(dbg_fetcher,2,"FETCHER.run(" + getTitle() + ") started num_records=" + records.size() + "  num_elements=" + getNumElements());

                num_fetchers_running++;
                artisan.showArtisanProgressIndicator(true);
                while (state == 1 &&
                       records.size() < getNumElements())
                {
                    int start = records.size();
                    int count = getNumElements() - start;
                    if (count > FETCH_NUM)
                        count = FETCH_NUM;

                    if (!synchGetItems(start,count,true))
                    {
                        state = 0;  // failure
                    }

                    // give SOME time to the UI

                    else if (state == 1 && SLEEP_FETCH_INTERNAL_MILLIS > 0)
                    {
                        Utils.sleep(SLEEP_FETCH_INTERNAL_MILLIS);
                    }
                }
                Utils.log(dbg_fetcher,2,"FETCHER.run(" + getTitle() + ") finished");
                num_fetchers_running--;
                if (num_fetchers_running == 0)
                    artisan.showArtisanProgressIndicator(false);
                state = 0;
            }


            //------------------------------------------
            // parsing
            //------------------------------------------

            private boolean parseResultDidl(String didl)
            {
                Document result_doc = null;
                Utils.log(dbg_fp+1,5,"FolderPlus.parseDidl() didl=" + didl);

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

                if (state == 3)
                    return false;

                // Parse the result into folder/track subitems
                // By the way, this is a duplicated, and somewhat better
                // version of the Track(uri,didl) and unimplemented
                // Folder(uri,didl) code ...

                Element result_ele = result_doc.getDocumentElement();
                Node node = result_ele.getFirstChild();
                while (node != null)
                {
                    if (state == 3)
                        return false;

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

                        if (state == 3)
                            return false;

                        Track add_track = new Track(params);
                        records.add(add_track);
                        tracks.put(item_id,add_track);
                    }

                    // add Folder to result AND global hash

                    else
                    {
                        if (state == 3)
                            return false;

                        String dirtype =
                            is_album ? "album" :
                            is_container ? "folder" :
                            "unknown";

                        if (dirtype.equals("unknown"))
                            Utils.warning(0,0,"Unknown folder type(" + node_class + ") for " + title);

                        params.put("dirtype",dirtype);

                        FolderPlus add_folder = new FolderPlus(params);
                        records.add(add_folder);
                        folders.put(item_id,add_folder);
                    }

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



            //------------------------------------------
            // synchGetItems()
            //------------------------------------------


            public boolean synchGetItems(int start, int count, boolean fromFetcher)
            {
                // get the sub_items from the remote server

                int cur_num_items = records.size();
                Utils.log(dbg_fetcher,3,"syncGetItems(" + start + "," + count + ") cur_num_items=" + cur_num_items +"/" + getNumElements());

                stringHash args = new stringHash();
                args.put("InstanceID","0");
                args.put("ObjectID",getId());
                args.put("StartingIndex",Integer.toString(start));
                args.put("RequestedCount",Integer.toString(count));
                args.put("Filter","*");
                args.put("SortCriteria","");
                args.put("BrowseFlag","BrowseDirectChildren");
                // not supported: "BrowseMetadata"

                Document doc = doAction(Service.serviceType.ContentDirectory,"Browse",args);
                if (doc == null)
                    return false;

                // if we are being forced to stop, don't go any further
                // otherwise, for a normal stop, we keep the results

                if (state == 3)
                    return false;

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
                    return false;
                }
                else if (num_returned == 0)
                {
                    Utils.warning(0,4,"BrowseResult: NumberReturned is zero");
                    return false;
                }
                else if (didl.isEmpty())
                {
                    Utils.warning(0,4,"BrowseResult: Didl is unexpectedly empty");
                    return false;
                }
                else if (!parseResultDidl(didl))
                    return false;


                if (state == 1 && fromFetcher && records.size() > cur_num_items)
                {
                    artisan.handleArtisanEvent(EventHandler.EVENT_ADDL_FOLDERS_AVAILABLE,self);
                }

                return true;

            }   // synchGetItems()


        }   // class Fetcher
    }   // class FolderPlus
}   // class prh.device.MediaServer


