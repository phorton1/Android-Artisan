package prh.artisan;

import android.database.Cursor;

import java.sql.Blob;
import java.util.HashMap;

public class Record extends HashMap<String,Object>
    // Class that should already exist
    // Maps a cursor to a Hash of Typed Objects by field name.
{
    private boolean exists = false;
    private boolean dirty = true;

    public boolean exists()             { return exists; }
    public boolean dirty()              { return dirty; }
    public void setDirty(boolean value) { dirty = value; }


    protected Record()
    {
        // protected default constructor
    }

    protected Record(Cursor cursor)
    {
        this.from_cursor(cursor);
    }

    protected void from_cursor(Cursor cursor)
    {
        exists = true;
        dirty = false;
        for (int i=0; i<cursor.getColumnCount(); i++)
        {
            int type = cursor.getType(i);
            String name = cursor.getColumnName(i);
            switch (type)
            {
                case Cursor.FIELD_TYPE_STRING:
                    put(name,new String(cursor.getString(i)));
                    break;
                case Cursor.FIELD_TYPE_INTEGER:
                    put(name,new Integer(cursor.getInt(i)));
                    break;
                case Cursor.FIELD_TYPE_FLOAT:
                    put(name,new Float(cursor.getFloat(i)));
                    break;
            }
        }
    }


    public String getString(String field_name)
    {
        String value = (String) get(field_name);
        if (value==null) value = new String();
        return value;
    }
    public Integer getInt(String field_name)
    {
        Integer value = (Integer) get(field_name);
        if (value==null) value = new Integer(0);
        return value;
    }
    public Float getFloat(String field_name)
    {
        Float value = (Float) get(field_name);
        if (value==null) value = new Float(0F);
        return value;
    }

    public void putString(String field_name, String value)
    {
        this.put(field_name,value);
    }
    public void putInt(String field_name, Integer value)
    {
        this.put(field_name,value);
    }
    public void putFloat(String field_name, Float value)
    {
        this.put(field_name,value);
    }

}
