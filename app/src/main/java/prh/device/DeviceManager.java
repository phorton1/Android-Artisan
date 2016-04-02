package prh.device;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;

import prh.artisan.Artisan;
import prh.base.ArtisanEventHandler;
import prh.utils.Utils;
import prh.utils.httpUtils;


public class DeviceManager
    // A class that manages a list of Devices and their Services.
    //
    // These Devices and Services are Providers of information to
    // the Artisan so that it can function, as opposed to the http
    // "services", where Artisan acts as a network server of that
    // information.
    //
    // Devices are kept in a hash by their UUID, so all devices
    // must provide a uuid.  Devices have a low level actual
    // DEVICE_TYPE that corresponds to an actual java object.
    //
    // Devices have a DEVICE_GROUP which is sometimes the same
    // as the deviceType.  LocalRenderer, MediaServer, and
    // OpenHomeServer are all different device_types, but they
    // all share the deviceGroup of "MediaRenderer". Artisan
    // currently makes use of the following DEVICE_GROUPS:
    //
    //       MediaServer
    //       MediaRenderer
    //       PlaylistSource
    //
    //---------------------------------------------------------
    // Unique friendlyNames within a DEVICE_GROUP
    //---------------------------------------------------------
    // Devices have a friendlyName.  In order to prevent confusion
    // in the UI, we ensure that all Devices in a DeviceGroup have
    // unique friendlyNames by tacking 1,2,3 and so on onto the
    // friendly name when we encounter conflicts.
    //
    // For this reason, for the time being, the UUID is not used
    // in the user interface, though accessors are provided.
    // Currently, within this system, a Device can be uniquely
    // identified, and retrieved, given it's DEVICE_GROUP and NAME.
    //
    //------------------------------------------------------------
    // Tweaks
    //------------------------------------------------------------
    // In general we use the actual deviceType returned by SSDPSearch
    // when creating the device.  The exception is that I didn't want
    // a device called "Source", so the open home renderer is called
    // an OpenHomeRenderer.  This change happens during SSDPSearch and
    // is not known to DeviceManager or the rest of the system.
    //
    // The other low level change is that SSDPSearch changes the
    // friendlyName of "WDTVLive - currently in use" to WDTVLive.
    //
    //---------------------------------------------------------------
    // Caching and Local Devices
    //---------------------------------------------------------------
    // The DeviceManager does not know particularly about LocalDevices.
    // Local devices are created by Artisan and added() to the device_manager.
    // Thereafter they can be used polymorphically by the UI to select the
    // LocalLibrary (MediaServer), LocalRenderer (MediaRenderer) or
    // LocalPlaylistSource (PlaylistSource).
    //
    // Local Devices are not written to (and hence not read from) the
    // cache.
    //
    // The cache is read during DeviceManager construction.
    // Artisan is NOT evented about devices in the cache, or
    // that it adds to the DeviceManager.
    //
    //-------------------------------------------------------------------
    // SSDPSearch and Eventing
    //-------------------------------------------------------------------
    // At the end of Artisan onCreate(), and via a UI button, an SSDPSearch
    // may be initiated.  Artisan will receive a EVENT_NEW_DEVICE for any
    // new devices found in the process.  At the end of each SSDPSearch
    // artisan will receive an EVENT_SSDP_SEARCH_FINISHED.
    //
    // It is up to Artisan to maintain the notion of a currently
    // selected Device, and to start() and stop() the Devices themselves
    // when changing selections, and or shutting down.
{

    private static int dbg_dm = 1;
    private static int dbg_cache = 1;
    private static String cache_file_name = "device_cache.txt";
    public static boolean USE_DEVICE_CACHE = true;

    // DeviceHash type - hash of all Devices by uuid

    private class DeviceHash extends HashMap<String,Device> {}
    private class DeviceGroupHash extends HashMap<Device.deviceGroup,DeviceHash> {}

    // variables

    Artisan artisan;
    private int busy_count = 0;
    private int num_devices_added = 0;
    private SSDPSearch ssdp_search = null;
    private DeviceHash devices = new DeviceHash();
    private DeviceGroupHash devices_by_group = new DeviceGroupHash();


    //----------------------------------------------------
    // Constructor and Accessors
    //----------------------------------------------------

    public DeviceManager(Artisan ma)
    {
        artisan = ma;
        readCache();
    }

    public Device getDevice(String uuid)
    {
        return devices.get(uuid);
    }

    public Device getDevice(Device.deviceGroup group, String name)
    {
        Device device = null;
        DeviceHash hash = devices_by_group.get(group);
        if (hash != null)
            device = hash.get(name);
        return device;
    }

    public Device[] getDevices(Device.deviceGroup group)
    {
        Device rslt[] = new Device[0];
        DeviceHash hash = devices_by_group.get(group);
        if (hash != null)
        {
            Collection devices = hash.values();
            rslt = (Device[]) devices.toArray(new Device[hash.size()]);
        }
        return rslt;
    }


    public void addDevice(Device d)
    {
        Device.deviceGroup group = d.getDeviceGroup();
        DeviceHash hash = devices_by_group.get(group);
        if (hash == null)
        {
            hash = new DeviceHash();
            devices_by_group.put(group,hash);
        }
        hash.put(d.getFriendlyName(),d);
        devices.put(d.getDeviceUUID(),d);
    }



    //----------------------------------------------------
    // CacheFile
    //----------------------------------------------------

    public boolean writeCache()
        // called at the end of SSDP Search
        // local devices are not cached
    {
        boolean ok = true;
        String header = "";
        String devices = "";
        int num_devices = 0;
        Utils.log(dbg_cache,0,"DeviceManager.writeCache() called");

        for (Device.deviceGroup group: devices_by_group.keySet())
        {
            DeviceHash hash = devices_by_group.get(group);
            for (String name : hash.keySet())
            {
                Device device = hash.get(name);
                if (!device.isLocal())
                {
                    num_devices++;
                    header += device.getDeviceType().toString() + "\t";
                    devices += device.toString();
                }
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
            String device_type_str = Utils.pullTabPart(device_line);
            Device.deviceType device_type = Device.deviceType.DeviceNone;

            try
            {
                device_type = Device.deviceType.valueOf(device_type_str);
            }
            catch (Exception e)
            {
                Utils.warning(0,0,"illegal device type: " + device_type_str);
                return false;
            }

            Utils.log(dbg_cache,2,"readCache() CREATING(" + device_type + ") device");

            Device device = null;
            if (device_type == Device.deviceType.MediaServer)
                device = new MediaServer(artisan);
            else if (device_type == Device.deviceType.MediaRenderer)
                device = new MediaRenderer(artisan);
            else if (device_type == Device.deviceType.OpenHomeRenderer)
                device = new OpenHomeRenderer(artisan);

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
            // artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_NEW_DEVICE,device);

        }

        Utils.log(dbg_cache,0,"DeviceManager.readCache() finished");
        return true;
    }





    public void stop()
        // wait for any pending device search to finish
    {
        if (busy_count > 0)
        {
            int count = 0;
            int RETRIES = 8;
            while (busy_count>0 && count++ < RETRIES)
            {
                Utils.log(0,0,"waiting for ssdp search on deviceManager.stop() busy="  + busy_count);
                Utils.sleep(1000);
            }
            busy_count = 0;
        }
        ssdp_search = null;
    }



    public boolean searchInProgress()
    {
        return busy_count > 0;
    }

    public boolean canDoDeviceSearch()
    {
        return busy_count == 0;
    }


    public int incDecBusy(int inc)
    {
        busy_count += inc;
        if (inc == 1 && busy_count == 1)
        {
            num_devices_added = 0;
            artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_SSDP_SEARCH_STARTED,null);
        }
        else if (inc == -1 && busy_count == 0)
        {
            // finished!
            if (USE_DEVICE_CACHE && num_devices_added > 0)
                writeCache();
            artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_SSDP_SEARCH_FINISHED,null);
        }
        return busy_count;
    }



    public void doDeviceSearch(boolean clear_cache)
    {
        if (ssdp_search != null && busy_count > 0)
        {
            Utils.error("There is already a device search under way");
            return;
        }

        if (clear_cache)
        {
            artisan.restartDeviceSearch();
            devices = new DeviceHash();
            devices_by_group = new DeviceGroupHash();
            addDevice(artisan.getLocalLibrary());
            addDevice(artisan.getLocalRenderer());
            addDevice(artisan.getLocalPlaylistSource());
        }

        ssdp_search = new SSDPSearch(this,artisan);
        Thread search_thread = new Thread(ssdp_search);
        search_thread.start();
    }




    public void doDeviceCheck(String location, String usn)
        // Called from SSDPSearch and SSDPServer-via-notifyDeviceSSDP
        //
        // Check to see if the device with the given usn already exists,
        // and if not, if it is interesting and valid, start a thread
        // to create it.
        //
        // Makes sure to not create devices for our own http server.
    {
        // basic parameter validation

        if (usn != null && !usn.isEmpty() &&
            location != null && !location.isEmpty() &&
            !location.contains(Utils.server_ip + ":" + Utils.server_port))
        {
            // parse the usn, with more basic validation
            // and see if the device already exists

            String uuid = Utils.extract_re("uuid:(.*?):",usn);
            String urn = Utils.extract_re("urn:(.*?):",usn);
            String device_type_string = Utils.extract_re("device:(.*?):",usn);

            if (uuid.isEmpty())
                return;
            if (urn.isEmpty())
                return;
            if (device_type_string.isEmpty())
                return;

            // map "Source" device to our "OpenHomeRenderer"

            if (device_type_string.equals("Source"))
                device_type_string = "OpenHomeRenderer";

            // check if device exists

            Device exists = getDevice(uuid);
            if (exists == null)
            {
                // Device does not exist
                // See if its interesting, and just return if it is not
                // or give an error if we get an unexpected urn

                Device.deviceType device_type = null;
                String expected_urn = "";
                if (device_type_string.equals(Device.deviceType.MediaRenderer.toString()))
                {
                    expected_urn = httpUtils.upnp_urn;
                    device_type = Device.deviceType.MediaRenderer;
                }
                if (device_type_string.equals(Device.deviceType.MediaServer.toString()))
                {
                    expected_urn = httpUtils.upnp_urn;
                    device_type = Device.deviceType.MediaServer;
                }
                if (device_type_string.equals(Device.deviceType.OpenHomeRenderer.toString()))
                {
                    expected_urn = httpUtils.open_device_urn;
                    device_type = Device.deviceType.OpenHomeRenderer;
                }

                if (device_type == null)
                {
                    Utils.warning(dbg_dm,0,"un-interesting device type: " + device_type_string);
                    return;
                }

                if (!urn.equals(expected_urn))
                {
                    Utils.warning(0,0,"mismatched urn " + urn + " for " + device_type_string);
                    return;
                }

                // Device is interesting and has a valid urn and device_type.
                // Fire off the thread to check it out in more detail, which
                // will create it if so.

                Utils.log(dbg_dm,0,"firing off SSDPSearchDevice(" + device_type + ") at " + location);
                SSDPSearchDevice device = new SSDPSearchDevice(
                    this,
                    location,
                    device_type,
                    uuid,
                    urn);
                incDecBusy(1);
                Thread device_thread = new Thread(device);
                device_thread.start();
            }
            else
            {
                Utils.log(dbg_dm + 1,0,"Device Already Exists " + device_type_string + "::" + exists.getFriendlyName());
            }
        }
        else
        {
            Utils.warning(dbg_dm + 2,1,"Skipping reply from device(" + location + ") usn=" + usn);
        }

    }   // doDeviceCheck()




    public boolean createDevice(SSDPSearchDevice ssdp_device)
        // Called from SSDPSearch when a valid, createable,
        // Device and set of Services are found.
        //
        // We change the ssdp deviceType to our "short_type"
        // which is just MediaRenderer, MediaServer, etc, and
        // "OpenHomeRenderer".
        //
        // The OpenHomeRenderer is added to the hash as a
        // MediaRenderer. Clients can differentiate it by
        // asking it's actual deviceType.
    {
        String friendlyName = ssdp_device.getFriendlyName();
        Device.deviceType device_type = ssdp_device.getDeviceType();
        Device.deviceGroup device_group = ssdp_device.getDeviceGroup();
        String device_url = ssdp_device.getDeviceUrl();
        String icon_path = ssdp_device.getIconPath();

        String dbg_msg = "(" + device_group + (device_type.equals(device_group)?"":"("+device_type+")") + " " + friendlyName + ") at " + device_url;
        Utils.log(0,0,"CREATE_DEVICE" + dbg_msg);

        // See if the device already exists.
        // Should not happen, but just in case ...

        Device device = devices.get(ssdp_device.getDeviceUUID());
        if (device != null)
        {
            Utils.log(dbg_dm,3,"device already exists" + dbg_msg);
            return false;
        }


        // CREATE DEVICE
        // device is null, and needs to be created at this point
        // create the appropriate derived class

        if (device_type == Device.deviceType.MediaServer)
        {
            device = new MediaServer(artisan,ssdp_device);
        }
        else if (device_type == Device.deviceType.MediaRenderer)
        {
            device = new MediaRenderer(artisan,ssdp_device);
        }
        else if (device_type == Device.deviceType.OpenHomeRenderer)
        {
            device = new OpenHomeRenderer(artisan,ssdp_device);
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
            num_devices_added ++;
            device.createSSDPServices(ssdp_device);
            addDevice(device);
            artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_NEW_DEVICE,device);
            return true;
        }
        return false;
    }


    //-------------------------------------------------
    // Notifications
    //-------------------------------------------------


    public void notifyDeviceSSDP(String location, String usn, String action)
        // acttion="alive" || "bye_bye"
        //
        // Called when the SSDP Server receives an M_NOTIFY for
        // an interesting device: MediaServer, MediaRenderer, openSource.
        //
        // If the device already exists, we set it's online statua
        // given the action. If the status changed, dispatch
        // an artisan EVENT_DEVICE_STATUS_CHANGED message.
        //
        // As a note, Artisan does not wait for the UI thread to
        // take the current device offline and switch to the default.
        // It does this as soon as possible to try to stop hitting the
        // the device via fetchers, the Renderer, etc. and then immediately
        // issues the corresponding LIBRARY_CHANGED, RENDERER_CHANGED, etc
        // events.
        //
        // If the device does not exist, we call doCheckDevice() to possibly
        // create it and notify artisan via a NEW_DEVICE message.
    {
        Utils.log(dbg_dm,1,"processing M_NOTIFY(" + action + ") from " + location + " usn=" + usn);
        String device_uuid = Utils.extract_re("uuid:(.*?):",usn);
        Device device = getDevice(device_uuid);

        // Send EVENT_DEVICE_STATUS_CHANGED if device exists and
        // it came online, or went offline

        if (device != null)
        {
            Device.deviceStatus status = device.getDeviceStatus();
            Device.deviceStatus new_status =
                action.equals("alive") ? Device.deviceStatus.ONLINE :
                action.equals("bye_bye") ? Device.deviceStatus.OFFLINE :
                Device.deviceStatus.UNKNOWN ;

            if (status != new_status)
            {
                device.setDeviceStatus(new_status);
            }
        }

        // Use a thread to create an SSDPDevice and possibly
        // set it off to check out the device, possibly
        // creating it.

        else
        {
            doDeviceCheck(location,usn);
        }

    }   // notifyDeviceSSDP()


}   // class DeviceManager



