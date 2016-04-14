package prh.types;

import java.util.HashMap;

import prh.artisan.Folder;
import prh.artisan.Record;
import prh.utils.Utils;

public class selectedHash extends HashMap<Record,Boolean>
    // A selection of records must be homogeneous, that is,
    // it must contain all Folders or all Tracks, with no mix.
    // We remember the first type added, and enforce homogeneity.
{
    String type = "";
    public String getType()  {return type; }

    public boolean getSelected(Record record)
    {
        Boolean b = get(record);
        if (b == null || !b) return false;
        return true;
    }

    public void setSelected(Record record, Boolean b)
    {
        String put_type = (record instanceof Folder) ?
            "folder" : "track";
        if (!type.isEmpty() && !put_type.equals(type))
        {
            Utils.error("Attempt to create non-homogeneous selection");
            return;
        }
        type = put_type;
        put(record,b);
    }

    @Override public void clear()
    {
        super.clear();
        type = "";
    }
}
