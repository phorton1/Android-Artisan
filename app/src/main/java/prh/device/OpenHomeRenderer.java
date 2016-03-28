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

    public OpenHomeRenderer(Artisan artisan, SSDPSearchDevice ssdp_device)
    {
        super(artisan,ssdp_device);
        Utils.log(0,1,"new OpenHomeRenderer(" + ssdp_device.getFriendlyName() + "," + ssdp_device.getDeviceType() + "," + ssdp_device.getDeviceUrl());
    }

    public OpenHomeRenderer(Artisan artisan)
    {
        super(artisan);
    }

    @Override public void notifyPlaylistChanged() {}

    @Override public String getName() { return getFriendlyName(); }
    @Override public boolean startRenderer() { return false; }
    @Override public void stopRenderer()  {  }
    @Override public Volume getVolume()   { return null; }
    @Override public String getRendererState()    { return null; }
    @Override public String getRendererStatus()   { return null; }
    @Override public String getPlayMode() { return null; }
    @Override public String getPlaySpeed()    { return null; }
    @Override public int getTotalTracksPlayed()   { return 0; }
    @Override public boolean getShuffle() { return false; }
    @Override public boolean getRepeat()  { return false; }
    @Override public void setRepeat(boolean value)    {  }
    @Override public void setShuffle(boolean value)   {  }
    @Override public void pause() {  }
    @Override public void play()  {  }
    @Override public void stop()  {  }
    @Override public void incAndPlay(int offset)  {  }
    @Override public int getPosition()    { return 0;  }
    @Override public void seekTo(int progress)    {  }
    @Override public Track getTrack() { return null; }
    @Override public void setTrack(Track track, boolean interrupt_playlist)   {  }

}
