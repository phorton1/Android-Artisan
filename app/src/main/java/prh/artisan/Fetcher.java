package prh.artisan;

import prh.utils.Utils;
import prh.types.recordList;


public class Fetcher implements Runnable

    // A Fetcher is an object that connects a source of Records
    //
    //         Playlist (i.e. LocalPlaylist)
    //         device.MediaServer (remote Library)
    //
    //     with a client that needs a list of Records:
    //
    //         aLibrary <-- device.MediaServer
    //         aPlaylist <-- Playlist
    //         http.OpenPlaylist <-- Playlist
    //
    //     to perform (possibly) long lasting operations to build
    //     the set of records and deliver them to the client.
    //
    // Some clients (aLibrary, aPlaylist) use a single fetcher,
    // but other clients (http.OpenPlaylist) may have multiple
    // concurrent active Fetchers.
    //
    // All fetchers start with an initial call by the client
    // to get some records, that is handled synchronously,
    // immediately returning a valid Fetcher, and initial set
    // of records, to the client.
    //
    // Thererafter, as long as there are more records to fetch, a Fetcher
    // either runs itself, asynchronously thru a thread/sleep loop,
    // or explicit via calls from the client, to get additional records
    // from the source, a chunk at a time.
    //
    // Base Fetchers run against "static" sources, like device.MediaServer,
    // that deliver a fixed set of records that does not change, and so those
    // fetchers can reach a state where there is no more (will never be anymore)
    // work to perform, and they can be considered to be "done".
    //
    // The dervied Playlist fetchers use sources whose underlying list of
    // records can change at any time. For example, while a client is
    // fetching a Playlist, an operation may occur on the playlist to add or
    // delete records. Therefore the source has to be able to notify the
    // Fetcher, of changes in the source list of records, so that the Fetcher
    // itself can do what is appropriate with regards to the client, and notify
    // the client with a possibly new, changed set of records.
    //
    // Fetchers can be long lived, and remain associated with the Source
    // for as long as the client remains interested.  Also in this regards,
    // the Source for a fetcher may go out of scope (i.e. the Playlist or
    // currently selected Library changes) in which case the Fetcher
    // must be stopped.
    //
    // Fetchers obviate the need to use ArtisanEvents between the Source
    // and the Client, as the client is tightly bound to the source, and
    // care must be taken in the ArtisanEvent scheme to both optimize,
    // and work correctly with Fetchers, when either the Source or Client
    // is either ArtisanEvent dispatcher, or recipient.
    //
    // It is worth discussing ownership of recordLists here.
    //
    // Care must be taken to understand the difference between using
    // a recordList by reference, and ownership. The Fetcher (and
    // likewise the ListItemAdapter) OWN lists of records, and users
    // of those objects MAY NOT CHANGE the record lists returned by them.
{
    private static int dbg_fetcher = 0;
    private static int SLEEP_FETCH_INTERNAL_MILLIS = 0;

    //-------------------------------------
    // Enums
    //-------------------------------------

    public static enum fetchResult
        // The result of a call by the Fetcher to the Source
        // asking for more records.
    {
        FETCH_RECS,
            // Additional records were found.
            // The client will be notified.
            // The loop will continue in state RUNNING

        FETCH_NONE,
            // The fetcher enters state IDLE or DONE.
            // The underlying data source has identified
            // that as a result of this call, no more
            // records could be obtained, and the fetch
            // is done, but the client does not need to
            // be notified.

        FETCH_DONE,
            // The fetcher enters state IDLE or DONE.
            // The underlying data source has identified
            // that as a result of this call, no more
            // records could be obtained, but additional
            // records were added, so the client needs to
            // be notified.

        FETCH_ERROR,
            // there was a FATAL error during the call to
            // getFetchRecords().  The fetcher will be
            // FORCED_STOPPED.  This should be returned
            // in case the source goes offline.

    }; // enum fetchResult



    public static enum fetcherState
        // The current state of the fetcher.
    {
        FETCHER_INIT,
            // the fetcher has been constructed,
            // but has not yet been started.

        //----------

        FETCHER_RUNNING,
            // A threaded fetcher is running

        //----------

        FETCHER_PAUSING,
            // Someone has told the fetcher to pause().
            // The current fetch in progress will finish
            // and the recordList will be updated, then
            // the fetcher loop itself will be stopped.
            //
            // Based on the state of the records, if done,
            // the fetcher will proceed to FETCHER_DONE,
            // FETCHER_IDLE, or FETCHER_PAUSED

        FETCHER_STOPPING,
            // Someone has told the fetcher to stop().
            // The current fetch in progress will finish
            // and the recordList will be updated, and
            // the fetcher loop will be stopped. The
            // fetcher will proceed to FETCHER_STOPPED.

        FETCHER_FORCE_STOP,
            // Someone has told the fetcher to stop immediately.
            // The source may be going out of scope, or the
            // the client may be being destroyed. Any fetch
            // in progress is stopped as soon as possible, and
            // care is taken to not access either the client
            // or the source while shutdown is in progress.
            // The fetcher will proceed to FETCHER_STOPPED.

        //----------

        FETCHER_DONE,
            // A call to getFetchRecords() in a fetcher identified
            // as having a static source, has returned FETCH_DONE,
            // as the source has identified that no more records
            // can be fetched, i.e. a Library has delivered
            // all the sub-items for a folder. Subsequent calls
            // to start() will do nothing.

        FETCHER_IDLE,
            // A call to getFetchRecords() has returned FETCH_DONE,
            // but the source is known to be dynamic, so the fetcher
            // enters an idle state. The onus is on the source to call
            // restart() if the underyling records change

        FETCHER_PAUSED,
            // The fetcher has entered a paused state.
            // start() will continue from where it left off.

        FETCHER_STOPPED,
            // The fetcher has been stopped.
            // The record list may still be valid.
            // The fetcher cannot cannot be started(),
            // but can be restarted().

    };  // enum fetcherState


    //------------------------------------
    // Fetcher Source Interface
    //------------------------------------
    // All client, source, and public API methods
    // are synchronized into atomic operations.

    public interface FetcherSource
        // A FetcherSource maintains a list of Records that
        // can be incrementally built and accessed. The Source
        // is presumed to be running at the time of the Fetcher
        // construction, and should call fetcherNotifySourceStopped()
        // if it is stopped out from underneath the fetcher.
    {
        public fetchResult getFetchRecords(Fetcher fetcher, boolean initial_call, int num);
            // Called by the Fetcher when it needs more records, either
            // synchronously, at construction, or asynchronously, by its
            // own looper. The call is expected to happen synchronously,
            // on the Fetcher's thread.
            //
            // The Source should add NUM more records to this Fetcher's
            // records, as returned by getRecordsRef() and return FETCH_RECS,
            // or FETCH_DONE if there are no more records to fetch, or
            // FETCH_ERROR if there is a problem.
            //
            // If initial_call, getRecordsRef() will return an empty list,
            // and the Source should add any already available records to
            // the Fetcher, before normally doing whatever it does to get
            // NUM records and return FETCH_RECS or FETCH_DONE.
    }


    // public void fetcherNotifySourceStopped()
    //     // Called by the Source if it is STOPPED
    //     // out from underneath us.
    // {
    //     Utils.log(dbg_fetcher,1,"fetcherNotifySourceStopped(" + dbg_title + ")");
    //     stop(true,false);
    // }



    //------------------------------------
    // FetcherClient interface
    //------------------------------------

    public interface FetcherClient
        // A FetcherClient builds the Fetcher, calls start(),
        // and if it returns true, gets the initial bunch of
        // records from the Fetcher, and displays them.
        // It then waits for notify calls.
    {
        public void notifyFetchRecords(Fetcher fetcher,fetchResult fetch_result);
            // Notify the client of a change to the record list.
            // The client can get, and use, recordList, from the fetcher.
            // The fetch_result contains the result of the most recent
            // call to the Source.

        public void notifyFetcherStop(Fetcher fetcher,fetcherState fetcher_state);
            // Notify the client that the fetcher is being STOPPED
            // or FORCE_STOPPED.  If it's a normal STOP, and there
            // is a fetch in progress, they will first receive a
            // notifyFetchRecords() with the last batch of records.
            // In any case, this means that the Fetcher should no
            // longer be used, as the Source, and the records,
            // are no longer valid.
    }


    //-------------------------------------------------------------------
    // Variables
    //-------------------------------------------------------------------
    // static vars

    private static int STOP_RETRIES = 120;
    private static int STOP_WAIT_MILLIES = 250;
        // wait up to 30 seconds for fetcher to stop

    // configuration variables

    private Artisan artisan;
    private FetcherSource source;
    private FetcherClient client;
    private int num_per_fetch;
    private int num_initial_fetch;
    private String dbg_title;
        // a string identifying this fetcher,
        // for debugging purposes, i.e. the name
        // of the folder or playlist being fetched.
        // and/or the user_agent for OpenHomeRenderer
        // clients

    private boolean is_dynamic_source;
        // True if this source's underlying set of records may change.
        // Set for PlaylistFetchers who call setSource()
        //
        // After getting all the records, Dynamic fetchers go into
        // an IDLE state, rather than a DONE state.
        //
        // Sources may call restart() when the underlying set of
        // records changes, and the fetcher will get all currently
        // available records, pass them to the client, and put the
        // fetcher into a RUNNING state until all records are fetched.

    // state variables.

    private fetcherState state;
    protected recordList records;
    private boolean in_fetch;


    protected void setSource(FetcherSource s)
        // only called by playlists
    {
        is_dynamic_source = true;
        source = s;
    }

    public void setClient(FetcherClient c)
        // called on cached fetchers in MediaServer
        // as they are associated with aLibrary
    {
        client = c;
    }

    //-------------------------------------------------------------------
    // Simple Public API
    //-------------------------------------------------------------------

    public Fetcher(
        Artisan artisan,
        FetcherSource source,
        FetcherClient client,
        int num_initial_fetch,
        int num_per_fetch,
        String dbg_title)
    {
        this.artisan = artisan;
        this.source = source;
        this.client = client;
        this.num_initial_fetch = num_initial_fetch;
        this.num_per_fetch = num_per_fetch;
        this.dbg_title = dbg_title;
        is_dynamic_source = false;

        in_fetch = false;
        state = fetcherState.FETCHER_INIT;
        records = new recordList();
    }


    // simple accessors
    // The album_mode is just a generic configuration
    // boolean passed between the Client and the Source.

    public fetcherState getState() { return state; };
    public String getTitle() { return dbg_title; }
    public Artisan getArtisan() { return artisan; }
    public FetcherSource getSource() { return source; }
    public void setRecords(recordList new_records) { records = new_records; }

    public int getNumRecords()
    {
        return records.size();
    }

    synchronized public recordList getRecordsRef()
        // called by sources, or knowledgeable clients
    {
        return records;
    }

    synchronized public recordList getRecords()
        // called by clients, returns a copy of the record list
    {
        recordList copy = new recordList();
        copy.addAll(records);
        return copy;
    }



    //------------------------------------------
    // client API
    //------------------------------------------

    public boolean stop_fetch()
        // called by Sources and run() itself to
        // see if the loop should be stopped
    {
        return
            state != fetcherState.FETCHER_INIT &&
            state != fetcherState.FETCHER_RUNNING;
    }


    public void pause(boolean wait_for_pause)
    {
        Utils.log(dbg_fetcher,0,"Fetcher.pause(" + dbg_title + ") called");
        if (state == fetcherState.FETCHER_RUNNING)
        {
            Utils.log(dbg_fetcher,1,"Fetcher.pause(" + dbg_title + ") setting FETCHER_PAUSING to stop the fetcher");
            setState(fetcherState.FETCHER_PAUSING);
            if (wait_for_pause)
                waitNotInFetch();
        }
        Utils.log(dbg_fetcher,0,"Fetcher.pause(" + dbg_title + ") finished");
    }


    public void stop(boolean force, boolean wait_for_stop)
    {
        Utils.log(dbg_fetcher,1,"Fetcher.stop(" + dbg_title + "," + force + ") called");
        if (state == fetcherState.FETCHER_RUNNING)
        {
            if (force)
                setState(fetcherState.FETCHER_FORCE_STOP);
            else
                setState(fetcherState.FETCHER_STOPPING);
            Utils.log(dbg_fetcher,1,"Fetcher.pause(" + dbg_title + ") set " + state + " to stop the fetcher");
            if (wait_for_stop)
                waitNotInFetch();
        }
        Utils.log(dbg_fetcher,1,"Fetcher.stop(" + dbg_title + "," + force + ") finished");
    }   // Fetcher.stop()



    public boolean start()
    {
        Utils.log(dbg_fetcher,0,"Fetcher(" + dbg_title + ").start() state=" + state);

        // check if the fetcher is currently running,
        // if so, stop it, and wait for the current request to finish
        // absorb the resultant state, mapping STOPPED back to init

        if (state == fetcherState.FETCHER_RUNNING)
        {
            Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").start() - stopping running fetcher");
            stop(false,true);
            if (state == fetcherState.FETCHER_STOPPED)
            {
                Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").start() - setting well stopped fetcher to FETCHER_INIT");
                setState(fetcherState.FETCHER_INIT);
            }
        }

        // decide what to do based on the state

        if (state == fetcherState.FETCHER_DONE)
        {
            Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").start() - fetcher is already DONE");
            Utils.log(dbg_fetcher,0,"Fetcher(" + dbg_title + ").start() returning true1");
            return true;
        }
        else if (state == fetcherState.FETCHER_PAUSED)
        {
            Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").start() - continuing PAUSED fetcher");
            // fall thru to start code ...
        }
        else if (state == fetcherState.FETCHER_IDLE)
        {
            Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").start() restarting IDLE fetcher");
            boolean rslt = restart();
            Utils.log(dbg_fetcher,0,"Fetcher(" + dbg_title + ").start() returning " + rslt + "2");
        }
        else if (state != fetcherState.FETCHER_INIT)
        {
            Utils.error("Fetcher(" + dbg_title + ").start() called state not DONE,PAUSED,IDLE,or INIT=" + state);
            return false;
        }

        // Initial call to getFetchRecords()

        fetchResult fetch_result;
        synchronized (this)
        {
            Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").start() calling getFetchRecords(true," + num_initial_fetch + ")");
            fetch_result = source.getFetchRecords(this,true,num_initial_fetch);
            Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").start() call to getFetchRecords() returned " + fetch_result);
        }

        // ERROR

        if (fetch_result == fetchResult.FETCH_ERROR)
        {
            Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").start() calling stop(true,false) due to " + fetch_result);
            stop(true,false);
            return false;
        }

        // GOT ALL (OR NO MORE) of the records

        if (fetch_result == fetchResult.FETCH_NONE ||
            fetch_result == fetchResult.FETCH_DONE)
        {
            fetcherState new_state =
                is_dynamic_source ?
                    fetcherState.FETCHER_IDLE :
                    fetcherState.FETCHER_DONE;

            Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").start() advancing state to " + new_state);
            setState(new_state);
            Utils.log(dbg_fetcher,0,"Fetcher(" + dbg_title + ").start() returning true3");
            return true;
        }

        // DID NOT GET ALL or NO MORE
        // start the threaded_fetcher and return true

        Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").start() starting threaded_fetcher ...");
        Thread fetcher_thread = new Thread(this);
        fetcher_thread.start();

        Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").start() returning true");
        return true;

    }   // Fetcher.start()



    public boolean restart()
    // different than start() on an IDLE or PAUSED fetcher,
    // this clears the records, resets to STATE_INIT,
    // and starts over ..
    {
        Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").restart() called");
        if (state != fetcherState.FETCHER_INIT)
        {
            Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").restart() re-initializing");
            stop(true,true);
            records.clear();
            setState(fetcherState.FETCHER_INIT);
        }
        boolean rslt = start();
        Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").restart() returning " + rslt);
        return rslt;
    }



    //-------------------------------------------------------------------
    // Private implementation
    //-------------------------------------------------------------------

    private void setState(fetcherState new_state)
    {
        if (new_state != state)
        {
            Utils.log(dbg_fetcher,1,"Fetcher.setState(" + new_state + "," + dbg_title + ") old_state=" + state );
            // if (new_state == fetcherState.FETCHER_STOPPED ||
            //     new_state == fetcherState.FETCHER_FORCE_STOP)
            // {
            //     client.notifyFetcherStop(this,new_state);
            // }
            state = new_state;
        }
    }


    private void waitNotInFetch()
    {
        int count = 0;
        while (count++ <= STOP_RETRIES && in_fetch)
        {
            Utils.log(dbg_fetcher,5,"waiting for threaded_fetcher(" + dbg_title + ") loop to finish");
            Utils.sleep(STOP_WAIT_MILLIES);
        }

        if (in_fetch)
        {
            Utils.error("Timed out waiting for threaded_fetcher(" + dbg_title + ") loop to finish");
            setState(fetcherState.FETCHER_STOPPED);
        }
    }


    @Override
    public void run()
    {
        in_fetch = true;
        Utils.log(dbg_fetcher,0,"Fetcher.run(" + dbg_title + ") called .. current num_records=" + records.size());

        setState(fetcherState.FETCHER_RUNNING);
        artisan.showArtisanProgressIndicator(true);

        while (state == fetcherState.FETCHER_RUNNING)
        {
            final fetchResult fetch_result;
            int num_recs_before_call = records.size();
            Utils.log(dbg_fetcher,0,"Fetcher.run(" + dbg_title + ") TOP OF LOOP current num_records=" + records.size());

            // CALL THE SOURCE TO GET THE RECORDS

            Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").run() calling getFetchRecords(false," + num_per_fetch + ")");
            fetch_result = source.getFetchRecords(this,false,num_per_fetch);
            Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").run() call to getFetchRecords() returned " + fetch_result);

            // ERROR

            if (fetch_result == fetchResult.FETCH_ERROR)
            {
                Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").run() calling stop(true,false) due to FETCH_ERROR");
                stop(true,false);
                setState(fetcherState.FETCHER_STOPPED);
                artisan.showArtisanProgressIndicator(false);
                in_fetch = false;
                Utils.log(dbg_fetcher,0,"Fetcher(" + dbg_title + ").run() finished1");
                return;
            }

            // SHORT ENDING IF STOPPED

            if (stop_fetch())
            {
                Utils.log(dbg_fetcher,0,"Fetcher(" + dbg_title + ").run() finished2 - short ending due to stop_fetch() state=" + state);
                artisan.showArtisanProgressIndicator(false);
                in_fetch = false;
                return;
            }

            // GOT ALL (OR NONE) of the records

            fetcherState new_state = state;
            if (fetch_result == fetchResult.FETCH_NONE ||
                fetch_result == fetchResult.FETCH_DONE)
            {
                new_state = is_dynamic_source ?
                    fetcherState.FETCHER_IDLE :
                    fetcherState.FETCHER_DONE;
                Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").run() setting new_state=" + new_state);
                setState(new_state);
            }

            // NOTIFY THE CLIENT OF RECORDS
            // Needs to run on the UI thread.
            // When this was not synchronized, and I turned off debugging
            // the ui would hang on the fetcher, as I think the below code
            // loaded up the UI thread faster than it could handle things.
            // adding this call to synchronize fixed it ... interesting ...

            if (client != null && fetch_result != fetchResult.FETCH_NONE) synchronized (this)
            {
                artisan.runOnUiThread(new Runnable()
                {
                    public void run()
                    {
                        Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").run() calling client.notifyFetchRecords(" + fetch_result + ")");
                        client.notifyFetchRecords(Fetcher.this,fetch_result);
                        Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").run() back from client.notifyFetchRecords(" + fetch_result + ")");
                    }
                });
            }
        }   // while FETCHER_RUNNING

        artisan.showArtisanProgressIndicator(false);
        in_fetch = false;
        Utils.log(dbg_fetcher,0,"Fetcher(" + dbg_title + ").run() finished2");

    }   // Fetcher.run()
}   // class Fetcher

