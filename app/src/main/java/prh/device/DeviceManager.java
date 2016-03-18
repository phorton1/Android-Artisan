package prh.device;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
    // The cache is read during DeviceManager construction.
    // Thereafter, Artisan call doSSDPSearch(), and the
    // the SSDPSearch calls DeviceManager.createDevice()
    // for each new device it finds, which in turn sends
    // EVENT_NEW_DEVICE events back to Artisan.
    //
    // At the end of the SSDPSearch() Artisan is notified
    // via the EVENT_SSDP_SEARCH_FINISHED event.
    //
    // This class does not currently own, or know about the
    // LocalRenderer or LocalLibrary "devices".
    //
    // We currently keep separate hash of devices by device_type,
    // MediaRenderers and MediaServers.  This called the "list_type",
    // and may differ from the actual device_type (for the
    // OpenHomeRenderer, the list_type is still MediaRenderer).
    //
    // The hashes are currently by the device FRIENDLY_NAME,
    // which serves as the unique ID within Artisan. This really
    // PRH should be the UUID, but it works right now, so, later.
    //
    // One other wrinkle is that some devices give different names
    // at different times. Specifically the WDTVLive is sometimes
    // called "WDTVLive - currently in use", so we remove the
    // " - currently in use" portion.
    //
    // Memory is cleaned up during stop().
    // Pointers to devices and services are invalid after stop()
    //
    // It is up to Artisan to maintain the notion of a currently
    // selected Device, and to start() and stop() the Devices theselves
    // when changing selections, and or shutting down.
{
    private static int dbg_dm = 1;
    private static int dbg_cache = 0;

    private static String cache_file_name = "device_cache.txt";

    // types

    public class DeviceHash extends HashMap<String,Device> {}
    public class TypeHash extends HashMap<String,DeviceHash> {}
        // hash by device type of a hash by friendly name of Devices

    // variables

    Artisan artisan;
    private SSDPSearch ssdp_search = null;
    TypeHash types = new TypeHash();


    public DeviceManager(Artisan ma)
    {
        artisan = ma;
        readCache();
    }


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
        String list_type = d.getDeviceType();
        if (list_type.equals(Device.DEVICE_OPEN_HOME))
            list_type = Device.DEVICE_MEDIA_RENDERER;

        DeviceHash hash = getDevices(list_type);
        if (hash == null)
        {
            hash = new DeviceHash();
            types.put(list_type,hash);
        }
        hash.put(d.getFriendlyName(),d);
    }


    public boolean writeCache()
        // called at the end of SSDP Search
    {
        boolean ok = true;
        String header = "";
        String devices = "";
        int num_devices = 0;
        Utils.log(dbg_cache,0,"DeviceManager.writeCache() called");

        for (String type: types.keySet())
        {
            DeviceHash hash = types.get(type);
            for (String name : hash.keySet())
            {
                Device device = hash.get(name);
                num_devices++;
                header += device.getDeviceType() + "\t";
                devices += device.toString();
            }
        }

        Utils.log(dbg_cache,1,"DeviceManager.writeCache() writing " + num_devices + " devices");

        String result = "" +
            num_devices + "\n" +
            header + "\n" +
            devices;

        File cache_dir = artisan.getCacheDir();
        File output_file = new File(cache_dir,cache_file_name);
        if (false && !output_file.canWrite())
        {
            Utils.error("The device_cache.txt file is not writeable!!");
            return false;
        }

        try
        {
            OutputStream os = new FileOutputStream(output_file);
            os.write(result.getBytes());
            os.close();
        }
        catch (Exception e)
        {
            Utils.error("Could not write to device_cache.txt: " + e);
            return false;
        }

        Utils.log(dbg_cache,0,"DeviceManager.writeCache() finished");
        return true;

    }   // writeCache()


    private boolean readCache()
    {
        Utils.log(dbg_cache,0,"DeviceManager.readCache() called");

        File cache_dir = artisan.getCacheDir();
        File input_file = new File(cache_dir,cache_file_name);
        if (!input_file.exists())
        {
            Utils.warning(0,0,"The device_cache.txt does not exist!");
            return true;    // false;
        }

        Utils.log(dbg_cache,1,"readCache() reading " + input_file.length() + " bytes");
        int file_len = Utils.parseInt("" + input_file.length());
        byte buf[] = new byte[file_len];

        try
        {
            InputStream is = new FileInputStream(input_file);
            is.read(buf);
            is.close();
        }
        catch (Exception e)
        {
            Utils.error("Could not read device_cache.txt: " + e);
            return false;
        }



        StringBuffer buffer = new StringBuffer(new String(buf));
        Utils.log(dbg_cache,1,"readCache() parsing " + buffer.length() + " buffer bytes");

        // pull it apart

        StringBuffer first_line = Utils.readBufferLine(buffer);
        int num_devices = Utils.parseInt(first_line.toString());
        StringBuffer device_line = Utils.readBufferLine(buffer);

        Utils.log(dbg_cache,1,"readCache() creating " + num_devices + " devices");
        Utils.log(dbg_cache,2,"device_line = " + device_line);

        for (int i=0; i<num_devices; i++)
        {
            String device_type = Utils.pullTabPart(device_line);
            Utils.log(dbg_cache,2,"readCache() CREATING(" + device_type + ") device");

            Device device = null;
            if (device_type.equals(Device.DEVICE_MEDIA_SERVER))
                device = new MediaServer(artisan);
            else if (device_type.equals(Device.DEVICE_MEDIA_RENDERER))
                device = new MediaRenderer(artisan);
            else if (device_type.equals(Device.DEVICE_OPEN_HOME))
                device = new OpenHomeRenderer(artisan);
            else
            {
                Utils.error("unknown device type: " + device_type);
                return false;
            }

            Utils.log(dbg_cache,3,"readCache() calling device.fromString()");
            if (!device.fromString(buffer))
            {
                Utils.error("device(" + device_type + ").fromString() failed!");
                return false;
            }

            Utils.log(dbg_cache,3,"readCache() back from device.fromString()");
            Utils.log(dbg_cache,3,"adding device and sending NEW_DEVICE event");

            addDevice(device);
            // artisan explicitly checks after the device manager starts
            // artisan.handleArtisanEvent(EventHandler.EVENT_NEW_DEVICE,device);

        }

        Utils.log(dbg_cache,0,"DeviceManager.readCache() finished");
        return true;
    }




    public void stop()
        // wait for any pending device search to finishe
    {
    }


    public boolean canDoDeviceSearch()
    {
        return
            ssdp_search == null ||
            ssdp_search.finished();
    }


    public void doDeviceSearch()
    {
        if (ssdp_search != null &&
            !ssdp_search.finished())
        {
            Utils.error("There is already a device search under way");
            return;
        }
        ssdp_search = new SSDPSearch(this,artisan);
        Thread search_thread = new Thread(ssdp_search);
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
            Utils.log(0,3,"device already exists" + dbg_msg);
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



