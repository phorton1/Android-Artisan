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
        // must be shorter than wait loop in aLibrary adapter
        // wait loop for a fetcher to stop when we have a
        // new request to physically fetch records, with
        // a timeout.
        //
        // The number *could* supercede the previous number,
        // or the records could already be gotten at end of
        // the call. This sleep can happen on the UI thread.
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
        libraryBrowseResult result;
        Utils.log(dbg_ms,0,"MediaServer.getSubItems(" + id + "," + start + "," + count + ")");

        if (folder == null)
        {
            Utils.error("Unexpected forward reference to folder(" + id + " before it was gotten as a sub_item of it's parent folder");
            result = new libraryBrowseResult();
        }

        else    // stepping up the hierarchy
        {
            if (current_fetcher_folder != null &&
                current_fetcher_folder != folder)
            {
                Utils.log(dbg_ms,0,"folder changed from (" + current_fetcher_folder.getTitle() + ") to (" + folder.getTitle() + ") in getSubItems()");
                Utils.log(dbg_ms,1,"STOPPING OLD FETCHER");
                current_fetcher_folder.getFetcher().stop();
            }
            current_fetcher_folder = folder;
            result = folder.getBrowseResult(start,count,meta_data);
        }
        return result;
    }


    @Override
    public void setCurrentFolder(Folder folder)
        // backing down the stack
    {
        Utils.log(dbg_fetcher,0,"MediaServer.setCurrentFolder("  + folder.getTitle() + ")");
        if (current_fetcher_folder != null)
            current_fetcher_folder.getFetcher().stop();
        current_fetcher_folder = (FolderPlus) folder;
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
            // count==0 is a special flag meaning to get as many as possible.
            // if count==0 and records==null then the first INITIAL_FETCH_NUM records
            //    are gotten asynchronously and thread is fired off to fetch the rest.
            // if count != 0, then after the current fetch thread, if any, completes
            //    the remainder are gotten synchonously.
        {
            // records = new recordList();
            // this method can result in at one call to getRemoteItems()

            Utils.log(dbg_fp,1,"FolderPlus.getBrowseResult(" + start + "," + count + ")");

            if (meta_data)
            {
                Utils.error("Does not support browse with metadata");
                return null;
            }

            // if the start==0, and count==0, then the request is effectively
            // for the first FETCH_INITIAL_GET records. If records != null,
            // this has already been done, and so we return whatever's there,
            // by falling thru

            if (records == null || start !=- 0 || count != 0)
            {
                // if count==0, then the request is for any available
                // items from the current refresh loop, starting at start,
                // so we fall thru ...

                if (records == null || count != 0)
                {
                    int use_start = records == null ? 0 : records.size();
                    int use_count = count == 0 ? 0 : start + count - use_start;

                    // finally, we optimize out fetches outside of the current
                    // bounds of getNumElements() if we've already got that

                    if (records != null && use_start + use_count > getNumElements())
                    {
                        use_count = getNumElements() - use_start;
                        if (use_count < 0)
                            use_count = 0;
                   }

                    if (records == null || use_count > 0)
                    {
                        Utils.log(dbg_fp,1,"FolderPlus.getBrowseResult() calling fetchItems(" + use_start + "," + use_count + ")");
                        fetchItems(use_start,use_count);
                        Utils.log(dbg_fp+1,1,"back from FolderPlus.getBrowseResult() fetchItems(" + use_start + "," + use_count + ")");
                    }
                }
            }

            libraryBrowseResult result = new libraryBrowseResult();
            result.setTotalFound(num_found);
            result.setUpdateId(update_id);

            int use_count = records == null ? 0 : records.size();
            for (int pos=start; pos<use_count; pos++)
            {
                result.addItem(records.get(pos));
            }

            return result;
        }


        public void unused_getRemoteResult(int count)
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

        //------------------------------------------------------------------
        // THROTTLING AND PREFETCHING
        //------------------------------------------------------------------
        // Throttling and prefetching works on one folder at a time.
        // So, Libraries now have a limited concept of a current_folder,
        // to support the prefetch scheme. A call to getSubItems() implicitly
        // sets the current folder, and there is an explicit method that must
        // be called by clients when backing down the stack.
        //
        // The initial call to getSubItems() (when the "records" member array
        // is null) will start the process by fetching the first FETCH_INITIAL_NUM
        // items from the remote server synchronously, and returning them to
        // the client. It will then fire off a thread to fetch the remainding ones,
        // FETCH_NUM at a time, fleshing out the "records" list member on the
        // FolderPlus.
        //
        // The aLibrary listAdapter also keeps track of the number of subitems
        // that have been gotten by adding the num_returned method on each call.
        // In these cases (start != 0 and count==0) we return as many has have
        // been found, and the listAdapter must freeze and wait on the ui thread
        // until getSubItems() returns the record it is interested in.
        //
        // The rule that you cannot call getFolder() or getTrack() until the
        // parent sub-item has been loaded is still in effect.


        synchronized public void fetchItems(int start, int initial_count)
        {
            int num_returned = 0;
            int count = initial_count;
            Utils.log(dbg_fetcher,0,"fetchItems(" + start + "," + initial_count + ")");

            // if count==0, we set it to FETCH_INITIAL_NUM
            // otherwise we set it to 1 to see if there are
            // any already available.

            if (count == 0)
            {
                Utils.log(dbg_fetcher,1,"fetchItems() setting count to FETCH_INITIAL_NUM");
                count = FETCH_INITIAL_NUM;
            }
            else
            {
                Utils.log(dbg_fetcher,1,"fetchItems() using count=" + count);
                //count = 1;
            }

            // if records == null, this is the first fetch
            // so we perform it synchronously, and return the
            // number gotten.
            //
            // otherwise, we see if the request has already
            // been satisfied, and if so, return as many
            // records as possible.

            if (records == null)
            {
                // assert fetcher = null;
                // assert start==0
                // start the fetcher loop if any remaining

                records = new recordList();
                fetcher = new Fetcher();
                if (fetcher.synchGetItems(start,count) &&
                    records.size() < getNumElements())
                {
                    Utils.log(dbg_fetcher,1,"fetchItems() calling fetcher.start()");
                   fetcher.start();
                }

                Utils.log(dbg_fetcher,1,"fetchItems() returning after initial get");
                return;
            }
            else  if (start + count <= records.size())
            {
                Utils.log(dbg_fetcher,1,"fetchItems() returning after getting from cache");
                return;
            }

            // the request has not been satisfied. if there is
            // a fetch in progress, wait for it to finish.
            // if it doesn't get back in time, return any
            // available records.

            Utils.log(dbg_fetcher,1,"fetchItems(" + start + "," + count + ")  has " + records.size() + " of " + getNumElements() + " records");
            Utils.log(dbg_fetcher,2,"so we are STOPPING THE FETCHER to check the next results");

            fetcher.stop();
            int sleep_count = 0;
            while (sleep_count < SLEEP_FETCH_RETRIES && fetcher.getRunning())
            {
                sleep_count++;
                if ((sleep_count % 10)==0)
                    Utils.log(dbg_fetcher,0,"fetchItems() waiting for fetcher to finish");
                Utils.sleep(SLEEP_FETCH_MILLIS);
            }
            if (sleep_count == SLEEP_FETCH_RETRIES)
            {
                Utils.warning(0,2,"Timed out waiting for fetcher");
                Utils.log(dbg_fetcher,1,"fetchItems() returning due to time_out");
                return;
            }

            // assert fetcher != null
            // otherwise, we already know the total_returned
            // if it is a request outside of the bounds, return 0

            if (start >= getNumElements())
            {
                Utils.warning(0,2,"fetchItems() returning due out of bounds start=" + start + "(there are only " + getNumElements() + " elements)  .. implicitly stopping loop");
                return;
            }

            // constrain count to numElements
            // if it ends up as zero here, return 0

            if (start + count > getNumElements())
            {
                count = getNumElements() - start;
                if (count <= 0)
                    count = 0;
                {
                    Utils.warning(0,2,"fetchItems() returning due to out of bounds(2) .. implicitly stopping loop");
                    return;
                }
            }


            // THE FETCHER IS STOPPED
            // The request could have been satisfied by the stopped fetcher.
            // Double check here, and if count != 0, fire off an asynch request for the
            // remainder.

            if (start + count > records.size())
            {
                int use_start = records.size();
                int use_count = count == 0 ? count : start + count - use_start;
                if (use_start < 0)
                    use_start = 0;
                Utils.warning(0,2,"fetchItems() NEEDS MORE RECORDS .. callin synchGetItems()");
                fetcher.synchGetItems(use_start,use_count);
                if (records.size() < use_start + use_start)
                {
                    Utils.warning(0,2,"Call to synchGetItems(" + start + "," + count + ") did not return all the records. records.size=" + records.size());
                    Utils.warning(dbg_fetcher,0,"fetchItems() returning ... and implicitly STOPPING FETCHER LOOP");
                    return;
                }
            }


            // assert records.size() >= start + count
            // restart the fetcher if any remaining


            boolean ok = true;
            count = records.size() - start;
            if (count <= 0)
                 count = 0;

            if (ok && records.size() < getNumElements())
            {
                Utils.log(dbg_fetcher,1,"fetchItems() RESTARTING fetcher loop (calling fetcher.start())");
                fetcher.start();
            }

            Utils.log(dbg_fetcher,1,"fetchItems() returning");
        }



        private class Fetcher implements Runnable
        {
            int state = 0;
                // 0 = idle
                // 1 = running
                // 2 = stopping

            public boolean getRunning() { return state > 0; }


            public void start()
            {
                Utils.log(dbg_fetcher,1,"FETCHER.start(" + getTitle() + ") called");
                if (getRunning())
                {
                    Utils.error("Fetcher is already running");
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


            public void stop()
                // stop it if it's running
                // client must wait for current fetch to finish
            {
                Utils.log(dbg_fetcher,1,"FETCHER.stop(" + getTitle() + ") called");
                if (state == 1)
                    state = 2;
            }


            public void run()
            {
                Utils.log(dbg_fetcher,2,"FETCHER.run(" + getTitle() + ") started num_records=" + records.size() + "  num_elements=" + getNumElements());
                while (state == 1 &&
                       records.size() < getNumElements())
                {
                    int start = records.size();
                    int count = getNumElements() - start;
                    if (count > FETCH_NUM)
                        count = FETCH_NUM;

                    if (!synchGetItems(start,count))
                    {
                        state = 0;
                    }

                    // give SOME time to the UI

                    else if (state == 1 && SLEEP_FETCH_INTERNAL_MILLIS > 0)
                    {
                        Utils.sleep(SLEEP_FETCH_INTERNAL_MILLIS);
                    }
                }
                Utils.log(dbg_fetcher,2,"FETCHER.run(" + getTitle() + ") finished");
                state = 0;
            }



            public boolean synchGetItems(int start, int count)
            {
                // get the sub_items from the remote server

                Utils.log(dbg_fetcher,3,"syncGetItems(" + start + "," + count + ")");

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

                return parseResultDidl(didl);

            }   // synchGetItems()


        }   // class Fetcher
    }   // class FolderPlus
}   // class prh.device.MediaServer
