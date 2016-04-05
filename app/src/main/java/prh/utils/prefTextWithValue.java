package prh.utils;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import prh.artisan.Prefs;
import prh.artisan.R;

public class prefTextWithValue extends EditTextPreference
{
    private TextView text_view;

    public prefTextWithValue(Context context)
    {
        super(context);
        setLayoutResource(R.layout.preference_with_value);
        setDefaultValue(Prefs.defaultValue(Prefs.id.valueOf(getKey())));
    }

    public prefTextWithValue(Context context,AttributeSet attrs)
    {
        super(context, attrs);
        setLayoutResource(R.layout.preference_with_value);
        setDefaultValue(Prefs.defaultValue(Prefs.id.valueOf(getKey())));
    }

    public prefTextWithValue(Context context,AttributeSet attrs,int defStyle)
    {
        super(context, attrs, defStyle);
        setLayoutResource(R.layout.preference_with_value);
        setDefaultValue(Prefs.defaultValue(Prefs.id.valueOf(getKey())));
    }


    @Override protected void onBindView(View view)
    {
        super.onBindView(view);
        text_view = (TextView) view.findViewById(R.id.pref_value);
        if (text_view != null)
            text_view.setText(getText());
    }


    @Override public void setText(String text)
    {
        super.setText(text);
        if (text_view != null)
            text_view.setText(text);
    }

}   // class prefTextWithValue

