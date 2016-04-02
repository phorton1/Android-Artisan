package prh.device.service;


import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import fi.iki.elonen.NanoHTTPD;
import prh.artisan.Artisan;
import prh.base.ArtisanEventHandler;
import prh.base.EditablePlaylist;
import prh.artisan.Fetcher;
import prh.base.PlaylistSource;
import prh.base.Renderer;
import prh.artisan.Track;
import prh.device.Device;
import prh.device.SSDPSearchService;
import prh.device.Service;
import prh.base.UpnpEventReceiver;
import prh.types.intTrackHash;
import prh.types.stringHash;
import prh.types.trackList;
import prh.utils.Base64;
import prh.utils.Utils;
import prh.utils.playlistHelper;


// Is subscribed to a remote open playlist, and presents
// that to the tempEditablePlaylist via the Playlist interface.

public class OpenPlaylist extends Service implements
    UpnpEventReceiver,
    EditablePlaylist
{
    // Playlist contents

    private int num_tracks;
    private int track_index;       // one based
    private int my_shuffle;        //  overloaded to openhome shuffle

    // OpenHome contents

    private String transport_state;
    private boolean repeat;
    private int[] id_array;
    private intTrackHash tracks_by_open_id;
    private trackList tracks_by_position;

    // state

    private boolean is_dirty;
    private int playlist_count_id;
    private int content_change_id;
    private int recs_changed_count_id;


    // Fetchable Playlist Interface
    // except setAssociatedPlaylist

    @Override public int getPlaylistCountId() { return playlist_count_id; }
    @Override public int getContentChangeId() { return content_change_id; }

    // Basic Playlist Interface, except getTrack()

    @Override public PlaylistSource getSource() { return null; }

    @Override public boolean startPlaylist() { return true; }
    @Override public void stopPlaylist(boolean wait_for_stop)  {}

    @Override public String getName()         { return ""; }
    @Override public int getPlaylistNum()     { return 0; }
    @Override public int getNumTracks()       { return num_tracks; }
    @Override public int getCurrentIndex()    { return track_index; }
    @Override public Track getCurrentTrack()  { return getTrack(track_index); }
    @Override public int getMyShuffle()       { return my_shuffle; }
    @Override public String getQuery()        { return ""; }

    @Override public void saveIndex(int index) {}
    @Override public boolean isDirty() { return is_dirty; }
    @Override public void setDirty(boolean b) { is_dirty = b; }

    @Override public int getNumAvailableTracks()
    {
        return tracks_by_position.size();
    }

    //----------------------------------
    // Sevice ctors
    //----------------------------------

    public OpenPlaylist(
        Artisan artisan,
        Device device)
    {
        super(artisan,device);
        clean_init();
    }

    public OpenPlaylist(
        Artisan artisan,
        Device device,
        SSDPSearchService ssdp_service )
    {
        super(artisan,device,ssdp_service);
        clean_init();
    }

    private void clean_init()
    {
        num_tracks = 0;
        track_index = 0;
        my_shuffle = 0;

        is_dirty = false;
        playlist_count_id = 0;
        content_change_id = 0;

        transport_state = "";
        repeat = false;
        id_array = new int[0];

        tracks_by_open_id = new intTrackHash();
        tracks_by_position = new trackList();
    }



    //----------------------------------
    // getTrack
    //----------------------------------

    @Override public Track getTrack(int position)
    {
        int index = position -1;
        if (index < 0 || index >= num_tracks)
        {
            Utils.warning(0,0,"OpenPlaylist.getTrack(" + position + ") position out range - num_tracks=" + num_tracks);
            return null;
        }
        if (index >= tracks_by_position.size())
        {
            Utils.warning(0,0,"OpenPlaylist.getTrack(" + position + ") track not loaded. available=" + getNumAvailableTracks());
            return null;
        }

        Track track = tracks_by_position.get(index);
        if (track == null)
            Utils.error("OpenPlaylist.getTrack(" + position + ") returning null with available=" + getNumAvailableTracks());
        return track;
    }



    //--------------------------------------
    // OpenPlaylist Service Interface
    //--------------------------------------

    public void setShuffle(boolean b)
    {
        // action
    }
    public void setRepeat(boolean b)
    {
        // action
    }

    public String getTransportState() { return transport_state; }
    public boolean getShuffle()       { return my_shuffle >0 ; }
    public boolean getRepeat()        { return repeat; }


    public String OpenHomeToDLNAState(String dlna_state)
    {
        if (dlna_state.equals("Stopped")) return Renderer.RENDERER_STATE_STOPPED;
        if (dlna_state.equals("Playing")) return Renderer.RENDERER_STATE_PLAYING;
        if (dlna_state.equals("Paused")) return Renderer.RENDERER_STATE_PAUSED;
        if (dlna_state.equals("Buffering")) return Renderer.RENDERER_STATE_TRANSITIONING;
        return "";
    }


    //------------------------------
    // EditablePlaylist Interface
    //------------------------------

    @Override public void setName(String new_name)
    {}

    @Override public Track incGetTrack(int inc)
    {
        return playlistHelper.incGetTrack(this,inc);
    }

    @Override public Track seekByIndex(int position)
    {
        Track track = null;
        if (position>0 && position <= tracks_by_position.size())
        {
            track = tracks_by_position.get(position - 1);
            saveIndex(position);
        }
        return track;
    }


    @Override public Track insertTrack(int position, Track track)
    {
        // action
        return track;
    }


    @Override public boolean removeTrack(Track track)
    {
        // action
        return false;
    }


    //------------------------------
    // FetcherSource Interface
    //------------------------------

    private static int dbg_fetch = -1;

    @Override public int getRecChangedCountId()
    {
        return recs_changed_count_id;
    }

    @Override public trackList getFetchedTrackList()
    {
        return tracks_by_position;
    }

    public Fetcher.fetchResult getFetcherPlaylistRecords(int start, int num)
    {
        // short ending if all records gotten
        // else determine tracks to add

        Utils.log(dbg_fetch,0,"OpenPlaylist.getFetcherPlaylistRecords(" + start + "," + num + ") called");

        if (start >= num_tracks)
            return Fetcher.fetchResult.FETCH_DONE;

        // determine number of tracks to ADD
        // we already have start..tracks_by_position.size()-1

        if (start < tracks_by_position.size())
        {
            start = tracks_by_position.size();
            num -= tracks_by_position.size();
            Utils.log(dbg_fetch+1,1,"getFetcherPlaylistRecords() changing start,num to (" + start + "," + num + ")");
        }

        // and we can't be asked to get more than the playlist has
        // and we return FETCH_RECORDS if we don't get them all

        if (start + num > num_tracks)
        {
            num = num_tracks - start;
            Utils.log(dbg_fetch+1,1,"getFetcherPlaylistRecords() changing num to " + num);
        }


        // build the list of records to get

        String id_list = "";
        for (int i=start; i<start + num - 1; i++)
        {
            if (!id_list.isEmpty())
                id_list += " ";
            id_list += id_array[i];
        }

        // CALL THE REMOTE READLIST ACTION

        stringHash args = new stringHash();
        args.put("IdList",id_list);
        Utils.log(dbg_fetch+1,1,"getFetcherPlaylistRecords() asking remote for " + num + " records");
        Document result_doc = getDevice().doAction(getServiceType(),"ReadList",args);
        if (result_doc == null)
        {
            Utils.warning(0,1,"getFetcherPlaylistRecords() - ReadList returned null document");
            return Fetcher.fetchResult.FETCH_ERROR;
        }

        // Get the TrackList

        Document tracklist_doc = null;
        Element result_ele = result_doc.getDocumentElement();
        String track_list = Utils.getTagValue(result_ele,"TrackList");
        ByteArrayInputStream stream = new ByteArrayInputStream(track_list.getBytes());

        // Parse the TrackList

        try
        {
            Utils.log(dbg_fetch + 1,1,"getFetcherPlaylistRecords() parsing xml");
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            tracklist_doc = builder.parse(stream);
        }
        catch (Exception e)
        {
            Utils.warning(0,1,"getFetcherPlaylistRecords() - Could not parse TrackList");
            return Fetcher.fetchResult.FETCH_ERROR;
        }
        if (tracklist_doc == null)
        {
            Utils.warning(0,1,"getFetcherPlaylistRecords() - null TrackList doc");
            return Fetcher.fetchResult.FETCH_ERROR;
        }

        // Build and add the tracks

        int num_gotten = 0;
        Element tracklist_ele = tracklist_doc.getDocumentElement();
        Node track_node = tracklist_ele.getFirstChild();
        Utils.log(dbg_fetch+1,1,"getFetcherPlaylistRecords() parsing tracks");

        while (track_node != null)
        {
            Element track_ele = (Element) track_node;
            int open_id = Utils.parseInt(Utils.getTagValue(track_ele,"Id"));
            String uri = Utils.getTagValue(track_ele,"Uri");
            String didl = Utils.getTagValue(track_ele,"Metadata");

            Track track = new Track(uri,didl);
            tracks_by_open_id.put(open_id,track);
            track_node = track_node.getNextSibling();
            num_gotten++;
        }

        // Final error check

        if (num_gotten != num)
        {
            Utils.warning(0,1,"getFetcherPlaylistRecords() - expected " + num + " got " + num_gotten + " tracks");
            return Fetcher.fetchResult.FETCH_ERROR;
        }

        // Return

        Fetcher.fetchResult rslt = tracks_by_position.size() >= num_tracks ?
            Fetcher.fetchResult.FETCH_DONE :
            Fetcher.fetchResult.FETCH_RECS;

        Utils.log(dbg_fetch,0,"getFetcherPlaylistRecords() returning " + rslt + " with " + num_gotten + " new records");
        return rslt;

    }   // OpenPlaylist.getFetcherPlaylistRecords()



    //-------------------------------------
    // UpnpEventReceiver Interface
    //-------------------------------------

    private int event_count = 0;
    public String last_transport_state = "";

    @Override public int getEventCount()
    {
        return event_count;
    }

    public String response(
        NanoHTTPD.IHTTPSession session,
        NanoHTTPD.Response response,
        String service,
        Document doc)
    {
        event_count++;
        Element doc_ele = doc.getDocumentElement();
        boolean any_changes = false;

        Utils.log(0,0,"starting OpenPlaylist.response() started");

        // Parse the result document
        // Not currently parsing ProtocolInfo or TracksMax

        transport_state = Utils.getTagValue(doc_ele,"TransportState");
        transport_state = OpenHomeToDLNAState(transport_state);
        repeat = Utils.parseInt(Utils.getTagValue(doc_ele,"Repeat")) > 0;
        my_shuffle = Utils.parseInt(Utils.getTagValue(doc_ele,"Shuffle"));
        String id_array_string = Utils.getTagValue(doc_ele,"IdArray");

        Utils.log(0,1,"OpenPlaylist.response() id_array_string=" + id_array_string);

        byte bytes[] = Base64.decode(id_array_string);
        if (num_tracks != bytes.length/4)
        {
            any_changes = true;
            Utils.log(0,1,"OpenPlaylist.response() num_tracks changed from " + num_tracks + " to " + (bytes.length/4));
            num_tracks = bytes.length / 4;
        }
        else
            Utils.log(0,1,"OpenPlaylist.response() num_tracks unchanged at " + num_tracks);

        id_array = new int[num_tracks];
        String dbg_id_array = "";

        for (int i=0; i<id_array.length; i++)
        {
            int byte_num = i * 4;
            int open_id =
                (((int) bytes[ byte_num + 0 ] & 0xff) << 24) +
                (((int) bytes[ byte_num + 1 ] & 0xff) << 16) +
                (((int) bytes[ byte_num + 2 ] & 0xff) << 8) +
                (((int) bytes[ byte_num + 3 ] & 0xff));
            id_array[i] = open_id;
            dbg_id_array += " " + Integer.toString(open_id);
        }

        Utils.log(0,1,"OpenPlaylist.response()  id_array=" + dbg_id_array);

        // find the current track

        int index = 0;
        track_index = 0;
        int current_open_id = Utils.parseInt(Utils.getTagValue(doc_ele,"Id"));
        while (track_index == 0 && index < id_array.length)
        {
            if (current_open_id == id_array[index])
                track_index = index + 1;
            else
                index++;
        }

        Utils.log(0,1,"OpenPlaylist.response() " + (track_index==0?"could not find":"found") + " current_track(" + current_open_id + ") at " + track_index);
        Utils.log(0,1,"OpenPlaylist.response() initial tracks_by_open_id.size=" + tracks_by_open_id.size());

        // Prune tracks_by_open_id

        intTrackHash new_tracks = new intTrackHash();
        for (int i=0; i<num_tracks; i++)
        {
            int open_id = id_array[i];
            Track exists = tracks_by_open_id.get(open_id);
            if (exists == null)
                any_changes = true;
            else
                new_tracks.put(open_id,exists);
        }
        tracks_by_open_id = new_tracks;

        Utils.log(0,1,"OpenPlaylist.response() new tracks_by_open_id.size=" + tracks_by_open_id.size());

        // if any changes, announce ourselves as a new
        // playlist to the tempEditablePlaylist and Renderer
        // and send out the change event

        if (any_changes)
        {
            Utils.log(0,1,"OpenPlaylist.response() setting self as tempEditablePlaylist.associated_playlist");
            artisan.setPlaylist(this);
            // artisan.getRenderer().notifyPlaylistChanged();
            // hey wait a minute, that's us!
            //Utils.log(0,1,"OpenPlaylist.response() sending EVENT_PLAYLIST_CHANGED");
            //artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_PLAYLIST_CHANGED,this);
        }

        // aPlaylist will now react by setting up a fetcher to call
        // tempEditablePlaylist, who will in knowledgeable turn, call our
        // getReadTracks() method a chunk at a time

        if (!transport_state.equals(last_transport_state))
        {
            last_transport_state = transport_state;
            Utils.log(0,1,"OpenPlaylist.response() sending EVENT_STATE_CHANGED(" + last_transport_state + ")");
            artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_STATE_CHANGED,last_transport_state);
        }

        Utils.log(0,0,"OpenPlaylist.response() finished");
        return "";
    }


}   // class device.service.OpenPlaylist
