package prh.artisan;

// The CurrentPlaylist is a singleton that acts
// as a wrapper for an actual Playlist.
//
// It is the current Playlist being edited by aPlaylist.
// It is the current playlist being played by aRenderer.
//
// It is the only playlist known to aPlaying and aRenderer.
// From the UI's perspective, it is the only Playlist in the system.
//
// It exists only in memory as an Edit Buffer and Queue, though
// it can be saved to
// with the little exception that if it happens to be asociated
// with a LocalPlaylist,

import prh.device.LocalPlaylist;
import prh.types.recordList;
import prh.utils.Utils;


public class CurrentPlaylist extends Playlist
    implements Fetcher.FetcherSource

{
    private int dbg_cp = 1;

    // state

    private Artisan artisan;
    private Playlist associated_playlist = null;
    private int playlist_count_id = 0;
    public int getPlaylistCountId() { return playlist_count_id; }


    //----------------------------------------------------------------
    // Constructor
    //----------------------------------------------------------------

    public CurrentPlaylist(Artisan ma)
        // default constructor creates an un-named playlist ""
        // with no associated parent playListSource
    {
        super(ma);
        artisan = ma;
    }

    public boolean startCurrentPlaylist()
    {
        return true;
    }

    public void stopCurrentPlaylist()
    {
    }

    public void setName(String new_name)
    {
        if (!name.equals(new_name))
        {
            name = new_name;
            artisan.handleArtisanEvent(EventHandler.EVENT_PLAYLIST_CHANGED,this);
        }
    }



    public void setAssociatedPlaylist(Playlist other)
        // the playist shall have already been
        // started by artisan.
    {
        next_open_id = 1;
        playlist_count_id++;

        if (associated_playlist != null)
            associated_playlist.stop();

        associated_playlist = other;

        this.clean_init_playlist();

        if (associated_playlist != null)
        {
            associated_playlist.start();

            this.name = associated_playlist.name;
            this.num_tracks = associated_playlist.num_tracks;
            this.track_index = associated_playlist.track_index;
            this.my_shuffle = associated_playlist.my_shuffle;
            this.next_open_id = associated_playlist.next_open_id;

            tracks_by_open_id.clear();
            tracks_by_position.clear();
            tracks_by_open_id.putAll(associated_playlist.tracks_by_open_id);
            tracks_by_position.addAll(associated_playlist.tracks_by_position);
        }

        setDirty(false);
    }


    @Override
    public Track getTrack(int index)
    {
        // try to get it in memory

        if (index <= 0 || index > num_tracks)
            return null;

        // get from in-memory cache

        if (index - 1 > tracks_by_position.size())
        {
            Utils.error("wtf2");
            return null;
        }
        Track track = tracks_by_position.get(index - 1);

        // however, if the track is not found,
        // defer to the associated_playlist

        if (track == null)
        {
            if (associated_playlist == null)
                Utils.error("No associated playlist for null track in CurrentPlaylist.getTrck(" + index + ")");
            else
            {
                track = associated_playlist.getTrack(index);
                tracks_by_position.set(index - 1,track);
                tracks_by_open_id.put(next_open_id++,track);
            }
        }

        return track;
    }


    //------------------------------
    // FetcherSource Interface
    //------------------------------

    private int dbg_fetch = 0;

    @Override public boolean isDynamicFetcherSource()
    {
        return true;
    }


    boolean fetcher_valid = true;
    int num_virtual_folders = 0;
    Folder last_virtual_folder = null;


    private void initVirtualFolders()
    {
        num_virtual_folders = 0;
        last_virtual_folder = null;
    }


    private Folder addVirtualFolder(Track track)
    // Given the track, if the virtual folder does
    // not already exist, create it an initialize
    // from the track, and return.  Otherwise add
    // the track's statistics (existence, duration)
    // to the virtual folder, but return null.
    {
        String title = track.getAlbumTitle();
        String artist = track.getAlbumArtist();
        int duration = track.getDuration();
        String art_uri = track.getLocalArtUri();
        String year_str = track.getYearString();
        String genre = track.getGenre();
        String id = track.getParentId();
        if (artist.isEmpty())
            artist = track.getArtist();

        if (last_virtual_folder == null ||
            !last_virtual_folder.getTitle().equals(title))
        {
            Folder folder = new Folder();
            folder.setId(id);
            folder.setTitle(title);
            folder.setNumElements(0);   // implicit
            folder.setDuration(0);      // implicit
            folder.setArtist(artist);
            folder.setArtUri(art_uri);    // requires internal knowledge
            folder.setYearString(year_str);
            folder.setGenre(genre);
            folder.setType("album");
            last_virtual_folder = folder;
            return folder;
        }

        Folder folder = last_virtual_folder;
        String folder_title = folder.getTitle();
        String folder_artist = folder.getArtist();
        String folder_art_uri = folder.getLocalArtUri();
        String folder_year_str = folder.getYearString();
        String folder_genre = folder.getGenre();
        String folder_id = folder.getId();

        folder.incNumElements();
        folder.addDuration(duration);

        if (folder_title.isEmpty())
            folder.setTitle(title);
        if (folder_art_uri.isEmpty())
            folder.setArtUri(art_uri);        // requires special knowledge
        if (folder_year_str.isEmpty())
            folder.setYearString(year_str);
        if (folder_genre.isEmpty())
            folder.setGenre(genre);
        if (folder_id.isEmpty())
            folder.setId(id);

        String sep = folder_genre.isEmpty() ? "" : "|";
        if (!genre.isEmpty() &&
            !folder.getGenre().contains(genre))
            folder.setGenre(folder_genre + sep + genre);
        if (!artist.isEmpty() &&
            !folder.getArtist().contains(artist))
            folder.setArtist("Various");

        return null;
    }


    @Override public Fetcher.fetchResult getFetchRecords(Fetcher fetcher, boolean initial_fetch, int num)
    {
        // synchronized (this)
        {
            recordList records = fetcher.getRecordsRef();
            int num_records = records.size();
            Utils.log(dbg_fetch,0,"CurrentPlaylist(" + getName() + ").getFetchRecords(" + initial_fetch + "," + num + "," + fetcher.getAlbumMode() + ") " +
                num_records + "/" + num_tracks + " tracks already gotten, and " +
                num_virtual_folders + " existing virtual folders");

            // cannot have more virtual folders than records and
            // we treat special case of num_records == 0 as restarting
            // the virtual folders

            if (num_records == 0 ||
                num_virtual_folders >= num_records)
                initVirtualFolders();

            // starting at the number of records in the fetcher
            // subtract the number of virtual folders if fetching that way
            // to get the actual 0 based track to get ...

            int num_added = 0;
            int next_index = num_records;
            if (fetcher.getAlbumMode())
                next_index -= num_virtual_folders;

            // if fetcher was invalidated start over
            // but go all the way up to the previous size + num

            if (!fetcher_valid)
            {
                Utils.log(dbg_fetch,1,"CurrentPlaylist(" + getName() + ").getFetchRecords(" + initial_fetch + "," + num + ") invalidated fetcher resetting next=0 and num=" + (num + next_index));
                num = num + next_index;
                next_index = 0;
                records.clear();
            }

            while (next_index < num_tracks && num_added < num)
            {
                Track track = getTrack(next_index + 1);    // one based
                if (track == null)
                {
                    Utils.error("Null track from getTrack(" + (next_index + 1) + " in fetchGetRecords()");
                    return Fetcher.fetchResult.FETCH_ERROR;
                }

                if (fetcher.getAlbumMode())
                {
                    Folder folder = addVirtualFolder(track);
                    if (folder != null)
                    {
                        track = null;
                        records.add(folder);
                        num_virtual_folders++;
                        num_added++;

                        Utils.log(dbg_fetch+1,1,"added virtual_folder[" + num_virtual_folders + "] " + folder.getTitle());
                        Utils.log(dbg_fetch+1,2,"at record[" + records.size() + "] as the " + num_added + " record in the fetch");
                    }
                }

                if (track != null)
                {
                    records.add(track);
                    num_added++;
                    next_index++;
                }
            }

            Fetcher.fetchResult result =
                next_index >= num_tracks ? Fetcher.fetchResult.FETCH_DONE :
                    num_added > 0 ? Fetcher.fetchResult.FETCH_RECS :
                        Fetcher.fetchResult.FETCH_NONE;

            // if (num_fetched > 0)
            //     artisan.handleArtisanEvent(EventHandler.EVENT_PLAYLIST_CONTENT_CHANGED,this);


            Utils.log(dbg_fetch,1,"CurrentPlaylist.getFetchRecords() returning " + result + " with " + records.size() + " records");
            return result;
        }
    }



}   // class CurrentPlaylist

