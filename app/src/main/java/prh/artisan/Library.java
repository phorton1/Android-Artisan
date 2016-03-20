package prh.artisan;


import java.util.List;

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

    List<Record> getSubItems(String table,String id,int start,int count);

}
