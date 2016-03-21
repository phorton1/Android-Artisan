package prh.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;

import java.io.InputStream;

import prh.artisan.Artisan;

public class ImageLoader extends Thread
        // The icon loader must run on a separate thread from the UI
        // but the View must be updated from the UI thread ...
{
    private String image_url;
    private ImageView image_view;
    private Artisan artisan;

    public ImageLoader(Artisan ma, ImageView item, String url)
    {
        artisan = ma;
        image_view = item;
        image_url = url;
    }

    public void run()   // NOT the ui-thread
    {
        synchronized (artisan)
        {
            Bitmap image_bitmap = null;
            try
            {
                InputStream in = new java.net.URL(image_url).openStream();
                image_bitmap = BitmapFactory.decodeStream(in);
            }
            catch (Exception e)
            {
                Utils.warning(0,0,"Could not load image:" + e.getMessage());
            }

            // call back to the UI thread

            if (image_bitmap != null)
                setImage(image_view,image_bitmap);
        }
    }



    private void setImage(final ImageView image_view, final Bitmap image_bitmap)
    // runOnUiThread() to set bitmap into image view
    {
        artisan.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                image_view.setImageDrawable(
                    new BitmapDrawable(image_bitmap));
            }

        });
    }

}   // ImageLoader
