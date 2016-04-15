package prh.device;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.ByteArrayInputStream;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import prh.artisan.Artisan;
import prh.artisan.Folder;
import prh.base.Library;
import prh.artisan.Track;
import prh.artisan.Fetcher;
import prh.types.objectHash;
import prh.utils.ImageLoader;
import prh.utils.Utils;
import prh.types.stringHash;


public class MediaServer extends Device
    implements Library
{
    private static final int dbg_ms = 1;    // mediaServer
    private static final int dbg_fp = 0;    // folder plus

    // constants

    private static int NUM_PER_FETCH = 50;
    private static int NUM_INITIAL_FETCH = 16;
    private static int NUM_FETCH_ROOT = 999999;

    // types

    private class folderHash extends HashMap<String,FolderPlus> {}
    private class trackHash extends HashMap<String,Track> {}

    // member variables

    private folderHash folders = null;
    private trackHash tracks = null;
    private FolderPlus root_folder = null;


    //----------------------------------------
    // Device ctors
    //----------------------------------------

    public MediaServer(Artisan artisan)
    {
        super(artisan);
    }


    public MediaServer(Artisan artisan, SSDPSearchDevice ssdp_device)
    {
        super(artisan,ssdp_device);
        Utils.log(dbg_ms,1,"new MediaServer(" + ssdp_device.getFriendlyName() + "," + ssdp_device.getDeviceType() + "," + ssdp_device.getDeviceUrl());
    }


    //----------------------------------------
    // Library Interface
    //----------------------------------------

    @Override public String getLibraryName()
    {
        return getFriendlyName();
    }

    @Override public Folder getRootFolder()
    {
        return root_folder;
    }



    @Override public boolean startLibrary()
    {
        Utils.log(dbg_ms,0,"MediaServer.startLibrary() called");
        tracks = new trackHash();
        folders = new folderHash();
        Folder folder = new Folder();
        folder.setTitle("root");
        folder.setId("0");
        root_folder = new FolderPlus(folder);
        if (!root_folder.getFetcher().start())
        {
            Utils.error("Could not start MediaServer(" + getFriendlyName() + ")");
            return false;
        }
        folder.put(root_folder.getId(),root_folder);
        Utils.log(dbg_ms,0,"MediaServer.startLibrary() returning true");
        return true;
    }


    @Override public void stopLibrary(boolean wait_for_stop)
    {
        Utils.log(dbg_ms,0,"MediaServer.stopLibrary() called");

        if (tracks != null)
            tracks.clear();
        tracks = null;

        // stop any fetchers on any folders

        if (folders != null)
        {
            for (FolderPlus folder : folders.values())
            {
                Fetcher fetcher = folder.getFetcher();
                if (fetcher != null)
                    fetcher.stop(true,wait_for_stop);
            }
            folders.clear();
        }
        folders = null;
        Utils.log(dbg_ms,0,"MediaServer.stopLibrary() finished");
    }


    //-------------------------------------------------
    // Implementation
    //-------------------------------------------------
    // Derived in memory folder that keeps it's children
    // in a fetcher and is also a fetcherSource ...

    public class FolderPlus extends Folder implements Fetcher.FetcherSource
        // it is expected that the records will be requested in order,
        // that is, a search will never start off with start>0
    {
        private int update_id;
        private Fetcher fetcher;

        // simple accessors

        public void setUpdateId(int i) { update_id = i; }
        @Override public Fetcher getFetcher() { return fetcher; }

        //---------------------------------------
        // FolderPlus API
        //---------------------------------------

        public FolderPlus(Folder folder)
        {
            super(folder);
            fetcher = new Fetcher(
                artisan,
                this,
                null,
                getId().equals("0") ?
                    NUM_FETCH_ROOT :
                    NUM_INITIAL_FETCH,
                NUM_PER_FETCH,
                "MediaServer(" + getFriendlyName() + "::" + getTitle() + ")");
        }

        // Fetcher configuration constants

        private final static int FETCH_NUM = 80;
            // number gotten on fetch thread per hit
        private final static int FETCH_INITIAL_NUM = 20;
            // number gotten synchronously for initial hit


        //---------------------------------------
        // FetcherSource interface
        //---------------------------------------

        @Override public Fetcher.fetchResult getFetchRecords(Fetcher fetcher, boolean initial_fetch, int num)
        {
            if (this.getFetcher() == null)
            {
                Utils.error("FolderPlus(" + getTitle() + ").getFetchRecords(" + initial_fetch + "," + num + ") null fetcher  .. called with fetcher=" + fetcher.getTitle());
                return Fetcher.fetchResult.FETCH_ERROR;
            }
            if (this.getFetcher() != fetcher)
            {
                Utils.error("TWO DIFFERENT FETCHERS ON THE SAME FOLDER: " + this.getFetcher().getTitle() + " new=" + fetcher.getTitle());
                return Fetcher.fetchResult.FETCH_ERROR;
            }


            // get the sub_items from the remote server

            int current_num = fetcher.getNumRecords();
            Utils.log(dbg_fp,0,"FolderPlus(" + getTitle() + ").getFetchRecords(" + initial_fetch + "," + num + ")  current_num=" + current_num + " getNumElements()=" + getNumElements());

            if (fetcher.getState() == Fetcher.fetcherState.FETCHER_DONE)
            {
                Utils.log(dbg_fp,1,"fetcher Already DONE");
                return Fetcher.fetchResult.FETCH_DONE;
            }
            if (!initial_fetch && current_num >= getNumElements())
            {
                Utils.log(dbg_fp,0,"FETCHER.DONE at top of FolderPlus(" + getTitle() + ").getFetchRecords( " + num + ")  current_num=" + current_num);
                return Fetcher.fetchResult.FETCH_DONE;
            }

            int rslt = getRecordsAction(this, current_num, num);
            if (rslt < 0)
                return Fetcher.fetchResult.FETCH_ERROR;

            int new_num = fetcher.getNumRecords();
            Fetcher.fetchResult result =
                new_num >= getNumElements() ? Fetcher.fetchResult.FETCH_DONE :
                new_num > current_num ? Fetcher.fetchResult.FETCH_RECS :
                Fetcher.fetchResult.FETCH_NONE;
            Utils.log(dbg_fp,0,"FolderPlus(" + getTitle() + ").getFetchRecords(" + num + "/" + getNumElements() +") returning " + result + " with list containing " + new_num + " records");
            return result;

        }   // MediaServer.FolderPlus.getFetchRecords()


    }   // class FolderPlus


    //-------------------------------------------------
    // HIT THE REMOTE
    //-------------------------------------------------

    private int getRecordsAction(FolderPlus folder, int start, int count)
    {
        stringHash args = new stringHash();
        args.put("InstanceID","0");
        args.put("ObjectID",folder.getId());
        args.put("StartingIndex",Integer.toString(start));
        args.put("RequestedCount",Integer.toString(count));
        args.put("Filter","*");
        args.put("SortCriteria","");
        args.put("BrowseFlag","BrowseDirectChildren");
        // not supported: "BrowseMetadata"

        // DO THE ACTION

        Utils.log(dbg_fp,0,"getRecordsAction(" + start + "," + count + "," + folder.getTitle() +") called");

        Document doc = doAction(Service.serviceType.ContentDirectory,"Browse",args);
        if (doc == null)
        {
            deviceFailure();
            return -1;
        }

        // if we are being forced to stop, don't go any further
        // otherwise, for a normal stop, we keep the results

        if (folder.getFetcher().stop_fetch())
            return 0;

        // parse library result contents
        // num_found and update_id always set to most recent search

        Element doc_ele = doc.getDocumentElement();
        folder.setNumElements(Utils.parseInt(Utils.getTagValue(doc_ele,"TotalMatches")));
        folder.setUpdateId(Utils.parseInt(Utils.getTagValue(doc_ele,"UpdateID")));
        String num_returned_str = Utils.getTagValue(doc_ele,"NumberReturned");
        int num_returned = Utils.parseInt(num_returned_str);


        if (num_returned_str.isEmpty())
        {
            deviceFailure();
            Utils.warning(0,4,"BrowseResult: NumberReturned is empty");
            return -1;
        }
        else if (num_returned == 0)
        {
            Utils.warning(0,4,"BrowseResult: NumberReturned is zero");
        }

        String didl = Utils.getTagValue(doc_ele,"Result");
        folder.setNumElements(Utils.parseInt(Utils.getTagValue(doc_ele,"TotalMatches")));
        folder.setUpdateId(Utils.parseInt(Utils.getTagValue(doc_ele,"UpdateID")));

        if (num_returned > 0)
        {
            if (didl.isEmpty())
            {
                deviceFailure();
                Utils.warning(0,4,"BrowseResult: Didl is unexpectedly empty");
                return -1;
            }
            else if (!parseResultDidl(folder,didl))
            {
                deviceFailure();
                return -1;
            }
        }

        deviceSuccess();
        Utils.log(dbg_fp,0,"getRecordsAction(" + start + "," + count + "," + folder.getTitle() + ") returning num_returned=" + num_returned + " total=" + folder.getNumElements());
        return num_returned;
    }



    private boolean parseResultDidl(FolderPlus folder, String didl)
    {
        Document result_doc = null;
        Utils.log(dbg_fp + 1,5,"FolderPlus.parseDidl() didl=" + didl);

        if (folder.getFetcher().stop_fetch())
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

        if (folder.getFetcher().stop_fetch())
            return true;

        // Parse the result into folder/track subitems
        // By the way, this is a duplicated, and somewhat better
        // version of the Track(uri,didl) and unimplemented
        // Folder(uri,didl) code ...

        Element result_ele = result_doc.getDocumentElement();
        Node node = result_ele.getFirstChild();
        while (node != null)
        {
            if (folder.getFetcher().stop_fetch())
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

            // set this folder's album type to "album" retroactivly
            // if any tracks found in it

            if (is_music)
                folder.setType("album");

            // Create the track or sub-folder and add it
            // to the FolderPlus AND the Fetcher

            String art_uri = "";
            if (is_track)
            {
                Track add_track = new Track(node);
                folder.getFetcher().getRecordsRef().add(add_track);
                tracks.put(add_track.getId(),add_track);
                art_uri = add_track.getLocalArtUri();
            }

            // add Folder to result AND global hash

            else
            {
                Folder base_folder = new Folder(node);
                base_folder.setType(
                    is_album ? "album" :
                        is_container ? "folder" :
                            "unknown");

                if (base_folder.getType().equals("unknown"))
                    Utils.warning(0,0,"Unknown folder type(" + node_class + ") for " + base_folder.getTitle());

                FolderPlus add_folder = new FolderPlus(base_folder);
                folder.getFetcher().getRecordsRef().add(add_folder);
                folders.put(add_folder.getId(),add_folder);
                art_uri = add_folder.getLocalArtUri();
            }

            // start pre-fetching the image

            //if (!art_uri.isEmpty())
            //    ImageLoader.loadImage(artisan,null,art_uri);

            // next record

            node = node.getNextSibling();

        }   // for each node

        return true;

    }   // parseResultDidl()



}   // class prh.device.MediaServer


