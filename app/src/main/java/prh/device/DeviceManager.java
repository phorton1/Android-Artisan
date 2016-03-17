package prh.device;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import prh.artisan.Artisan;
import prh.artisan.EventHandler;
import prh.utils.Utils;


public class DeviceManager
    // A class that manages a cached list of Devices and their
    // Services and which can do an SSDP scan for new ones.
    //
    // The LocalRenderer is always created upon start().
    // The LocalMediaServer may be created up start()
    // Existing cached Devices are created during start().
    //
    // Memory is cleaned up during stop().
    // Pointers to devices and services are invalid after stop()
    //
    // When Devices and Services are loaded, or found during the scan,
    // Artisan is notified about each one via handleArtisanEvent(EVENT_NEW_DEVICE,Device).
    // They are not automatically started().
    //
    // It is up to Artisan to maintain the notion of a currently
    // selected Device, and to start() and stop() the Devices theselves
    // when changing selections, and or shutting down.
    //
    // The LocalMediaRenderer and LocalMediaServer are not cached.
    // There are specific calls for Artisan to create these if it so chooses.
    //
    // CACHE NOT IMPLEMENTED YET
{
    private static int dbg_dm = 1;

    Artisan artisan;
    public class DeviceHash extends HashMap<String,Device> {}
    public class TypeHash extends HashMap<String,DeviceHash> {}
    TypeHash types = new TypeHash();
        // hash by device type of a hash by friendly name of Devices

    public DeviceHash getDevices(String type)
    {
        DeviceHash hash = types.get(type);
        return hash;
    }
    public Device[] getSortedDevices(String type)
    {
        Device rslt[] = new Device[0];
        DeviceHash hash = types.get(type);
        if (hash != null)
        {
            Collection devices = hash.values();
            rslt = (Device[]) devices.toArray(new Device[hash.size()]);
        }
        return rslt;
    }


    public Device getDevice(String type, String name)
    {
        Device device = null;
        DeviceHash hash = types.get(type);
        if (hash != null)
            device = hash.get(name);
        return device;
    }
    public void addDevice(Device d)
    {
        DeviceHash hash = getDevices(d.getDeviceType());
        if (hash == null)
        {
            hash = new DeviceHash();
            types.put(d.getDeviceType(),hash);
        }
        hash.put(d.getFriendlyName(),d);
    }

    public DeviceManager(Artisan ma)
    {
        artisan = ma;
    }


    public void stop()
        // wait for any pending device search to finishe
    {
    }



    public void doDeviceSearch()
    {
        SSDPSearch search = new SSDPSearch(this);
        Thread search_thread = new Thread(search);
        search_thread.start();
    }



    public void createDevice(SSDPSearch.SSDPDevice ssdp_device)
        // Called from SSDPSearch when a valid, createable,
        // Device and set of Services are found.
        //
        // We change the ssdp device_type to our "short_type"
        // which is just MediaRenderer, MediaServer, etc, and
        // "OpenHomeRenderer".
        //
        // The OpenHomeRenderer is added to the hash as a
        // MediaRenderer. Clients can differentiate it by
        // asking it's actual device_type.
    {
        String friendlyName = ssdp_device.getFriendlyName();
        String device_type = ssdp_device.getDeviceType();
        String device_url = ssdp_device.getDeviceUrl();
        String icon_url = ssdp_device.getIconUrl();

        device_type = device_type.replaceAll("^.*device:","");
        device_type = device_type.replaceAll(":.*$","");
        if (device_type.equals("Source"))
            device_type = Device.DEVICE_OPEN_HOME;

        String list_type = device_type;
        if (device_type.equals(Device.DEVICE_OPEN_HOME))
            list_type = Device.DEVICE_MEDIA_RENDERER;

        String dbg_msg = "(" + list_type + (device_type.equals(list_type)?"":"("+device_type+")") + " " + friendlyName + ") at " + device_url;
        Utils.log(dbg_dm,2,"createDevice" + dbg_msg);

        // See if the device already exists.
        // We don't overwrite existing devices.
        // If you need to, clear the cache and re-scan.

        DeviceHash devices = getDevices(list_type);
        Device device = devices == null ? null : devices.get(friendlyName);
        if (device != null)
        {
            Utils.log(dbg_dm,3,"device already exists" + dbg_msg);
            return;
        }


        // CREATE DEVICE
        // device is null, and needs to be created at this point
        // create the appropriate derived class

        if (device_type.equals(Device.DEVICE_MEDIA_SERVER))
        {
            device = new MediaServer(artisan,friendlyName,device_type,device_url,icon_url);
        }
        else if (device_type.equals(Device.DEVICE_MEDIA_RENDERER))
        {
            device = new MediaRenderer(artisan,friendlyName,device_type,device_url,icon_url);
        }
        else if (device_type.equals(Device.DEVICE_OPEN_HOME))
        {
            device = new OpenHomeRenderer(artisan,friendlyName,device_type,device_url,icon_url);
        }
        else
        {
            Utils.error("Don't know how to create device type(" + device_type + ")");
        }

        // DEVICE CREATED
        // create it's default ssdp services
        // add it to the list
        // and notify Artisan

        if (device != null)
        {
            device.createSSDPServices(ssdp_device);
            addDevice(device);
            artisan.handleArtisanEvent(EventHandler.EVENT_NEW_DEVICE,device);
        }
    }



}   // class DeviceManager



