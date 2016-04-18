package prh.artisan;

import java.util.List;

import prh.base.Playlist;
import prh.types.trackList;
import prh.utils.Utils;


public class PlaylistFetcher extends Fetcher implements Fetcher.FetcherSource
    // Playlist Fetchers have an album_mode, which
    // allows the fetcher to build virtual folders
    // into the list of records as seen by aPlaylist.
    //
    // PlaylistFetchers implement the PlaylistFetcherSource API to
    // get the underlying records, and this class adds them and
    // to the fetcher record list, with virtual folders as needed.
{
    private static int dbg_plf = 1;

    // member vars

    private boolean album_mode;
    private PlaylistFetcherSource my_source;

    private int source_rec_changed_count_id;

    private int num_virtual_folders;
    private Folder last_virtual_folder;


    // PlaylistFetcherSource interface

    public interface PlaylistFetcherSource extends Playlist
        // a PlaylistFetcherSource presents a non-sparse
        // underlying list of actual existing records from
        // which the PlaylistFetcher can build the virtual
        // albums necessary. This record list is copied from
        // and the reference is kept strictly local to our
        // getFetchRecords()
   {
        public int getRecChangedCountId();
            // this is an id that is bumped when the Source
            // does not just Add more records, but is forced
            // to rebuild the entire list, so we have to
            // rebuild virtualFolders in that case.
            // Otherwise, we just continue with the virtual
            // folders we are already building.

       trackList getFetchedTrackList();
            // Set during construction, return a reference to a list
            // of records that will be used in a read-only manner.
            // The Source should create one if necessary, and may
            // assume that there is only one possible PlaylistFetcher
            // (i.e. it can have a single instance member).
            // Should never return null.

        public fetchResult getFetcherPlaylistRecords(int start, int num);
            // The Source returns a fetchResult, just like a normal Source
            // However, it does not modify the fetcher's list of records
            // directly, but instead, this object calls getFetchedTracks()
            // and adds those to the list
    }


    // ctor and simple accessors

    public PlaylistFetcher(
        Artisan artisan,
        PlaylistFetcherSource src,
        FetcherClient client,
        int num_initial_fetch,
        int num_per_fetch,
        String dbg_title)
    {
        super(artisan,null,client,num_initial_fetch,num_per_fetch,dbg_title);
        setSource(this);    // also sets is_dynamic_source
        my_source = src;
        album_mode = false;
        source_rec_changed_count_id = -1;
        initVirtualFolders();
    }


    void setPlaylistSource(PlaylistFetcherSource src)
    {
        my_source = src;
        source_rec_changed_count_id = -1;
        initVirtualFolders();
    }


    public boolean getAlbumMode()
    {
        return album_mode;
    }
    public void setAlbumMode(boolean on)
    {
        if (album_mode != on)
        {
            album_mode = on;
            source_rec_changed_count_id = -1;
        }
    }


    //----------------------------
    // virtual folders
    //----------------------------

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
            num_virtual_folders++;
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


    //-----------------------------------
    // getFetchRecords()
    //-----------------------------------

    public fetchResult getFetchRecords(Fetcher fetcher, boolean initial_call, int num)
        // if initial call the record list will have already been emptied
    {
        // Add the next bunch of records to the Fetcher

        trackList source_tracks = my_source.getFetchedTrackList();
        int num_tracks = source_tracks.size();

        Utils.log(dbg_plf,0,"PlaylistFetcher.getFetchRecords(" + fetcher.getTitle() +
            "," + initial_call + "," + num + ")");
        Utils.log(dbg_plf,1,"PlaylistFetcher.getFetchRecords(" + fetcher.getTitle() +
            ") base records size=" + records.size() + " and " +
            num_tracks + "/" + my_source.getNumTracks() + " tracks already gotten" +
            " " + (album_mode ? " with " + num_virtual_folders + " existing virtual folders" : ""));

        if (fetcher != this)
        {
            Utils.error(this.getTitle() + ".getFetchRecords() called with wrong fetcher!");
            return fetchResult.FETCH_ERROR;
        }

        // if initial_call, restart the virtual folders

        int next_index = num_tracks;
        if (initial_call)
        {
            next_index = 0;
            initVirtualFolders();
        }

        // CALL THE SOURCE PLAYLIST getFetcherPlaylistRecords

        Utils.log(dbg_plf,1,"PlaylistFetcher.getFetchRecords(" + fetcher.getTitle() + ") calling my_source.getFetcherPlaylistRecords() my_count_id=" + source_rec_changed_count_id);
        fetchResult result = my_source.getFetcherPlaylistRecords(next_index,num);
        int new_count_id = my_source.getRecChangedCountId();
        Utils.log(dbg_plf,1,"PlaylistFetcher.getFetchRecords(" + fetcher.getTitle() + ") my_getFetcherPlaylistRecords() returned " + result +
            " with count_id=" + new_count_id + " track_list has " + source_tracks.size() + " tracks");

        // RETURN IF ERROR

        if (result == fetchResult.FETCH_ERROR)
        {
            Utils.log(dbg_plf,1,"PlaylistFetcher.getFetchRecords(" + fetcher.getTitle() + ") returning FETCH_ERROR");
            return result;
        }

        // START OVER IF COUNT_ID CHANGED

        if (new_count_id != source_rec_changed_count_id)
        {
            Utils.log(dbg_plf,1,"PlaylistFetcher.getFetchRecords(" + fetcher.getTitle() + ") count_id changed ... starting over");
            next_index = 0;
            records.clear();
            initVirtualFolders();
            source_rec_changed_count_id = new_count_id;
        }

        // ADD RECORDS FROM SOURCE TO BASE CLASS
        // fast way if !album_mode
        // or slow way for album_mode

        int num_added = source_tracks.size() - next_index;
        Utils.log(dbg_plf,1,"PlaylistFetcher.getFetchRecords(" + fetcher.getTitle() + ") adding " + num_added + " tracks");

        if (num_added > 0)
        {
            if (!album_mode)
            {
                List<Track> to_add = source_tracks.subList(next_index,source_tracks.size());
                records.addAll(to_add);
            }
            else
            {
                for (int i = next_index; i < source_tracks.size(); i++)
                {
                    Track track = source_tracks.get(i);
                    Folder folder = addVirtualFolder(track);
                    if (folder != null)
                        records.add(folder);
                    records.add(track);
                }

            }   // ! album_mode
        }   // num_added > 0

        // return to caller

        Utils.log(dbg_plf,1,"PlaylistFetcher.getFetchRecords(" + fetcher.getTitle() + ") returning " + result);
        return result;

    }   // PlaylistFetcher.getFetchRecords();

}   // class PlaylistFetcher
