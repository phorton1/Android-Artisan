package prh.device.service;


import org.w3c.dom.Document;

import prh.artisan.Artisan;
import prh.device.Device;
import prh.device.SSDPSearch;
import prh.device.SSDPSearchService;
import prh.device.Service;

public class OpenTime extends Service
{
    // state and accessors

    private int elapsed = 0;
    private int duration = 0;
    private int total_tracks_played = 0;

    public int getElapsed() { return elapsed; }
    public int getDuration() { return duration; }
    public int getTotalTracksPlayed()  { return total_tracks_played; }

    // Constructors

    public OpenTime(
        Artisan artisan,
        Device device)
    {
        super(artisan,device);
    }

    public OpenTime(
        Artisan artisan,
        Device device,
        SSDPSearchService ssdp_service )
    {
        super(artisan,device,ssdp_service);
    }






}
