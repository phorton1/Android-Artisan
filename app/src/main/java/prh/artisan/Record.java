package prh.artisan;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.sql.Blob;
import java.util.HashMap;

import prh.types.objectHash;

public class Record extends objectHash
    // Class that should already exist
    // Maps a cursor to a Hash of Typed Objects by field name.
{
    /*
    private boolean exists = false;
    private boolean dirty = true;

    public boolean exists()             { return exists; }
    public boolean dirty()              { return dirty; }
    public void setDirty(boolean value) { dirty = value; }
    */

    protected Record()
    {
    }

    protected Record(Cursor cursor)
    {
        this.from_cursor(cursor);
    }

    protected void from_cursor(Cursor cursor)
    {
        //exists = true;
        //dirty = false;
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


    protected Object get(String field_name)
    {
        return super.get(field_name);
    }
    protected String getString(String field_name)
    {
        String value = (String) super.get(field_name);
        if (value==null) value = new String();
        return value;
    }
    protected Integer getInt(String field_name)
    {
        Integer value = (Integer) super.get(field_name);
        if (value==null) value = new Integer(0);
        return value;
    }
    protected Float getFloat(String field_name)
    {
        Float value = (Float) super.get(field_name);
        if (value==null) value = new Float(0F);
        return value;
    }

    protected void putString(String field_name, String value)
    {
        super.put(field_name,value);
    }
    protected void putInt(String field_name, Integer value)
    {
        super.put(field_name,value);
    }
    protected void putFloat(String field_name, Float value)
    {
        super.put(field_name,value);
    }



}
