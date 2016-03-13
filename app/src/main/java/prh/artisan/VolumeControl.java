package prh.artisan;


// The entry point is static doVolumeControl() which
// brings up the volume control dialog box.  Internally the
// dialog box implements a second timer loop, using Runnable(),
// because it has to update the controls.
// The only control that appears valid on the emulator
// is the volume control.

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import prh.utils.Utils;


public class VolumeControl extends Dialog implements
    EventHandler,
    SeekBar.OnSeekBarChangeListener,
    View.OnClickListener
{
    private static int dbg_vol = 0;

    // working variables

    private Artisan artisan;
    private Volume volume = null;

    private int in_slider = -1;
        // 0-based control number of the currently grabbed slider.
        // We don't update the slider we are moving.
    private int ctrl_values[] = new int[]{0,0,0,0,0,0,0,0};
        // Local copy of the values for the controls


    //-----------------------------------------
    // Construction and Initialization
    //-----------------------------------------

    public VolumeControl(Artisan a)
    {
        super(a);
        artisan = a;
    }


    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Utils.log(dbg_vol,1,"VolumeControl::onCreate() started ...");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.volume_control);

        findViewById(R.id.button_close_volume).setOnClickListener(this);


        in_slider = -1;

        // set the initial max values and enable controls
        // the initial layout should be all sliders and buttons disabled

        Renderer renderer = artisan.getRenderer();
        volume = renderer.getVolume();
        init_controls();
        Utils.log(dbg_vol,1,"VolumeControl::onCreate() finished");

    }   // onCreateDialog()


    private void init_controls()
        // init_controls all controls based on a possibly null volume object
    {
        if (volume != null)
            ctrl_values = volume.getValues();

        init_slider(R.id.slider_vol,Volume.CTRL_VOL);
        init_slider(R.id.slider_bal,Volume.CTRL_BAL);
        init_slider(R.id.slider_fade,Volume.CTRL_FADE);
        init_slider(R.id.slider_bass,Volume.CTRL_BASS);
        init_slider(R.id.slider_mid,Volume.CTRL_MID);
        init_slider(R.id.slider_high,Volume.CTRL_HIGH);
        init_button(R.id.button_mute,Volume.CTRL_MUTE);
        init_button(R.id.button_loud,Volume.CTRL_LOUD);
    }


    private void init_slider(int id, int idx)
    {
        Boolean enabled = false;
        SeekBar seekbar = (SeekBar) findViewById(id);
        if (volume != null)
        {
            int max_values[] = volume.getMaxValues();
            if (max_values[idx] > 0)
            {
                seekbar.setOnSeekBarChangeListener(this);
                seekbar.setMax(max_values[idx]);
                seekbar.setProgress(ctrl_values[idx]);
                enabled = true;
            }
        }
        if (!enabled)
        {
            seekbar.setMax(0);
            seekbar.setProgress(0);
            seekbar.setOnSeekBarChangeListener(null);
        }

        seekbar.setEnabled(enabled);
    }


    private void init_button(int id, int idx)
    {
        Boolean enabled = false;
        Button btn = (Button) findViewById(id);
        if (volume != null)
        {
            if (volume.getMaxValues()[idx] > 0)
            {
                btn.setOnClickListener(this);
                btn.setSelected(ctrl_values[idx] > 0);
                enabled = true;
            }
        }

        if (!enabled)
        {
            btn.setOnClickListener(null);
            btn.setSelected(false);
        }
        btn.setEnabled(enabled);
    }

    //-----------------------------------------------
    // Helper Methods for derived Volumes
    //-----------------------------------------------

    public static int valid(int max_values[], int idx, int val)
    {
        int max = max_values[idx];
        String descrip = Volume.ctrl_names[idx];

        if (val < 0)
        {
            Utils.error("illegal value for " + descrip + "=" + val + " ... setting to zero");
            val = 0;
        }
        if (val > max)
        {
            Utils.error("illegal value for " + descrip + "=" + val + " ... setting to max=" + max);
            val = max;
        }
        // Utils.log(3,0,"valid(" + descrip + ") idx=" + idx + " val=" + val + " finished");
        return val;
    }


    //---------------------------------------------------
    // UpdateUI
    //---------------------------------------------------


    private void update_controls()
        // not called with a null volume
    {
        Utils.log(dbg_vol + 1,1,"update_controls() in_slider=" + in_slider);
        update_slider(Volume.CTRL_VOL);
        update_button(R.id.button_mute,Volume.CTRL_MUTE);
        update_button(R.id.button_loud,Volume.CTRL_LOUD);
        update_slider(Volume.CTRL_BAL);
        update_slider(Volume.CTRL_FADE);
        update_slider(Volume.CTRL_BASS);
        update_slider(Volume.CTRL_MID);
        update_slider(Volume.CTRL_HIGH);
        Utils.log(dbg_vol + 1,1,"update_controls() finished");
    }


    private void update_slider(int idx)
    {
        if (volume != null && in_slider != idx)
        {
            String ctrl_name = Volume.ctrl_names[idx];
            Utils.log(dbg_vol + 2,2,"update_slider(" + idx + "," + ctrl_name + ")=" + ctrl_values[idx]);

            int ctrl_id = seekBarId(idx);
            int label_id = seekBarLabelId(idx);
            SeekBar slider = (SeekBar) findViewById(ctrl_id);
            TextView text = (TextView) findViewById(label_id);

            slider.setProgress(ctrl_values[idx]);
            text.setText("" + ctrl_values[idx]);
        }
    }


    private void update_button(int id, int idx)
    {
        if (volume != null && in_slider != idx)
        {
            boolean is_mute = idx == Volume.CTRL_MUTE;
            String ctrl_name = is_mute ? "mute" : "loud";
            String offLabel = is_mute ? "Mute" : "Loud";
            String onLabel = is_mute ? "Mute Off" : "Loud Off";
            String label = (ctrl_values[idx] == 1) ? onLabel : offLabel;
            Utils.log(dbg_vol + 2,2,"update_button(" + idx + "," + ctrl_name + ")=" + ctrl_values[idx] + "=" + label);

            Button button = (Button) findViewById(id);
            button.setText(label);
        }
    }


    //--------------------------------------------------
    // Button Handlers
    //--------------------------------------------------

    @Override
    public void onClick(View v)
    {
        int id = v.getId();
        Utils.log(dbg_vol,0,"onClick()");
        if (id == R.id.button_mute) toggle_button(id,Volume.CTRL_MUTE);
        if (id == R.id.button_loud) toggle_button(id,Volume.CTRL_LOUD);
        if (id == R.id.button_close_volume) dismiss();
    }


    private void toggle_button(int id, int idx)
    {
        if (volume != null)
        {
            in_slider = idx;
            Utils.log(dbg_vol,1,"toggle_button(" + idx + ") old_value=" + ctrl_values[idx]);
            ctrl_values[idx] = (ctrl_values[idx] == 1) ? 0 : 1;
            // update_button(id,idx);
            volume.setValue(idx,ctrl_values[idx]);
            in_slider = -1;
        }
    }


    //--------------------------------------------------------
    // SeekBar Handling
    //--------------------------------------------------------

    private int seekBarIdx(SeekBar seekBar)
    {
        int idx = -1;
        int id = seekBar.getId();
        if (id == R.id.slider_vol) idx = Volume.CTRL_VOL;
        if (id == R.id.slider_bal) idx = Volume.CTRL_BAL;
        if (id == R.id.slider_fade) idx = Volume.CTRL_FADE;
        if (id == R.id.slider_bass) idx = Volume.CTRL_BASS;
        if (id == R.id.slider_mid) idx = Volume.CTRL_MID;
        if (id == R.id.slider_high) idx = Volume.CTRL_HIGH;
        return idx;
    }

    private int seekBarId(int idx)
    {
        int id = -1;
        if (idx == Volume.CTRL_VOL)  id = R.id.slider_vol;
        if (idx == Volume.CTRL_BAL)  id = R.id.slider_bal;
        if (idx == Volume.CTRL_FADE) id = R.id.slider_fade;
        if (idx == Volume.CTRL_BASS) id = R.id.slider_bass;
        if (idx == Volume.CTRL_MID)  id = R.id.slider_mid;
        if (idx == Volume.CTRL_HIGH) id = R.id.slider_high;
        return id;
    }

    private int seekBarLabelId(int idx)
    {
        int id = -1;
        if (idx == Volume.CTRL_VOL)  id = R.id.slider_vol_value;
        if (idx == Volume.CTRL_BAL)  id = R.id.slider_bal_value;
        if (idx == Volume.CTRL_FADE) id = R.id.slider_fade_value;
        if (idx == Volume.CTRL_BASS) id = R.id.slider_bass_value;
        if (idx == Volume.CTRL_MID)  id = R.id.slider_mid_value;
        if (idx == Volume.CTRL_HIGH) id = R.id.slider_high_value;
        return id;
    }


    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
    {
        if (in_slider >= 0)
        {
            int idx = seekBarIdx(seekBar);
            int label_id = seekBarLabelId(idx);
            TextView value_ctl = (TextView) findViewById(label_id);
            Utils.log(dbg_vol + 2,0,"onProgressChanged(" + idx + ")=" + progress);
            value_ctl.setText("" + progress);
            ctrl_values[idx] = progress;
        }
    }

    public void onStartTrackingTouch(SeekBar seekBar)
    {
        int idx = seekBarIdx(seekBar);
        in_slider = idx;
        Utils.log(dbg_vol + 1,0,"onStartTracking(" + idx + ")");
    }

    public void onStopTrackingTouch(SeekBar seekBar)
    {
        int idx = seekBarIdx(seekBar);
        Utils.log(dbg_vol,0,"onStopTracking(" + idx + ")=" + seekBar.getProgress());
        ctrl_values[idx] = seekBar.getProgress();
        if (volume != null)
            volume.setValue(idx,ctrl_values[idx]);
        in_slider = -1;
    }


    //---------------------------------------------------
    // Artisan Event Handling
    //---------------------------------------------------

    public void handleArtisanEvent(String event_id,Object data)
    {
        if (event_id.equals(EVENT_VOLUME_CONFIG_CHANGED))
        {
            volume = (Volume) data;
            init_controls();
        }
        else if (event_id.equals(EVENT_VOLUME_CHANGED))
        {
            volume = (Volume) data;

            // null is handled easiest by a re-init_controls

            if (volume == null)
            {
                init_controls();
            }
            else
            {
                int values[] = volume.getValues();
                for (int i = 0; i < Volume.NUM_CTRLS; i++)
                {
                    if (in_slider != i)
                        ctrl_values[i] = values[i];
                }
            }
            update_controls();

        }
    }



}   // class VolumeControl





