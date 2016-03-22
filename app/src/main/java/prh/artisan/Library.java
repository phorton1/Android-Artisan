package prh.artisan;


import java.util.List;

import prh.types.libraryBrowseResult;

public interface Library
    // This is the base class for MediaServers Providers.
    //
    // It provides a hiearchical library of Folders and tracks
    // to other code in this program, particularly the aLibrary
    // activity/view.
    //
    // Derived classes include the ssdp MediaServer,
    // the LocalLibrary, and the RemoteLibrary.
    //
    // The derived LocalLibrary is also the provider
    // for our own public http dlna MediaServer service.
{
    String getName();

    boolean start();
    void stop();

    Track getTrack(String id);
    Folder getFolder(String id);


    libraryBrowseResult getSubItems(String id,int start,int count, boolean meta_data);
        // the id is presumed to be the id of a folder
        // and generally meta_data is false.
        //
        // meta_data == true means get the metadata for the single folder by id
        //      dunno if this is a list of all siblings or not, with total_matches
        //      for now it should be total_matches=1, num_returned=1
        //
        // may need to support rare case of meta_data and id actually being a trackId
        //      by a second "get" in the implementation

}
