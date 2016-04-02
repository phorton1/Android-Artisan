package prh.artisan.interfaces;


import prh.artisan.Folder;

public interface Library
    // This is the base class for Libraries that can be
    // used by aLibrary. The folders within it can be
    // associated with fetchers.
    //
    // Note that the LocalLibrary presents the getSubItems(),
    // getFolder(), and getTrack() APIs
{
    String getLibraryName();
    boolean startLibrary();
    void stopLibrary(boolean wait_for_stop);

    Folder getRootFolder();

// with a fetcher member.

}
