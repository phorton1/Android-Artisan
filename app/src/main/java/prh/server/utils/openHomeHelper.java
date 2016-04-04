package prh.server.utils;

import prh.artisan.Track;
import prh.base.HelpablePlaylist;
import prh.base.ServablePlaylistHelper;
import prh.types.intTrackHash;
import prh.types.trackList;
import prh.utils.Base64;
import prh.utils.Utils;
import prh.utils.httpUtils;


public class openHomeHelper implements
    ServablePlaylistHelper
{
    private static int dbg_ida = 0;     // id arrays

    private static int next_open_id = 1;
    private HelpablePlaylist parent;
    private intTrackHash tracks_by_open_id;

    // do nothing for the ServablePlaylist Inteface

    //-------------------------------------------------
    // ctor && custom API
    //-------------------------------------------------

    public openHomeHelper(HelpablePlaylist parent)
    {
        this.parent = parent;
        tracks_by_open_id = new intTrackHash();
        trackList tracks_by_position = parent.getTrackListRef();
        for (int i = 0; i < tracks_by_open_id.size(); i++)
        {
            Track track = tracks_by_position.get(i);
            addTrack(track);
        }
    }


    public void stop()
    {
        if (tracks_by_open_id != null)
            tracks_by_open_id.clear();
        tracks_by_open_id = null;
        parent = null;
    }

    public void clear()
    {
        tracks_by_open_id.clear();
    }

    public void addTrack(Track track)
    {
        track.setOpenId(next_open_id);
        tracks_by_open_id.put(next_open_id++,track);
    }

    public void delTrack(Track track)
    {
        tracks_by_open_id.remove(track.getOpenId());
    }



    //---------------------------------------------
    // ServablePlaylist
    //---------------------------------------------

    @Override
    public Track getByOpenId(int open_id)
    {
        return tracks_by_open_id.get(open_id);
    }


    @Override
    public Track insertTrack(int after_id,String uri,String metadata)
    {
        Track track = new Track(uri,metadata);
        return insertTrack(track,after_id);
    }


    @Override
    public Track seekByOpenId(int open_id)
    {
        Track track = getByOpenId(open_id);
        if (track != null)
        {
            trackList tracks_by_position = parent.getTrackListRef();
            int position = tracks_by_position.indexOf(track) + 1;
            parent.saveIndex(position);
        }
        return track;
    }


    @Override
    public Track insertTrack(Track track,int after_id)
    // insert the item after the given open id
    // where 0 means front of the list.
    // returns the item on success
    {
        int insert_idx = 0; // zero base
        if (after_id > 0)
        {
            Track after_track = tracks_by_open_id.get(after_id);
            if (track == null)
            {
                Utils.error("Could not find item(after_id=" + after_id + ") for insertion");
                return null;    // should result in 800 error
            }
            trackList tracks_by_position = parent.getTrackListRef();
            insert_idx = tracks_by_position.indexOf(after_track) + 1;
            // insert_pos is zero based
        }

        int position = insert_idx + 1;
        return parent.insertTrack(position,track);
    }


    @Override
    public boolean removeTrack(int open_id)
    // OPEN_ID SIGNATURE
    {
        Track track = tracks_by_open_id.get(open_id);
        if (track == null)
        {
            Utils.error("Could not remove(" + open_id + ") from playlist(" + parent.getName() + ")");
            return false;
        }
        return parent.removeTrack(track);
    }


    //------------------------------------------------------------------
    // IdArrays
    //------------------------------------------------------------------


    @Override
    public int[] string_to_id_array(String id_string)
    // gets a string of space delimited ascii integers!
    {
        Utils.log(0,0,"string_to_id_array(" + id_string + ")");
        String parts[] = id_string.split("\\s");
        int ids[] = new int[parts.length];
        for (int i = 0; i < parts.length; i++)
            ids[i] = Utils.parseInt(parts[i]);
        return ids;
    }


    @Override
    public String getIdArrayString(playlistExposer exposer)
    // return a base64 encoded array of integers
    // May be called with a playlistExposer or not
    {
        int num_ids = 0;
        int num_available = tracks_by_open_id.size();

        byte data[] = new byte[num_available * 4];
        Utils.log(dbg_ida,0,"getIdArrayString() num_tracks=" + num_available);

        // show debugging for the exposer, if any

        if (exposer != null)
            Utils.log(dbg_ida,1,"exposer(" + exposer.getUserAgent() + ") num_exposed=" + exposer.getNumExposed());

        for (int position = 1; position <= num_available; position++)
        {
            Track track = parent.getTrack(position);

            if (exposer == null || exposer.isExposed(track))
            {
                int id = track.getOpenId();
                data[num_ids * 4 + 0] = (byte) ((id >> 24) & 0xFF);
                data[num_ids * 4 + 1] = (byte) ((id >> 16) & 0xFF);
                data[num_ids * 4 + 2] = (byte) ((id >> 8) & 0xFF);
                data[num_ids * 4 + 3] = (byte) (id & 0xFF);
                num_ids++;
            }
        }

        byte data2[] = new byte[num_ids * 4];
        for (int i = 0; i < num_ids * 4; i++)
            data2[i] = data[i];

        String retval = Base64.encode(data2);
        Utils.log(dbg_ida + 1,1,"id_array Length=" + num_ids);
        Utils.log(dbg_ida + 1,1,"id_array='" + retval + "'");
        return retval;
    }


    @Override
    public String id_array_to_tracklist(int ids[])
    {
        String rslt = "";
        for (int i = 0; i < ids.length; i++)
        {
            int id = ids[i];
            Track track = getByOpenId(id);
            if (track == null)
            {
                Utils.error("id_array_to_tracklist: index(" + i + ")  id(" + id + ") not found");
                return httpUtils.encode_lite("<TrackList></TrackList>");
            }

            rslt += "<Entry>" + // \n" +
                "<Id>" + id + "</Id>" + // "\n" +
                "<Uri>" + track.getPublicUri() + "</Uri>" + // "\n" +
                "<Metadata>" + track.getDidl() + "</Metadata>" + // "\n" +
                "</Entry>"; // \n";
        }

        // took a while to figure this ...
        //
        // This is an INNER <TrackList> that is Didl encoded.
        // It is the VALUE of a regular XML <Tracklist> element
        // in the ReadList result.

        rslt = "<TrackList>" + rslt + "</TrackList>";
        rslt = httpUtils.encode_lite(rslt);
        return rslt;
    }

}   // class openHomeHelper
