package prh.device;

import prh.artisan.Artisan;
import prh.artisan.Playlist;
import prh.artisan.PlaylistSource;
import prh.artisan.Renderer;
import prh.artisan.Track;
import prh.artisan.Volume;
import prh.utils.Utils;

public class OpenHomeRenderer extends Device implements Renderer
{

    public OpenHomeRenderer(Artisan artisan, SSDPSearch.SSDPDevice ssdp_device)
    {
        super(artisan,ssdp_device);
        Utils.log(0,0,"new OpenHomeRenderer(" + ssdp_device.getFriendlyName() + "," + ssdp_device.getDeviceType() + "," + ssdp_device.getDeviceUrl());
    }

    public OpenHomeRenderer(Artisan artisan)
    {
        super(artisan);
    }

    public String getName() { return getFriendlyName(); }
    public boolean startRenderer() { return false; }

    public void stopRenderer()  {  }
    public Volume getVolume()   { return null; }
    public String getRendererState()    { return null; }
    public String getRendererStatus()   { return null; }
    public String getPlayMode() { return null; }
    public String getPlaySpeed()    { return null; }
    public int getTotalTracksPlayed()   { return 0; }
    public boolean getShuffle() { return false; }
    public boolean getRepeat()  { return false; }
    public void setRepeat(boolean value)    {  }
    public void setShuffle(boolean value)   {  }
    public void pause() {  }
    public void play()  {  }
    public void stop()  {  }
    public void incAndPlay(int offset)  {  }
    public int getPosition()    { return 0;  }
    public void seekTo(int progress)    {  }
    public Track getTrack() { return null; }
    public void setTrack(Track track, boolean interrupt_playlist)   {  }
    public void setPlaylist(Playlist playlist)  { ; }
    public void setPlaylistSource(PlaylistSource playlistsource) {  }
    public Playlist getPlaylist() { return null; }
    public PlaylistSource getPlaylistSource() { return null; }

}
