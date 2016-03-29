package prh.artisan;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashMap;

import prh.utils.Utils;



public class Database
    // Static class
    // Never constructed
{
    public static int dbg_db = 1;

    // Field Name Cache

    private static class fieldNameHash extends HashMap<String,Integer> {}
    private static class fieldNameCache extends HashMap<String,fieldNameHash> {}
    private static fieldNameCache fields = new fieldNameCache();

    // The Main Database

    private static SQLiteDatabase db = null;

    public static SQLiteDatabase getDB() { return db; }


    public static boolean start()
    {
        if (db == null)
        {
            Utils.log(dbg_db,0,"starting database ...");
            try
            {
                String db_name = Prefs.getString(Prefs.id.DATA_DIR) + "/artisan.db";
                db = SQLiteDatabase.openDatabase(db_name,null,0);   // SQLiteDatabase.OPEN_READONLY);
            }
            catch (Exception e)
            {
                Utils.warning(0,0,"No Database Present on this machine");
                db = null;
                return false;
            }
            Utils.log(0,0,"database started");
        }
        else
        {
            Utils.log(dbg_db,0,"database already started!");
        }
        return true;
    }


    public static void stop( )
    {
        try
        {
            Utils.log(dbg_db,0,"stopping database ...");
            if (db != null) db.close();
            Utils.log(0,0,"database stopped");
        }
        catch (Exception t)
        {
            Utils.error("Error shutting down database: " + t.toString());
        }
        db = null;
    }


    //-------------------------------------------------------------
    // static initialization
    //-------------------------------------------------------------

    public static fieldNameHash get_fields(String table)
    {
        Cursor cursor = db.rawQuery("SELECT * FROM " + table + " LIMIT 0",null);
        return get_fields(table,cursor);
    }


    public static fieldNameHash get_fields(String table, Cursor cursor)
    // initialize the list of fields in the database files if not already done
    {
        fieldNameHash rslt = fields.get(table);
        if (rslt == null)
        {
            rslt = new fieldNameHash();
            for (int i = 0; i < cursor.getColumnCount(); i++)
            {
                String field_name = cursor.getColumnName(i);
                rslt.put(field_name,i);
            }
            fields.put(table,rslt);
        }
        return rslt;
    }


    public static String getStringField(String table, Cursor cursor, String name)
    {
        String rslt = "";
        if (cursor != null)
        {
            fieldNameHash fields = get_fields(table,cursor);
            rslt = cursor.getString(fields.get(name));
        }
        return rslt;
    }

    public static int getIntField(String table, Cursor cursor, String name)
    {
        int rslt = 0;
        if (cursor != null)
        {
            fieldNameHash fields = get_fields(table,cursor);
            rslt = cursor.getInt(fields.get(name));
        }
        return rslt;
    }


    // static field definitions

    public static class fieldDefs extends HashMap<String,String []> {}
    public static fieldDefs field_defs = new fieldDefs();


    private static void init_field_defs()
    {
        field_defs.put("playlists",new String[]{
            "num         INTEGER",
            "name        VARCHAR(16)",
            "num_tracks  INTEGER",
            "track_index INTEGER",
            "shuffle     INTEGER",
            "query       VARCHAR(2048)"});

        field_defs.put("tracks",new String[]{
            "position       INTEGER",
            "is_local       INTEGER",
            "id             VARCHAR(40)",
            "parent_id      VARCHAR(40)",
            "has_art        INTEGER",
            "path           VARCHAR(1024)",
            "art_uri        VARCHAR(1024)",
            "duration       BIGINT",
            "size           BIGINT",
            "type           VARCHAR(8)",
            "title          VARCHAR(128)",
            "artist         VARCHAR(128)",
            "album_title    VARCHAR(128)",
            "album_artist   VARCHAR(128)",
            "tracknum       VARCHAR(6)",
            "genre          VARCHAR(128)",
            "year_str       VARCHAR(4)",
            "timestamp      BIGINT",
            "file_md5       VARCHAR(40)",
            "error_codes    VARCHAR(128)",
            "highest_error  INTEGER"});

        field_defs.put("folders",new String[]{
            "is_local       INTEGER",
            "id             VARCHAR(40)",
            "parent_id      VARCHAR(40)",
            "dirtype        VARCHAR(16)",
            "has_art        INTEGER",
            "path           VARCHAR(1024)",
            "art_uri        VARCHAR(1024)",
            "num_elements   INTEGER",
            "title          VARCHAR(128)",
            "artist         VARCHAR(128)",
            "genre          VARCHAR(128)",
            "year_str       VARCHAR(4)",
            "folder_error          INTEGER",
            "highest_folder_error  INTEGER",
            "highest_track_error   INTEGER"});

    }   // init_field_defs()


    public static boolean createTable(SQLiteDatabase in_db, String table)
    {
        init_field_defs();
        String defs[] = field_defs.get(table);
        if (defs == null)
        {
            Utils.error("No field_defs for '" + table + "'");
            return false;
        }

        String query = "";
        for (String def:defs)
        {
            if (!query.isEmpty())
                query += ",";
            query += def;

        }
        query = "CREATE TABLE " + table + " (" + query + ")";
        try
        {
            in_db.execSQL(query);
        }
        catch (Exception e)
        {
            Utils.error("Could not execute query: " + query + " exception=" + e.toString());
            return false;
        }
        return true;
    }




    public static ContentValues getContentValues(String table, Record rec)
        // set ContentValues to only those fields in the database
        // missing fields set to null ....
    {
        ContentValues values = new ContentValues();
        fieldNameHash fields = get_fields(table);
        for (String key : rec.keySet())
        {
            if (fields.get(key) != null)
            {
                Object value = rec.get(key);
                if (value instanceof Integer)
                    values.put(key,(Integer) value);
                else if (value instanceof String)
                    values.put(key,(String) value);
                else if (value instanceof Float)
                    values.put(key,(Float) value);
            }
        }
        return values;
    }




}   // class Database
