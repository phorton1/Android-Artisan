package prh.utils;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;

import prh.artisan.Prefs;
import prh.artisan.R;

public class prefCheckBox extends CheckBoxPreference
{

    public prefCheckBox(Context context)
    {
        super(context);
        setLayoutResource(R.layout.preference_with_value);
        setupDefaultValue();
    }

    public prefCheckBox(Context context,AttributeSet attrs)
    {
        super(context, attrs);
        setLayoutResource(R.layout.preference_with_value);
        setupDefaultValue();
    }

    public prefCheckBox(Context context,AttributeSet attrs,int defStyle)
    {
        super(context,attrs,defStyle);
        setLayoutResource(R.layout.preference_with_value);
        setupDefaultValue();
    }


    private void setupDefaultValue()
    {
        String def = Prefs.defaultValue(Prefs.id.valueOf(getKey()));
        Boolean b = Utils.parseInt(def) > 0;
        setDefaultValue(b);
    }

}
