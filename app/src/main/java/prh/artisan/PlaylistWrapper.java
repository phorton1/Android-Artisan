package prh.artisan;

import prh.base.HelpablePlaylist;
import prh.base.ServablePlaylist;
import prh.server.utils.playlistExposer;
import prh.base.ArtisanEventHandler;
import prh.base.EditablePlaylist;
import prh.base.Playlist;
import prh.base.PlaylistSource;
import prh.types.trackList;
import prh.utils.Utils;
import prh.server.utils.openHomeHelper;
import prh.utils.playlistHelper;


public class PlaylistWrapper implements
    EditablePlaylist,
    ServablePlaylist,
    HelpablePlaylist
{

    private static int dbg_cp = 1;      // basics
    private static int dbg_fetch = 0;   // fetcher

    // state

    private Artisan artisan;
    private PlaylistSource source;
    private Playlist other;

    private boolean is_dirty;
    private int playlist_count_id;
    private int content_change_id;
    private int recs_changed_count_id;

    // contents

    private  String name;
    private  int playlist_num;
    private  int num_tracks;
    private  int track_index;      // one based
    private  int my_shuffle;
    private  String pl_query;

    private trackList tracks_by_position;
    private openHomeHelper open_helper;

    // Basic Playlist Interface, except getTrack()

    @Override public PlaylistSource getSource()  { return source; }

    @Override public String getName()         { return name; }
    @Override public int getPlaylistNum()     { return playlist_num; }
    @Override public int getNumTracks()       { return num_tracks; }
    @Override public int getCurrentIndex()    { return track_index; }
    @Override public Track getCurrentTrack()  { return getTrack(track_index); }
    @Override public int getMyShuffle()       { return my_shuffle; }
    @Override public String getQuery()        { return pl_query; }

    @Override public boolean isDirty() { return is_dirty; }
    @Override public void setDirty(boolean b) { is_dirty = b; }

    @Override public int getNumAvailableTracks()
    {
        return tracks_by_position.size();
    }
    @Override public void saveIndex(int position)
    {
        track_index = position;
    }

    // EditablePlaylist Interface

    @Override public int getPlaylistCountId() { return playlist_count_id; }
    @Override public int getContentChangeId() { return content_change_id; }

    // HelpablePlaylist Intefaces

    @Override public trackList getTrackListRef() { return tracks_by_position; }


    //----------------------------------------------------------------
    // Constructor
    //----------------------------------------------------------------

    public PlaylistWrapper(Artisan ma, Playlist other)
    {
        artisan = ma;
        clean_init();
        this.other = other;
        if (other != null)
        {
            source = other.getSource();
            name = other.getName();
            num_tracks = other.getNumTracks();
            track_index = other.getCurrentIndex();
            my_shuffle = other.getMyShuffle();
        }
    }


    private void clean_init()
    {
        is_dirty = false;

        name = "";
        my_shuffle = 0;
        num_tracks = 0;
        track_index = 0;
        playlist_num = 0;
        pl_query = "";

        tracks_by_position =  new trackList();
        open_helper = null;

        playlist_count_id = 0;      // identity changed
        content_change_id = 0;      // any contents changed
        recs_changed_count_id = 0;  // records before eol changed

        playlist_count_id++;
        content_change_id++;
        recs_changed_count_id++;

    }



    //----------------------------------------------------------------
    // Playlist Interface
    //----------------------------------------------------------------


    @Override public boolean startPlaylist()
    {
        if (other.startPlaylist())
        {
            if (other.getNumTracks() != other.getNumAvailableTracks())
            {
                Utils.warning(0,0,"Cannot start PlaylistWrapper with partially constructed playlist:" +
                    " other.num_tracks=" + other.getNumTracks() +
                    " other.available=" + other.getNumAvailableTracks());
                return false;
            }

            for (int i = 0; i < num_tracks; i++)
            {
                Track track = other.getTrack(i + 1);
                track.setPosition(i + 1);   // in case it was mucked up
                tracks_by_position.add(track);
            }
        }
        return true;
    }

    @Override public void stopPlaylist(boolean wait_for_stop)
    {
        tracks_by_position.clear();
        clean_init();
    }


    @Override public Track getTrack(int index)
    {
        // check the index

        if (index <= 0 || index > num_tracks)
            return null;

        // get it

        Track track = tracks_by_position.get(index - 1);
        if (track == null)
            Utils.warning(0,0,"Null Track for tempEditablePlaylist.getTrack(" + index + ")");
        return track;
    }


    //------------------------------
    // FetcherSource Interface
    //------------------------------


    @Override public int getRecChangedCountId()
    {
        return recs_changed_count_id;
    }

    @Override public trackList getFetchedTrackList()
    {
        return tracks_by_position;
    }


    @Override public Fetcher.fetchResult getFetcherPlaylistRecords(int start, int num)
    {
        return Fetcher.fetchResult.FETCH_DONE;
    }


    //-----------------------------------------------------
    // EditablePlaylist Inteface (continued)
    //-----------------------------------------------------

    @Override public Track incGetTrack(int inc)
    {
        return playlistHelper.incGetTrack(this,inc);
    }

    @Override public void setName(String new_name)
        // Called by the PlaylistSource in saveAs()
    {
        if (!name.equals(new_name))
        {
            name = new_name;
            artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_PLAYLIST_CHANGED,this);
        }
    }


    @Override public Track seekByIndex(int position)
    {
        Track track = getTrack(position);
        if (track != null)
            saveIndex(position);
        return track;
    }


    @Override public Track insertTrack(int position, Track track)
    // ONE-BASED POSITION SIGNATURE
    // This IS the lowest level insertTrack method
    // PlaylistBase assigns next_open_id for insertTrack()
    {
        is_dirty = true;
        content_change_id++;

        if (position == 0)  // inserting into a new
            position = 1;   // empty playlist

        if (position > tracks_by_position.size() + 1)
        {
            Utils.error("Cannot add past the end of the list .. list may not be ready");
            return null;
        }

        // not just adding?

        if (position < tracks_by_position.size())
            recs_changed_count_id++;

        track.setPosition(position);
        int old_track_index = track_index;

        Utils.log(dbg_cp,0,"insertTrack(" + track.getTitle() + " to tempEditablePlaylist(" + name + ") at position=" + position);
        if (open_helper != null)
            open_helper.addTrack(track);

        tracks_by_position.add(position - 1,track);
        num_tracks++;

        if (track_index > position)
            track_index++;
        else if (track_index == 0)
            track_index = 1;

        if (old_track_index != track_index)
            saveIndex(track_index);

        // Tell server.http.OpenPlaylist to expose this tracl

        // prh.server.http.OpenPlaylist open_playlist = getHttpOpenPlaylist();
        // if (open_playlist != null)
        //     open_playlist.exposeTrack(track,true);

        artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_PLAYLIST_CONTENT_CHANGED,this);
        return track;
    }


    @Override public boolean removeTrack(Track track)
    // Assumes that track is in the playlist
    // Takes track and not integer position
    {
        is_dirty = true;
        content_change_id++;
        recs_changed_count_id++;

        int position = tracks_by_position.indexOf(track) + 1;
        Utils.log(dbg_cp,0,"removeTrack(" + track.getTitle() + ") from tempEditablePlaylist(" + name + ") at position=" + position);

        num_tracks--;
        if (open_helper != null)
            open_helper.delTrack(track);
        tracks_by_position.remove(track);

        int old_track_index = track_index;
        if (position < track_index)
            track_index--;
        else if (track_index > num_tracks)
            track_index = num_tracks;

        if (old_track_index != track_index)
            saveIndex(track_index);

        artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_PLAYLIST_CONTENT_CHANGED,this);
        return true;
    }


    //---------------------------------------------------
    // pass thru ServablePlaylist interface
    //---------------------------------------------------

    private void initOpenHelper()
    {
        if (open_helper == null)
            open_helper = new openHomeHelper(this);
    }

    @Override public Track getByOpenId(int open_id)
    {
        initOpenHelper();
        return open_helper.getByOpenId(open_id);
    }

    @Override public Track insertTrack(Track track, int after_id)
    {
        initOpenHelper();
        return open_helper.insertTrack(track,after_id);
    }

    @Override public Track insertTrack(int after_id, String uri, String metadata)
    {
        initOpenHelper();
        return open_helper.insertTrack(after_id,uri,metadata);
    }

    @Override public boolean removeTrack(int open_id)
    {
        initOpenHelper();
        return open_helper.removeTrack(open_id);
    }

    @Override public Track seekByOpenId(int open_id)
    {
        initOpenHelper();
        return open_helper.seekByOpenId(open_id);
    }

    @Override public String getIdArrayString(playlistExposer exposer)
    {
        initOpenHelper();
        return open_helper.getIdArrayString(exposer);
    }

    @Override public String id_array_to_tracklist(int ids[])
    {
        initOpenHelper();
        return open_helper.id_array_to_tracklist(ids);
    }

    @Override public int[] string_to_id_array(String id_string)
    {
        initOpenHelper();
        return open_helper.string_to_id_array(id_string);
    }


}   // class PlaylistWrapper

