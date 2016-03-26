package prh.utils;

import prh.artisan.Artisan;
import prh.artisan.Playlist;
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
    // Some Fetchers run against "static" sources, like device.MediaServer,
    // that deliver a fixed set of records that does not change, and so those
    // fetchers can reach a state where there is no more (will never be anymore)
    // work to perform, and they can be considered to be "done".
    //
    // Some Fetchers use sources whose underlying list of records can
    // change at any time. For example, while a client is fetching a Playlist,
    // an operation may occur on the playlist to add or delete records.
    // Therefore the source has to be able to notify the Fetcher, of
    // changes in the source list of records, so that the Fetcher itself
    // can do what is appropriate with regards to the client, and notify
    // the client with a poossibly new, changed set of records.
    //
    // So Fetchers can be long lived, and remain associated with the Source
    // for as long as the client remains interested.  Also in this regards,
    // the Source for a fetcher may go out of scope (i.e. the Playlist or
    // currently selected Library changes) in which case the Fetcher essentially
    // has to start over.
    //
    // Fetchers obviate the need to use ArtisanEvents between the Source
    // and the Client, as the client is tightly bound to the source, and
    // care must be taken in the ArtisanEvent scheme to both optimize,
    // and work correctly with Fetchers, when either the Source or Client
    // is either ArtisanEvent dispatcher, or recipient.
    //
    // It is worth discussing ownership of recordLists here.
    // Care must be taken to understand the difference between using
    // a recordList by reference, and ownership. The Fetcher (and
    // likewise the ListItemAdapter) OWN lists of records, and
    // users of those objects MAY NOT CHANGE the record list
    // returned by them.
    //

    //-------------------------------------------
    // Usage and Public Client API Recap
    //-------------------------------------------
    //
    //      public boolean start()
    //      public void stop(boolean force)
    //      public void pause()
    //      public recordList getRecords()
    //      public recordList getRecordsRef()
    //
    // These methods can be called by clients or sources on a fetcher.
    // They are protected against re-entrancy, and synchronized
    // with calls to clients and sources into atomic operations.
    //
    //
    //     boolean start()
    //         Called immediately after construction and to restart
    //         a possibly paused fetcher. On cold start, it does the
    //         initial fetch and returns true, indicating to the caller
    //         that the record list is current and can be used.
    //
    //         If called on an IDLE fetcher (that can be restarted)
    //         it simply restarts the thread. The caller may assume
    //         that the call did not change the record list, and that
    //         clients will be notified if the thread subsequently does.
    //
    //         if start() return false, it should be considered an error,
    //         and the fetcher is no longer valid, and should probably be
    //         disconnected.  THERE IS CURRENTLY NO WAY FOR MediaServer's
    //         fetcher(s) to notify the library that they have died.
    //
    //     stop(boolean force)
    //         Stop the fetcher.
    //         If force, do it as quickly as possible and do
    //              not make any more client or source calls
    //         otherwise, finish the fetch in progress
    //              add the records, if any
    //         Notify the client if it's a force_stop.
    //         Notify the client when STOPPED is reached
    //
    //     pause()
    //          Bring the fetcher to state IDLE by finishing
    //          the current fetch in progress.  The client is
    //          no longer interested, so will not be notified
    //          of the results of the new fetch.
    //
    //
    //  recordList getRecords()
    //       Return a cloned copy of the record list.
    //       To be used by client consumers of the records,
    //       to ensure that they cannot change the record list.
    //
    //  recordList getRecordsRef()
    //       Return the actual record list.
    //       Used by sources during calls to getFetchRecords
    //       so that they may activly modify the list of records.

