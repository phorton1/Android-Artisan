package prh.base;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import prh.artisan.Folder;
import prh.artisan.Record;
import prh.types.recordList;
import prh.utils.Utils;

public class Selection extends HashMap<Record,Integer>
    // A selection of records must be homogeneous, that is,
    // it must contain all Folders or all Tracks, with no mix.
    // We remember the first type added, and enforce homogeneity.
{
    String type = "";
    public String getType()  {return type; }


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


}   // Selection
