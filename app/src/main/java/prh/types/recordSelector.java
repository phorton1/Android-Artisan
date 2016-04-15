package prh.types;

import prh.artisan.Record;

public interface recordSelector
    // used by ListItem and ListItemAdapter to determine
    // if a record is selected, and/or to select it
{
    boolean getSelected(boolean for_display, Record record);
        // this method will be called with for_display == true
        // and the given record, and you should return true if
        // it is to be highlighted

    void setSelected(Record record, boolean selected);
        // will be called when the record is long clicked
        // with the opposite of getSelected(false);
}

