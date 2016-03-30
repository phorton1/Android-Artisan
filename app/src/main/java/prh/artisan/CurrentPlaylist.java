package prh.artisan;

// The CurrentPlaylist is a singleton that acts
// as a wrapper for an actual Playlist.
//
//     It is the current Playlist being edited by aPlaylist.
//        which uses a fetcher to get records from it
//     It is the current playlist being played by aRenderer.
//        so it knows the next track to play
//
//     It exists only in memory as an Edit Buffer and Queue, though
//     it can be loaded and saved to the current PlaylistSource.
//
//     It keeps a pointer to actual underlying associated_playlist,
//     and uses a sparse array of nulls to begin with.
//
//     getPlaylistTrack() is the key routine, a read-thru cache from
//     the underlying associated playlist.
//
//     It is also the only Playlist to get served by the http.OpenPlaylist
//     and the only playlist to know about, and known by, exposers.
//
// OpenHome
//
//     It may also, additionally, be tightly bound to a device.OpenPlaylist.
//
//     An OpenPlaylist has no notion of a name, or dirty state, or the fact that a
//     PlaylistSource context might exist. But, while the OpenHomeRenderer is the
//     selected device, this CurrentPlaylist and the OpenPlaylist are synchronized,
//     both always showing the same list of tracks.
//
//     When an OpenPlaylist is attached, it is absorbed into this CurrentPlaylist,
//     just like an associated Playlist. aPlaylist starts a fetcher, and we start handing
//     it records from the device.OpenPlaylist, which in turn gets, and caches them,
//     based on the IDArray it gets (via SSDP Events), from the remote open home device.
//     If want to insert a record, we ask the device.OpenPlaylist to insert it,
//     it calls the remote open home device to do the insert, which returns the
//     new openID, which gets added to the device.OpenHomeCache, and in turn,
//     this CurrentPlaylist's list of records, which is marked as dirty, etc.
//
//     But, because of this, we do not get explicit information about changes made
//     on the remote open home device.  All we get is a new IdArray that we can
//     tell if it has changed or not, and/or if wa are missing any elements.
//     So we can tell if it changed, and is dirty, and can find our place in
//     it again, sort of.  That's ok .. it's a generic EVENT_PLAYLIST_CONTENT_CHANGED.
//
//     The other issue is that when we load a new playlist, we HAVE to do it in
//     terms of individual Inserts to the remote open_home device, which is not
//     only really slow
//
//     The OpenPlaylist DeleteAll action is the equivalent of the setPlaylist("")
//     command ... a new empty playlist is created and the old one is tossed.
//     The big difference is that if the DeleteAll action comes FROM the OpenPlaylist,
//     i.e. the user has pressed the "Clear" button in BubbleUp to clear the playlist,
//     then all changes are lost, whereas on the Artisan UI, you get a chance to
//     Save the changes.
//
//     When we load a new playlist, it has to be So Artisan acts as a repository of OpenHome playlists.
//
// The difference is that

// synchonic
//

import prh.device.LocalPlaylist;
import prh.server.HTTPServer;
import prh.server.http.OpenPlaylist;
import prh.types.recordList;
import prh.utils.Utils;


public class CurrentPlaylist extends PlaylistBase
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

    // start and stop the whole thing

    public boolean startCurrentPlaylist()
    {
        return true;
    }

    public void stopCurrentPlaylist()
    {
    }


    // support for schemes

    public Track getTrackLow(int position)
        // One based access to the sparse array
        // for CurrentPlaylistExposer
    {
        Track track = null;
        if (position > 0 &&
            position <= tracks_by_position.size())
            track = tracks_by_position.get(position - 1);
        return track;
    }


    public void setName(String new_name)
        // used in saveAs()
    {
        if (!name.equals(new_name))
        {
            name = new_name;
            artisan.handleArtisanEvent(EventHandler.EVENT_PLAYLIST_CHANGED,this);
        }
    }


    //----------------------------------------------------------------
    // associated_playlist
    //----------------------------------------------------------------


    private OpenPlaylist getHttpOpenPlaylist()
        // OpenHome support
    {
        HTTPServer http_server = artisan.getHTTPServer();
        OpenPlaylist open_playlist = http_server == null ? null :
            (OpenPlaylist) http_server.getHandler("Playlist");
        return open_playlist;
    }


    public void setAssociatedPlaylist(Playlist other)
    {
        // clean_init() is the equivalent of start()
        // for the CurrentPlaylist

        playlist_count_id++;
        this.clean_init_playlist_base();
        OpenPlaylist open_playlist = getHttpOpenPlaylist();
        if (open_playlist != null)
            open_playlist.clearAllExposers();

        // stop the old playlist

        if (associated_playlist != null)
            associated_playlist.stopPlaylist(false);
        associated_playlist = other;

        // start the new one

        if (associated_playlist != null)
        {
            associated_playlist.startPlaylist();
            name = associated_playlist.getPlaylistName();
            num_tracks = associated_playlist.getNumTracks();
            track_index = associated_playlist.getCurrentIndex();
            my_shuffle = associated_playlist.getMyShuffle();

            // build the sparse array

            for (int i = 0; i < num_tracks; i++)
                tracks_by_position.add(i,null);

            // espose the first track

            if (num_tracks > 0)
            {
                Track track = getCurrentTrack();
                if (track != null && open_playlist != null)
                    open_playlist.exposeTrack(track,true);
            }
        }
    }


    @Override
    public Track getPlaylistTrack(int index)
        // Overrides to use our own sparse by_position array
        // and double-cache them from LocalPlaylist ...
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
                track = associated_playlist.getPlaylistTrack(index);
                track.setPosition(index);   // in case it was mucked up
                track.setOpenId(next_open_id);
                tracks_by_position.set(index - 1,track);
                tracks_by_open_id.put(next_open_id++,track);
            }
        }

        return track;
    }


    @Override public Track insertTrack(int position, Track track)
        // we override insertTrack so that we can expose the
        // tracks that are added
    {
        Track result = super.insertTrack(position,track);
        if (result != null)
        {
            OpenPlaylist open_playlist = getHttpOpenPlaylist();
            if (open_playlist != null)
                open_playlist.exposeTrack(track,true);
        }
        return result;
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

            String dbg_title = "CurrentPlaylist(" + getPlaylistName() + ").getFetchRecords(" + initial_fetch + "," + num + "," + fetcher.getAlbumMode() + ") ";
            Utils.log(dbg_fetch,0, dbg_title +
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
                Utils.log(dbg_fetch,1,dbg_title + " invalidated fetcher resetting next=0 and num=" + (num + next_index));
                num = num + next_index;
                next_index = 0;
                records.clear();
            }

            while (next_index < num_tracks && num_added < num)
            {
                Track track = getPlaylistTrack(next_index + 1);    // one based
                if (track == null)
                {
                    Utils.error("Null track from getTrack(" + (next_index + 1) + " in " + dbg_title);
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


            Utils.log(dbg_fetch,1,dbg_title + " returning " + result + " with " + records.size() + " records");
            return result;
        }
    }



}   // class CurrentPlaylist