{
    private static int dbg_fetcher = 1;
    private static int SLEEP_FETCH_INTERNAL_MILLIS = 0;

    // types

    public static enum fetchResult
        // The result of a call by the Fetcher to the Source
        // asking for more records.
    {
        FETCH_NONE,
            // no records were fetched,
            // but no error was returned
            // so the fetcher state is unchanged

        FETCH_RECS,
            // records were found, and added,
            // the previous records remain valid
            // so the new records are simple additions

        // FETCH_CHANGED,
            // the underlying source records changed
            // so the recordList has been invalidated.
            // the fetcher has essentially been restarted.

        FETCH_DONE,
            // the underlying data source has identified
            // that as a result of this call, no more
            // records could be obtained. For the client,
            // this is equivalent to FETCH_RECS if the number
            // of records changed during the call, as the client
            // still needs to be notified. The fetcher will now enter
            // state FETCHER_DONE or FETCHER_IDLE.

        FETCH_ERROR,
            // there was a FATAL error during the call to
            // getFetchRecords().  The fetcher will be stopped

    }; // enum fetchResult



    public static enum fetcherState
        // The current state of the fetcher.
    {
        FETCHER_INIT,
            // the fetcher has been constructed,
            // but has not yet been started.

        //----------

        FETCHER_DONE,
            // A call to getFetchRecords() in a fetcher identified
            // as having a static source, has returned FETCH_DONE,
            // as the source has identified that no more records
            // can be fetched, i.e. a Library has delivered
            // all the sub-items for a folder, and that subsequent
            // calls to the fetcher would be useless.

        FETCHER_IDLE,
            // A call to getFetchRecords() has returned FETCH_DONE,
            // but the source is known to be dynamic, so the fetcher
            // enters an idle state, and the onus is on the source
            // to notify the fetcher when it needs to resume or restart.

        FETCHER_RUNNING,
            // A threaded fetcher is running

        //----------

        FETCHER_STOPPING,
            // Someone has told the fetcher to stop().
            // The current fetch in progress will finish
            // and the recordList will be updated, then
            // the fetcher loop will be stopped.
            // The fetcher will proceed to FETCH_STOPPED.

        FETCHER_FORCE_STOP,
            // Someone has told the fetcher to stop immediately.
            // The source may be going out of scope, or the
            // the client may be being destroyed. Any fetch
            // in progress is stopped as soon as possible, and
            // care is taken to not access either the client
            // or the source while shutdown is in progress.
            // The fetcher will proceed to FETCH_STOPPED.

        FETCHER_STOPPED,
            // As a result of a call to stop(), the fetcher has
            // been stopped. The record list is really no longer valid,
            // as it may point to Folders and Tracks that are de-cached
            // from memory, although with Java reference counting,
            // they can still be accessed.
            //
            // This state is somewhat synonymous with FETCH_IDLE, in that,
            // theoretically the fetcher can be restarted, in practice,
            // the fetcher should not be used after it is STOPPED.

    };  // enum fetcherState


    //------------------------------------
    // interfaces
    //------------------------------------
    // All client, source, and public API methods
    // are synchronized into atomic operations.

    public interface FetcherSource
    {
        public boolean isDynamicFetcherSource();
            // returns true if this source's underlying set of records
            // may change (i.e. Playlist), false if it is static (i.e. Library)

        public fetchResult getFetchRecords(Fetcher fetcher, boolean initial_call, int num);
            // Called by the Fetcher when it needs more records,
            // either synchronously, at construction, or asynchronously,
            // by its own thred/timer loop, or indirectly by a client.
            //
            // The call is expected to happen synchronously, on the
            // Fetcher's thread.
            //
            // Returns an enum indicating what has happened to the
            // records.  The source *should* call fetcherAborting()
            // in any loops, and quit if it returns true, but should
            // otherwise attempt to finish the fetch.
            //
            // The source *may* call back to Fetcher methods like error(),
            // which can change the state of the fetcher directly.
    }


    public interface FetcherClient
    {
        public void notifyFetchRecords(Fetcher fetcher,fetchResult fetch_result);
            // Notify the client of a change to the record list.
            // The client can get the state, and recordList, from the fetcher.
            // The fetch_result describes what has happened to the
            // records since the last notification was sent to the client.

        public void notifyFetcherStop(Fetcher fetcher,fetcherState fetcher_state);
            // Notify the client that the fetcher is STOPPED
            // or being FORCE_STOP'd.  If it's a normal STOP,
            // the client will first receive a notifyFetchRecords()
            // if the stop() occurred during a fetch, with the
            // records accumulated to that point, so this notification
            // means that the fetcher should no longer be used.
            // if FORCE_STOPing, the client will receive two calls,
            // one for the FETCH_FORCE_STOP, and one for the
            // FETCHER_STOPPED.
    }


    //-------------------------------------------------------------------
    // Variables
    //-------------------------------------------------------------------
    // static vars

    private static int STOP_RETRIES = 120;
    private static int STOP_WAIT_MILLIES = 250;
        // wait up to 30 seconds for fetcher to stop


    // configuration variables

    Artisan artisan;
    FetcherSource source;
    FetcherClient client;
    int num_per_fetch;
    int num_initial_fetch;
    Playlist.fetchHow how_playlist;
    String dbg_title;
        // a string identifying this fetcher,
        // for debugging purposes, i.e. the name
        // of the folder or playlist being fetched.
        // and/or the user_agent for OpenHomeRenderer
        // clients

    // state variables.

    fetcherState state;
    recordList records;
    boolean in_fetch;


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

        in_fetch = false;
        state = fetcherState.FETCHER_INIT;
        records = new recordList();
        how_playlist = Playlist.fetchHow.DEFAULT;
    }

    // simple accessors

    public Playlist.fetchHow getHow()
    {
        return how_playlist;
    }
    public void setHow(Playlist.fetchHow how)
    {
        how_playlist = how;
    }

    public int getNumRecords()
    {
        return records.size();
    }

    synchronized public recordList getRecordsRef()
    // called by sources
    {
        return records;
    }

    synchronized public recordList getRecords()
    // called by clients
    {
        recordList copy = new recordList();
        copy.addAll(records);
        return copy;
    }

    public FetcherSource getSource()
    {
        return source;
    }

    public boolean stop_fetch()
    // called by sources from getFetchRecords() to
    // check if they should stop their loop.
    {
        if (state == fetcherState.FETCHER_FORCE_STOP ||
            state == fetcherState.FETCHER_STOPPED)
            return true;
        return false;
    }


    //-------------------------------------------------------------------
    // Functional Public API
    //-------------------------------------------------------------------

    public void pause()
    {
        Utils.log(dbg_fetcher,1,"Fetcher.pause(" + dbg_title + ")");

        if (state == fetcherState.FETCHER_RUNNING)
        {
            // IDLE will also stop the fetcher loop
            Utils.log(dbg_fetcher,1,"Fetcher.pause(" + dbg_title + ") setting IDLE to stop the fetcher");
            setState(fetcherState.FETCHER_IDLE);
        }
    }


    public void stop(boolean force, boolean wait_for_stop)
    {
        Utils.log(dbg_fetcher,1,"Fetcher.stop(" + dbg_title + "," + force + ")");
        if (state == fetcherState.FETCHER_RUNNING)
        {
            if (force)
                setState(fetcherState.FETCHER_FORCE_STOP);
            else
                setState(fetcherState.FETCHER_STOPPING);

            if (in_fetch && wait_for_stop)
            {
                int count = 0;
                while (count++ <= STOP_RETRIES && in_fetch)
                    // in_fetchstate != fetcherState.FETCHER_STOPPED)
                {
                    Utils.log(dbg_fetcher,5,"Fetcher.stop( + dbg_title +) waiting for threaded_fetcher to stop");
                    Utils.sleep(STOP_WAIT_MILLIES);
                }

                if (in_fetch) // state != fetcherState.FETCHER_STOPPED)
                {
                    Utils.error("Timed out waiting for FETCHER(" + dbg_title + ") to stop");
                    setState(fetcherState.FETCHER_STOPPED);
                }
            }
        }
    }   // Fetcher.stop()



    public boolean start()
    {
        // check if resuming previous fetcher

        if (state == fetcherState.FETCHER_DONE)
        {
            // assert !in_fetch
            Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").start() - fetcher is already DONE");
            return true;
        }
        else if (state == fetcherState.FETCHER_IDLE)
        {
            // assert !in_fetch
            Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").start() resuming IDLE fetcher");
            // fall thru to restart
            // case is just here for debugging
        }
        else if (state != fetcherState.FETCHER_INIT)
        {
            Utils.error("Fetcher(" + dbg_title + ").start() called in and state not DONE,IDLE,or INIT=" + state);
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
            Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").start() calling stop(true,false) due to FETCH_ERROR");
            stop(true,false);
            return false;
        }

        // GOT ALL (OR NO) of the records

        boolean is_dynamic_source = source.isDynamicFetcherSource();

        if (fetch_result == fetchResult.FETCH_NONE ||
            fetch_result == fetchResult.FETCH_DONE)
        {
            fetcherState new_state =
                is_dynamic_source ?
                    fetcherState.FETCHER_IDLE :
                    fetcherState.FETCHER_DONE;

            Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").start() advancing state to " + new_state);
            setState(new_state);
            return true;
        }

        // DID NOT GET ALL, START threaded_fetcher
        // and return true;

        Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").start() starting threaded_fetcher ...");
        Thread fetcher_thread = new Thread(this);
        fetcher_thread.start();
        return true;

    }   // Fetcher.start()



    //-------------------------------------------------------------------
    // Private implementation
    //-------------------------------------------------------------------

    private void setState(fetcherState new_state)
    {
        if (new_state != state)
        {
            Utils.log(dbg_fetcher,2,"Fetcher.setState(" + new_state + "," + dbg_title + ") old_state=" + state );
            if (new_state == fetcherState.FETCHER_STOPPED ||
                new_state == fetcherState.FETCHER_FORCE_STOP)
            {
                client.notifyFetcherStop(this,new_state);
            }
            state = new_state;
        }
    }


    @Override
    public void run()
    {
        in_fetch = true;
        Utils.log(dbg_fetcher,1,"Fetcher.run(" + dbg_title + ") called .. current num_records=" + records.size());

        setState(fetcherState.FETCHER_RUNNING);
        artisan.showArtisanProgressIndicator(true);

        while (state == fetcherState.FETCHER_RUNNING)
        {
            final fetchResult fetch_result;
            int num_recs_before_call = records.size();
            Utils.log(dbg_fetcher,2,"Fetcher.run(" + dbg_title + ") TOP OF LOOP current num_records=" + records.size());

            synchronized (this)
            {
                Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").run() calling getFetchRecords(false," + num_per_fetch + ")");
                fetch_result = source.getFetchRecords(this,false,num_per_fetch);
                Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").run() call to getFetchRecords() returned " + fetch_result);
            }

            // ERROR

            if (fetch_result == fetchResult.FETCH_ERROR)
            {
                Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").run() calling stop(true,false) due to FETCH_ERROR");
                stop(true,false);
                setState(fetcherState.FETCHER_STOPPED);
                artisan.showArtisanProgressIndicator(false);
                in_fetch = false;
                return;
            }

            // GOT ALL (OR NO) of the records

            boolean is_dynamic_source = source.isDynamicFetcherSource();

            if (fetch_result == fetchResult.FETCH_NONE ||
                fetch_result == fetchResult.FETCH_DONE)
            {
                fetcherState new_state =
                    is_dynamic_source ?
                        fetcherState.FETCHER_IDLE :
                        fetcherState.FETCHER_DONE;

                Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").run() advancing state to " + new_state);
                setState(new_state);

                if (!stop_fetch() &&
                    records.size() != num_recs_before_call)
                {
                    artisan.runOnUiThread( new Runnable() { public void run()
                    {
                        Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").run() calling client.notifyFetchRecords(" + fetch_result + ")");
                        client.notifyFetchRecords(Fetcher.this,fetch_result);
                    }});
                }

                artisan.showArtisanProgressIndicator(false);
                in_fetch = false;
                return;
            }

            // DID NOT GET ALL .. notify client of changes
            // and continue loop

            if (!stop_fetch())
            {
                artisan.runOnUiThread(new Runnable()
                {
                    public void run()
                    {
                        Utils.log(dbg_fetcher,1,"Fetcher(" + dbg_title + ").run() calling 2nd client.notifyFetchRecords(" + fetch_result + ")");
                        client.notifyFetchRecords(Fetcher.this,fetch_result);
                    }
                });
            }
        }

        artisan.showArtisanProgressIndicator(false);
        in_fetch = false;


    }   // Fetcher.run()
}   // class Fetcher

