package prh.utils;

import prh.artisan.Artisan;
import prh.types.recordList;


public class Fetcher implements Runnable
{
    private static int dbg_fetcher = 0;
    private static int SLEEP_FETCH_INTERNAL_MILLIS = 0;

    // types

    public interface FetcherUser
    {
        public boolean getFetchItems(int start,int count,Fetcher fetcher);
        // Do what ever is necessary to increase the number of records
        // Return false for errors.
    }

    // static vars

    private static int num_fetchers_running = 0;

    // member vars

    Artisan artisan;
    FetcherUser caller;
    recordList records;
    int num_to_fetch;
    int num_per_fetch;
    String dbg_title;
    int state = 0;
        // 0 = idle
        // 1 = running
        // 2 = stopping (keep results)
        // 3 = force_stop (dont keep result)

    public boolean forcingStop() { return state==3; }
    public recordList getRecords() { return records; };



    public Fetcher(
        Artisan artisan,
        FetcherUser caller,
        recordList records,         // the record list we will count records in
        int num_to_fetch,           // number of total records to get
        int num_per_fetch,          // number to get per call to getFetcherItems
        String dbg_title)           // a title to show in debugging
    {
        this.artisan = artisan;
        this.caller = caller;
        this.records = records;
        this.num_to_fetch = num_to_fetch;
        this.num_per_fetch = num_per_fetch;
        this.dbg_title = dbg_title;
    }


    public boolean getRunning()
    {
        return state > 0;
    }


    public void start()
    {
        Utils.log(dbg_fetcher,1,"FETCHER.start(" + dbg_title + ") called");
        if (getRunning())
        {
            Utils.warning(0,3,"Fetcher is already running");
            return;
        }
        if (records.size() >= num_to_fetch)
        {
            Utils.warning(0,3,"Fetcher.start() - nothing to do");
            return;
        }

        // assert state == 0
        // change to state == 1 (running);

        state = 1;
        Thread fetcher_thread = new Thread(this);
        fetcher_thread.start();

    }


    public void stop(boolean force)
    // stop it if it's running
    // client must wait for current fetch to finish
    {
        Utils.log(dbg_fetcher,1,"FETCHER.stop(" + dbg_title + ") called");
        if (state != 0)
            state = force ? 3 : 2;
    }


    public void run()
    {
        Utils.log(dbg_fetcher,2,"FETCHER.run(" + dbg_title + ") started num_records=" + records.size() + "  num_to_fetch=" + num_to_fetch);

        num_fetchers_running++;
        artisan.showArtisanProgressIndicator(true);
        while (state == 1 &&
            records.size() < num_to_fetch)
        {
            int start = records.size();
            int count = num_to_fetch - start;
            if (count > num_per_fetch)
                count = num_per_fetch;

            if (!caller.getFetchItems(start,count,this))
            {
                state = 0;  // failure
            }

            // give SOME time to the UI

            else if (state == 1 && SLEEP_FETCH_INTERNAL_MILLIS > 0)
            {
                Utils.sleep(SLEEP_FETCH_INTERNAL_MILLIS);
            }
        }
        Utils.log(dbg_fetcher,2,"FETCHER.run(" + dbg_title + ") finished");
        num_fetchers_running--;
        if (num_fetchers_running == 0)
            artisan.showArtisanProgressIndicator(false);
        state = 0;
    }


}   // class Fetcher