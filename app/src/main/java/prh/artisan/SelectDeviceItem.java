package prh.artisan;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

public class SelectDeviceItem extends RelativeLayout
{
    private Artisan artisan;


    public SelectDeviceItem(Context context,AttributeSet attrs)
    {
        super(context,attrs);
        artisan = (Artisan) context;
    }

    public void onFinishInflate()
    {
    }


}
