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
    // Currently
    //    All images are cached in memory.
    //    REMOTE IMAGES ARE WRITTEN THRU TO A DISK BASED CACHE.
    //         http...www.blah.com.8080.tthththth.jpg
    //    In memory the key is the URL
    //         res/2345678
    //         file://mnt/shared/mp3s/albums/Blues/New/Album/blah.jpg
    //
    // Future
    //    Prune the in-memory LRU cache
    //    Limit physical disk usage.
    //
    // Requests to associate a remote image with an ImageView are asynchronous.
    // So, by the time the request is fulfilled, the view may have been recycled,
    // and the imageView is no longer interested in the given image. Therefore
    // we keep track of the most recent image requested for each imageView, and
    // only actually display the most recently requested on for the imageView.
{
    private Artisan artisan;
    private String image_url;
    private ImageView image_view;

    private String cacheKey()
    {
        return image_url.replaceAll(":|/",".");
    }

    // STATIC PUBLIC API

    private static class NullableBitmap
    {
        private Bitmap bitmap = null;

        public Bitmap getBitMap() { return bitmap; }

        public NullableBitmap(Bitmap bm)
        {
            bitmap = bm;
        }
    }

    private static HashMap<String,NullableBitmap> image_cache = new HashMap<>();
    private static HashMap<ImageView,String> last_image_url = new HashMap<>();


    public static void loadImage(Artisan ma, ImageView image, int res_id)
        // local cache of resource bitmaps
    {
        NullableBitmap found = image_cache.get("res/" + res_id);
        Bitmap bitmap = null;
        if (found != null)
        {
            bitmap = found.getBitMap();
        }
        else
        {
            bitmap = BitmapFactory.decodeResource(ma.getResources(),res_id);
            // Utils.log(0,0,"adding cached_image res/" + res_id);
            // image_cache.put("res/" + res_id,new NullableBitmap(bitmap));
        }

        // should never be null for a resource
        // set the image immediately

        if (bitmap == null)
             Utils.warning(0,0,"Null bitmap trying to get image at resource_id=" + res_id);
        setImage(ma,image,bitmap);
    }




    public static void loadImage(Artisan ma, ImageView image, String url)
        // if image is in the cache, or
        // the file is local, load it directly.
        // OTHERWISE, CHECK THE DISK CACHE, AND IF FOUND, READ IT FROM THERE
        // create a thread to load it.
    {
        NullableBitmap found = image_cache.get(url);
        if (found != null)
        {
            Bitmap bitmap = found.getBitMap();
            setImage(ma,image,bitmap);
        }
        else if (url.startsWith("http://"))
        {
            last_image_url.put(image,url);
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

            // image_cache.put(url,new NullableBitmap(bitmap));
            setImage(ma,image,bitmap);
        }
    }


    // PRIVATE THREAD UI

    private ImageLoader(Artisan ma,ImageView item,String url)
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

            // save the image to the cache, and
            // if the view is still interested, set it

            Utils.log(0,0,"image_cache adding " + (bitmap==null?"null":bitmap.getByteCount()) + " byte bitmap from " + image_url);
            image_cache.put(image_url,new NullableBitmap(bitmap));
            if (image_url.equals(last_image_url.get(image_view)))
            {
                setImage(artisan,image_view,bitmap);
                last_image_url.remove(image_view);
            }
        }
    }


    // PRIVATE RUN-ON-UI

    private static void setImage(
        Artisan artisan,
        final ImageView image_view,
        final Bitmap bitmap)
        // runOnUiThread() to set bitmap into image view
    {
        if (image_view != null && bitmap != null)
            artisan.runOnUiThread(new Runnable()
            {
                @Override  public void run()
                {
                    try
                    {
                        image_view.setImageDrawable(
                            new BitmapDrawable(bitmap));
                    }
                    catch (Exception e)
                    {
                        Utils.error("Setting image bitmap: " +e);
                    }
                }
            });
    }


}   // ImageLoader
