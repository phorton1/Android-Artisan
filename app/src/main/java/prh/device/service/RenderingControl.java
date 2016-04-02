package prh.device.service;

// Implemented as a Service ...
// whereas AVTransport is implemented in the MediaRenderer Device

// Note that BuP responds to our SetVolume actions,
// but that it uses the Android volume, which is
// not hooked up to the "real" MTC volume control
// on the Car Stereo, so if the Volume Fixer is not
// running on the Car Stereo, it may appear as if
// this remote object is not working, when it may be.
// All that is expected is that this will change
// BubbleUp's ANDROID volume.


import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.HashMap;

import prh.artisan.Artisan;
import prh.base.ArtisanEventHandler;
import prh.base.Volume;
import prh.artisan.VolumeControl;
import prh.device.Device;
import prh.device.SSDPSearchService;
import prh.device.Service;
import prh.utils.Utils;
import prh.types.stringHash;

public class RenderingControl extends Service implements Volume
{
    static int dbg_rc = 0;

    // static variables

    static String master_channel = "Master";
        // The required, and only channel we support

    static HashMap<Integer,String> var_names= new HashMap<Integer,String>();
    private static void putConfig(int idx, String var_name) { var_names.put(idx,var_name); }
        // a map of our id to a DLNA Variable Type (i.e. CTRL_VOL==Volume)
        // These are used directly on the service_description to get max values.
        // They are used to create the command names (i.e. GetVolume and SetVolume)
        // as well as the names of the parameters (DesiredVolume, CurrentVolume)
    static {
        putConfig(CTRL_VOL,"Volume");
        putConfig(CTRL_MUTE,"Mute");
        putConfig(CTRL_LOUD,"Loudness");
        putConfig(CTRL_BAL,"Balance");
        putConfig(CTRL_FADE,"Fade");
        putConfig(CTRL_BASS,"EQLow");
        putConfig(CTRL_MID,"EQMid");
        putConfig(CTRL_HIGH,"EQHigh"); }


    //----------------------------------------------------------------
    // runtime variables and Volume Interface
    //----------------------------------------------------------------

    private int[] max_values = null;
    private int[] current_values = null;

    @Override public int[] getMaxValues()
    {
        return max_values;
    }

    @Override public int[] getValues()
        // return a COPY!
    {
        return current_values.clone();
    }


    @Override public void setValue(int idx, int value)
    {
        doCommand("Set",idx,value);
    }

    @Override public void incDecValue(int idx, int inc)
    {
        int value = current_values[idx] + inc;
        doCommand("Set",idx,value);
    }


    @Override public void stop()
    {
        current_values = null;
    }

    @Override public void start()
    {
        current_values = new int[]{0,0,0,0,0,0,0,0};
        getUpdateValues();
        if (current_values[CTRL_VOL] == 0)
            Utils.warning(0,0,"Could not start the Rendering Control .. no Volume value returned from getUpdateValues()");
    }


    //----------------------------------------------------------------
    // Constructor
    //----------------------------------------------------------------

    public RenderingControl(
        Artisan artisan,
        Device device,
        SSDPSearchService ssdp_service )
    {
        super(artisan,device,ssdp_service);
        max_values = new int[]{0,0,0,0,0,0,0,0};

        // check the device for SETTABLE values

        Document service_description = ssdp_service.getServiceDoc();
        Element doc_ele = service_description.getDocumentElement();
        for (int i=0; i<NUM_CTRLS; i++)
            checkSetMax(doc_ele,i);

        if (max_values[CTRL_VOL] == 0)
            Utils.warning(0,0,"Will not start the Rendering Control .. no MAX_VOL return from DLNA service description");
    }




    private void checkSetMax(Element doc_ele, int idx)
        // if the SetAction exists, then get the
        // allowedValueRange maximum
    {
        String var_name = var_names.get(idx);
        if (Utils.getTagElementBySubTagValue(doc_ele,"action","name","Set" + var_name) != null)
        {
            // For Mute and Loudness, the presence of the SetAction is sufficient to set
            // max value to 1, for all others, we get the actual value from the descrip

            if (idx == CTRL_MUTE || idx == CTRL_LOUD)
                max_values[idx] = 1;
            else
            {
                Element var_ele = Utils.getTagElementBySubTagValue(doc_ele,"stateVariable","name",var_name);
                if (var_ele != null)
                {
                    String max = Utils.getTagValue(var_ele,"maximum");
                    if (!max.isEmpty())
                        max_values[idx] = Utils.parseInt(max);
                }
            }
        }
    }


    //---------------------------------------------------------------
    //  to and from string
    //---------------------------------------------------------------

    public RenderingControl(Artisan artisan, Device device)
    {
        super(artisan,device);
        max_values = new int[]{0,0,0,0,0,0,0,0};
    }


    @Override public String toString()
    {
        String result = "";
        for (int i=0; i<NUM_CTRLS; i++)
            result = result + max_values[i] + "\t";
        result = result + super.toString();
        return result;
    }

    @Override public boolean fromString(StringBuffer buffer)
    {
        for (int i = 0; i < NUM_CTRLS; i++)
            max_values[i] = Utils.parseInt(Utils.pullTabPart(buffer));
        super.fromString(buffer);
        return true;
    }



    //----------------------------------------------------------------
    // getUpdateValues() is called by VolumeControl loop
    //----------------------------------------------------------------
    // wow i'm still stunned.  It has to do upto 8 network hits
    // tho only called when the volume control is showing

    public int[] getUpdateValues()
        // must be able to get at least a Volume
        // value to start the control.
    {
        int old_values[] = current_values;
        current_values = new int[]{0,0,0,0,0,0,0,0};

        for (int i = 0; i<NUM_CTRLS; i++)
            if (max_values[i] > 0)  // filter by active control
                doCommand("Get",i,0);

        // if any changed, send the event

        if (VolumeControl.changed(old_values,current_values))
            artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_VOLUME_CHANGED,this);

        return current_values.clone();
    }


    public synchronized boolean doCommand(String cmd, int idx, int value)
    {
        String error_msg = "";
        stringHash args = new stringHash();
        String var_name = var_names.get(idx);
        String action = cmd + var_name;

        if (max_values[idx] == 0)
            return false;

        args.put("InstanceID","0");
        args.put("Channel",master_channel);

        if (cmd.equals("Get"))
        {
            Element doc_ele;
            String str_value;
            Document doc = (Document) getDevice().doAction(serviceType.RenderingControl,"Get" + var_name,args);
            doc_ele = doc == null ? null : doc.getDocumentElement();

            if (doc_ele == null)
                error_msg = "no document found";
            else if ((str_value = Utils.getTagValue(doc_ele,"Current" + var_name)).isEmpty())
                error_msg = "no value found";
            else
                current_values[idx] = Utils.parseInt(str_value);
        }

        else if (cmd.equals("Set"))
        {
            value = VolumeControl.valid(max_values,idx,value);
            if (value != current_values[idx])
            {
                args.put("Desired" + var_name,Integer.toString(value));
                if (null == getDevice().doAction(serviceType.RenderingControl,"Set" + var_name,args))
                    error_msg = "could not setValue(" + value + ")";
                else
                    current_values[idx] = value;

                // since it changed, set the value
                artisan.handleArtisanEvent(ArtisanEventHandler.EVENT_VOLUME_CHANGED,this);
            }
        }

        if (!error_msg.isEmpty())
        {
            Utils.warning(0,0,"Error in " + cmd + var_name + " idx=" + idx + "  " + error_msg);
            return false;
        }
        return true;

    }   // doCommand()

}   // class RenderingControl
