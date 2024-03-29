package prh.utils;

import android.os.Handler;

public class loopingRunnable
{
    private int dbg_looper = 0;

    public interface handler {
        public boolean continue_loop();
    }

    // default for the major Artisan loops

    public static final boolean DEFAULT_USE_POST_DELAYED = false;

    // construction params

    private String name;
    private handler owner;
    private Runnable runnable;
    private Object synchrnonizer;
    private int STOP_RETRIES;
    private int REFRESH_INTERVAL;
    private boolean USE_POST_DELAYED;
    private boolean ONE_TIME;

    // member variables

    private Handler handler = null;
    private runnableLooper looper = null;
    private boolean in_loop = false;
    private boolean stopping = false;


    private void init(
        String title,
        handler parent,
        Runnable to_run,
        int stop_retries,
        int refresh_interval,
        Boolean use_post_delayed,
        Object synchronizer)
    {
        name = title;
        owner = parent;
        runnable = to_run;

        ONE_TIME = false;
        STOP_RETRIES = stop_retries;
        REFRESH_INTERVAL = refresh_interval;
        USE_POST_DELAYED = use_post_delayed;
        this.synchrnonizer = synchronizer;
    }


    //--------------------------------
    // Ctors
    //--------------------------------

    public loopingRunnable(
        String title,
        Runnable to_run,
        int delay)
        // One shot PostDelayed Runnable.
        // Call stop() to quickly clear() the one-shot
        // Start can be called multiple times
    {
        init(title,null,to_run,0,delay,true,null);
        ONE_TIME = true;
    }


    public loopingRunnable(
        String title,
        handler parent,
        Runnable to_run,
        int stop_retries,
        int refresh_interval)
        // Normal long lived Runnable Loop with no synchronizer
    {
        name = title;
        owner = parent;
        runnable = to_run;

        init(title,parent,to_run,stop_retries,refresh_interval,DEFAULT_USE_POST_DELAYED,null);
    }

    public loopingRunnable(
        String title,
        handler parent,
        Runnable to_run,
        int stop_retries,
        int refresh_interval,
        Boolean use_post_delayed,
        Object synchronizer)
        // takes a synchronizer
        // and explicit value for use_post_delayed
    {
        init(title,parent,to_run,stop_retries,refresh_interval,use_post_delayed,synchronizer);
    }


    //--------------------------------------
    // Public API
    //--------------------------------------

    public void start()
    {
        // Start a thread with the runnableLooper directly,
        // or a delayedRunnableLooper wrapper around it

        Runnable the_run;
        if (USE_POST_DELAYED)
        {
            if (handler != null)
            {
                handler.removeCallbacks(looper);
                handler = null;
                looper = null;
            }
            the_run = new delayedRunnableLooper();
        }
        else
            the_run = looper = new runnableLooper();
        Thread thread = new Thread(the_run);
        thread.start();
    }



    public void stop(boolean wait_stop)
    {
        stopping = true;

        if (handler != null)
            handler.removeCallbacks(looper);
        handler  = null;
        looper = null;

        if (wait_stop)
        {
            int count = 0;
            while (in_loop && count++ < STOP_RETRIES)
            {
                Utils.log(0,0,"waiting for " + name + " to stop ...");
                Utils.sleep(REFRESH_INTERVAL);
            }
            if (in_loop)
                Utils.warning(0,0,"loopingRunnable(" + name + ") TIMED OUT WAITING FOR STOP");
        }
    }

    public boolean continue_loop()
    {
        boolean continue_loop =
            looper != null && !stopping;
        if (USE_POST_DELAYED && handler == null)
            continue_loop = false;
        if (continue_loop && owner != null)
            continue_loop = owner.continue_loop();
        return continue_loop;
    }


    //---------------------------------
    // implementation
    //---------------------------------

    private class delayedRunnableLooper implements Runnable
    {
        public delayedRunnableLooper()
        {
            // Looper.prepare();
            handler = new Handler();
            looper = new runnableLooper();
        }

        public void run()
        {
            handler.postDelayed(looper,REFRESH_INTERVAL);
        }
    }


    private class runnableLooper implements Runnable
        // if !USE_POST_DELAYED loop until stopped
        // if POST_DELAYED do one call, and return
    {

        public void run()
        {
            in_loop = true;
            // if (synchronizer == null || synchronized (this))
            {

                boolean cont = continue_loop();
                while (cont)
                {
                    Utils.log(dbg_looper+1,0,"Looper(" + name + ") calling client runnable");
                    runnable.run();
                    Utils.log(dbg_looper+1,0,"Looper(" + name + ") back from client runnable");

                    if (USE_POST_DELAYED || ONE_TIME)
                        cont = false;
                    else
                    {
                        cont = continue_loop();
                        if (cont)
                        {
                            Utils.log(dbg_looper+1,0,"Looper(" + name + ") sleeping(" + REFRESH_INTERVAL + ")");
                            Utils.sleep(REFRESH_INTERVAL);
                            cont = continue_loop();
                        }
                    }
                }

                if (USE_POST_DELAYED && !ONE_TIME && continue_loop())
                {
                    handler.postDelayed(this,REFRESH_INTERVAL);
                }

                if (stopping)
                    Utils.log(dbg_looper,2,"Looper(" + name + ")  stopped");

                in_loop = false;

            }
        }   // runnableLooper.run()

    }   // class runnableLooper

}   // class loopingRunnable


