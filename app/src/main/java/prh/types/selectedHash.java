package prh.types;

import java.util.HashMap;

import prh.artisan.Record;

public class selectedHash extends HashMap<Record,Boolean>
{
    public boolean getSelected(Record record)
    {
        Boolean b = get(record);
        if (b == null || !b) return false;
        return true;
    }

    public void setSelected(Record record, Boolean b)
    {
        put(record,b);
    }
}
