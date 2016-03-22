package prh.types;

import prh.artisan.Record;

public class libraryBrowseResult extends recordList
{
    private int num_returned = 0;
    private int total_found = 0;
    private int update_id = 0;

    public int getNumReturned()  { return num_returned; }
    public int getTotalFound()   { return total_found; }
    public int getUpdateId()     { return update_id; }
    public void setNumReturned(int v) { num_returned = v; }
    public void setTotalFound(int v)  { total_found = v; }
    public void setUpdateId(int v)    { update_id = v; }
    public void incNumReturned()      { num_returned++; }

    public void addItem(Record record)
    // increments num_returned
    {
        add(record);
        incNumReturned();
    }

}
