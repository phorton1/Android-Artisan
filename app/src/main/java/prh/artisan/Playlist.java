package prh.artisan;


import prh.server.http.UpnpEventManager;
import prh.utils.Utils;
import prh.utils.httpUtils;


public abstract class Playlist
    // base class of playlists
{
    public abstract void stop();
    public abstract void start();

    public abstract String getName();
    public abstract int getNum();
    public abstract int getMyShuffle();
    public abstract int getCurrentIndex();
    public abstract int getNumTracks();

    public abstract Track getTrack(int index);
    public abstract Track getCurrentTrack();
    public abstract Track incGetTrack(int inc);

    // open home support

    private static int dbg_expose = 0;
    private static int NUM_TO_EXPOSE = 10;
        // number that BuP asks for in a request

    protected UpnpEventManager upnp_event_manager = null;
    protected int num_exposed = 0;


    public abstract String getIdArrayString();
    public abstract Track getByOpenId(int open_id);
    public abstract boolean removeTrack(int open_id);
    public abstract Track insertTrack(int after_id, Track track);
    public abstract Track seekByOpenId(int open_id);
    public abstract Track seekByIndex(int index);
    public abstract void save(boolean release);

    public void setUpnpEventManager(UpnpEventManager mgr)
    {
        upnp_event_manager = mgr;
    }

    public Track insertTrack(int after_id, String uri, String metadata)
    {
        Track track = new Track(uri,metadata);
        return insertTrack(after_id,track);
    }

    public int[] string_to_id_array(String id_string)
        // gets a string of space delimited ascii integers!
    {
        Utils.log(0,0,"string_to_id_array(" + id_string + ")");
        String parts[] = id_string.split("\\s");
        int ids[] = new int[parts.length];
        for (int i=0; i<parts.length; i++)
            ids[i] = Utils.parseInt(parts[i]);
        return ids;
    }


    // functional constants
    // Took a long time to figure out that BubbleUP delivers (expects)
    // the whole thing to be xml_encoded, with no outer <didl> tag,
    // but an extra inner TrackList tag. And apparently BuP only works
    // if it gets ALL of the ids it asked for ... you cannot trim
    // the list here ...

    private static int READLIST_LIMIT = 0;
    // only return this number of tracks
    // set to zero to send all
    private static boolean READLIST_ENCODE = true;
    // if true, the entire tracklist returned by ReadList action
    // will be xml_encoded. This is what BubbleUp does.
    private static boolean READLIST_DIDL = false;
    // if true, the whole tracklist will be wrapped in didl tags.
    // BubbleUp does not do this, though they do encode the whole thing.
    private static boolean READLIST_INNER_TRACKLIST = true;
    // if true an extra level of of <Tracklist> tags
    // will be added to the Readlist tracklist reply.
    // For some reason BuP does this, and it's xml_encoded.
    private static boolean READLIST_INNER_DIDL = true;
    // if true, even if the whole message is diddled,
    // an inner didl tag will be added to each metadata
    // I guess this is true by default.


    public String id_array_to_tracklist(int ids[])
    // The response TrackList XML item has an
    // inner DIDL TrackList ... these details
    // determined empirically from BuP
    {
        boolean one_error = false;

        String rslt = "\n";
        if (READLIST_DIDL)
            rslt +=  httpUtils.start_didl() + "\n";
        if (READLIST_INNER_TRACKLIST)
            rslt += "<TrackList>\n";

        int use_len = READLIST_LIMIT > 0 ? READLIST_LIMIT : ids.length;

        for (int i=0; i<use_len; i++)
        {
            int id = ids[i];
            Track track = getByOpenId(id);
            if (track == null)
            {
                if (!one_error)
                    Utils.error("id_array_to_tracklist: index("+i+")  id("+id+") not found");
                one_error = true;
            }
            else
            {
                String metadata = !READLIST_DIDL || READLIST_INNER_DIDL ?
                    track.getDidl() :
                    track.getMetadata();

                rslt += "<Entry>\n" +
                    "<Id>" + id + "</Id>\n" +
                    "<Uri>" + track.getPublicUri() + "</Uri>\n" +
                    "<Metadata>" + metadata + "</Metadata>\n" +
                    "</Entry>\n";
            }
        }
        if (READLIST_INNER_TRACKLIST)
            rslt += "</TrackList>\n";
        if (READLIST_DIDL)
            rslt = httpUtils.end_didl() + "\n";
        if (READLIST_ENCODE)
            rslt = httpUtils.encode_xml(rslt);
        return rslt;
    }



    //---------------------------------------------------------------
    // exposure scheme
    //---------------------------------------------------------------
    // prh - this should only be done for BubbleUp, and generally
    // turned off by a null upnp_event_manager in THIS class.
    //
    // For bubbleUP, we incrementally expose the playlist,
    // about 20 records at a time, so that it doesn't hang
    // on large playlists.
    //
    // We keep an in-memory only bit on the actual tracks
    // that tells us if they have been exposed (default = false
    // for an "null" unloaded track in the list).
    //
    // We first hand out the currently playing track, then the one
    // after it, then the one before it, then 2 after it, and 2
    // before it, and so on, until we have exposed the whole thing.
    //
    // Tracks created from didl should start out as exposed.
    //
    // The exposed bits need to be cleared on stop() when
    // playlists change, so next time, Bup will start over.
    //
    // The call to looping call to expose_more is in the
    // OpenPlalylist ReadList action.

    protected boolean expose(int index)
    {
        Track track = getTrack(index);
        if (!track.getExposed())
        {
            track.setExposed(true);
            num_exposed++;
            return true;
        }
        return false;
    }


    public boolean expose_more()
    {
        Utils.log(dbg_expose,0,"expose_more() track_index=" + getCurrentIndex() + " num_exposed=" + num_exposed + " num_tracks=" + getNumTracks());
        int num = getNumTracks();
        int idx = getCurrentIndex();

        if (idx > 0 && num_exposed < num)
        {
            int offset = 0;
            int count_exposed = 0;
            int direction = 1;

            while (num_exposed < num &&
                count_exposed < NUM_TO_EXPOSE - 1)
            {
                int try_index = (idx - 1) + offset * direction;
                if (try_index > 0)
                    if (try_index < num)
                        if (expose(try_index))
                            count_exposed++;

                if (offset == 0)
                    offset = 1;
                else if (direction == 1)
                    direction = -1;
                else
                {
                    direction = 1;
                    offset++;
                }
            }

            Utils.log(dbg_expose,1,"expose_more() exposed " + count_exposed + " new tracks");
            return count_exposed > 0;
        }
        return false;
    }


    public void start_expose_more_thread()
    {
        Utils.log(dbg_expose,0,"start_expose_more_thread() track_index=" + getCurrentIndex() + " num_exposed=" + num_exposed + " num_tracks=" + getNumTracks());
        Thread thread = new Thread(new expose_more_thread());
        thread.start();
        Utils.log(0,0,"expose_thread started ..");
    }

    public class expose_more_thread implements Runnable
    {
        public void run()
        {
            Utils.log(dbg_expose,0,"expose_more_thread::run() track_index=" + " num_exposed=" + num_exposed + " num_tracks=" + getNumTracks());
            Utils.sleep(10000);
            Utils.log(0,0,"back from sleep");
            if (expose_more())
            {
                Utils.log(dbg_expose,1,"expose_more sending event");
                if (upnp_event_manager != null)
                    upnp_event_manager.incUpdateCount("Playlist");
            }
        }
    }


}
