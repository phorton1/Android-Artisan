package prh.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;

import prh.artisan.Artisan;


public class ImageLoader extends Thread
    // The icon loader must run on a separate thread from the UI
    // for network URLs, and the View must always be updated from
    // the UI thread ...
    //
    // Will eventually implement the ImageCache ...
{
    private String image_url;
    private ImageView image_view;
    private Artisan artisan;

    // STATIC PUBLIC API

    private class NullableBitmap
    {
        private Bitmap bitmap = null;

        public Bitmap getBitMap() { return bitmap; }

        public NullableBitmap(Bitmap bm)
        {
            bitmap = bm;
        }
    }

    private static HashMap<String,NullableBitmap> image_cache = new HashMap<>();




    public static void loadImage(Artisan ma, ImageView image, String url)
        // if the file is local, load it directly, otherwise,
        // create a thread to load it.
    {
        NullableBitmap found = image_cache.get(url);
        if (found != null)
        {
            Bitmap bitmap = found.getBitMap();
            if (bitmap != null)
                setImage(ma,image,bitmap);
        }
        else if (url.startsWith("http://"))
        {
            ImageLoader loader = new ImageLoader(ma,image,url);
            Thread image_thread = new Thread(loader);
            image_thread.start();
        }
        else
        {
            Bitmap bitmap = null;
            String use_url = url.replace("file://","");
            try
            {
                InputStream in = new FileInputStream(use_url);
                bitmap = BitmapFactory.decodeStream(in);
            }
            catch (Exception e)
            {
                Utils.warning(0,0,"Could not load Local image:" + e.getMessage());
            }
            if (bitmap != null)
                setImage(ma,image,bitmap);
        }
    }


    // PRIVATE THREAD UI

    private ImageLoader(Artisan ma, ImageView item, String url)
    {
        artisan = ma;
        image_view = item;
        image_url = url;
    }

    public void run()   // NOT the ui-thread
    {
        //synchronized (artisan)
        {
            Bitmap bitmap = null;
            try
            {
                InputStream in = new java.net.URL(image_url).openStream();
                bitmap = BitmapFactory.decodeStream(in);
            }
            catch (Exception e)
            {
                Utils.warning(1,0,"Could not load image:" + e.getMessage());
            }

            // call back to the UI thread

            image_cache.put(image_url,new NullableBitmap(bitmap));
            if (bitmap != null)
                setImage(artisan,image_view,bitmap);
        }
    }


    // PRIVATE RUN-ON-UI

    private static void setImage(
        Artisan artisan,
        final ImageView image_view,
        final Bitmap image_bitmap)
        // runOnUiThread() to set bitmap into image view
    {
        artisan.runOnUiThread(new Runnable()
        {
            @Override  public void run()
            {
                image_view.setImageDrawable(
                    new BitmapDrawable(image_bitmap));
            }
        });
    }


}   // ImageLoader
