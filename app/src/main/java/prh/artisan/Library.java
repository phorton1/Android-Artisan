package prh.artisan;


import java.util.List;

public abstract class Library
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
    public abstract void start();
    public abstract void stop();

    public abstract Track getTrack(String id);
    public abstract Folder getFolder(String id);

    public abstract List<Record> getSubItems(String table,String id,int start,int count);

}
