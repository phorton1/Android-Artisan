package prh.artisan.utils;

import prh.artisan.Folder;
import prh.artisan.Track;

public class unused_virtualFolderHelper
{
    private int num_virtual_folders = 0;
    private Folder last_virtual_folder = null;

    public int getNumVirtualFolders()
    {
        return num_virtual_folders;
    }

    public void initVirtualFolders()
    {
        num_virtual_folders = 0;
        last_virtual_folder = null;
    }


    public Folder addVirtualFolder(Track track)
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


}
