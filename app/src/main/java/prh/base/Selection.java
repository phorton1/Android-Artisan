package prh.base;

import android.view.LayoutInflater;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import prh.artisan.Artisan;
import prh.artisan.Fetcher;
import prh.artisan.Folder;
import prh.artisan.R;
import prh.artisan.Record;
import prh.artisan.Track;
import prh.types.recordList;
import prh.utils.Utils;

public class Selection extends HashMap<Record,Integer>
    // A selection of records must be homogeneous, that is,
    // it must contain all Folders or all Tracks, with no mix.
    // We remember the first type added, and enforce homogeneity.
{
    private static int dbg_sel = 0;

    private Artisan artisan;
    private String type = "";
    public String getType()  {return type; }


    public Selection(Artisan ma)
    {
        artisan = ma;
    }


    @Override public void clear()
    {
        super.clear();
        type = "";
    }

    public boolean getSelected(Record record)
    {
        Integer pos = get(record);
        if (pos == null) return false;
        return true;
    }

    public void setSelected(Record record, int position, Boolean b)
    {
        String put_type = (record instanceof Folder) ?
            "Folders" : "Tracks";
        if (!type.isEmpty() && !put_type.equals(type))
        {
            Utils.error("Attempt to create non-homogeneous selection");
            return;
        }
        if (b)
        {
            type = put_type;
            put(record,position);
        }
        else
        {
            remove(record);
            if (size() == 0)
                type = "";
        }
    }


    recordList getSelectedRecords()
    {
        recordList retval = new recordList();
        retval.addAll(this.keySet());

        Collections.sort(retval,new Comparator<Record>() {
            @Override
            public int compare(Record lhs,Record rhs)
            {
                return get(lhs) - get(rhs);
            }
        });

        return retval;
    }


    public recordList getSelectedTracks()
        // flatten the selection to a list of tracks
    {
        recordList records = getSelectedRecords();
        Utils.log(dbg_sel,0,"getSelectedTracks() selection=" + size() + " " + type);
        if (type.equals("Tracks"))
            return records;
        recordList retval = new recordList();
        for (Record rec:records)
            addTracksRecursive(retval,(Folder)rec);
        return retval;
    }


    private void addTracksRecursive(recordList list, Folder folder)
    {
        Utils.log(dbg_sel,0,"addTracksRecursive(" + folder.getTitle() + ")");
        recordList sub_items = getAllSubItems(folder);
        for (Record rec:sub_items)
        {
            if (rec instanceof Track)
            {
                list.add(rec);
            }
            else
            {
                addTracksRecursive(list,(Folder) rec);
            }
        }
    }



    private static int NUM_PER_FETCH = 300;
    private static int NUM_INITIAL_FETCH = 100;
        // for local library


    private recordList getAllSubItems(Folder folder)
    // must be called prior to first expansion
    // gets the children folders and adds them
    {
        Fetcher fetcher = folder.getFetcher();
        boolean our_fetcher = false;

        if (fetcher == null)
        {
            // Create a fetcher if its local.

            our_fetcher = true;
            fetcher = new Fetcher(
                artisan,
                folder,
                null,
                NUM_INITIAL_FETCH,
                NUM_PER_FETCH,
                "viewStack(" + folder.getTitle() + ")");
        }

        fetcher.start();
        while (fetcher.getState() == Fetcher.fetcherState.FETCHER_INIT ||
            fetcher.getState() == Fetcher.fetcherState.FETCHER_RUNNING)
        {
            Utils.log(0,0,"waiting for fetcher");
            Utils.sleep(200);
        }

        recordList records = fetcher.getRecordsRef();
        Utils.log(dbg_sel,0,"getAllSubItems() returning " + records.size() + " records");
        if (our_fetcher)
            fetcher.stop(true,false);
        return records;
    }




}   // Selection
