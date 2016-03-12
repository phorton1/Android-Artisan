package prh.artisan;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;

import prh.utils.Utils;



public class Database
    // Static class
    // Never constructed
{
    public static int dbg_db = 1;

    // Field Name Cache

    private static HashMap<String,HashMap<String,Integer>> fields = new HashMap<String,HashMap<String,Integer>>();

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

    public static HashMap<String,Integer> get_fields(String table, Cursor cursor)
    // initialize the list of fields in the database files if not already done
    {
        HashMap<String,Integer> rslt = fields.get(table);
        if (rslt == null)
        {
            rslt = new HashMap<String,Integer>();
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
            HashMap<String,Integer> fields = get_fields(table,cursor);
            rslt = cursor.getString(fields.get(name));
        }
        return rslt;
    }

    public static int getIntField(String table, Cursor cursor, String name)
    {
        int rslt = 0;
        if (cursor != null)
        {
            HashMap<String,Integer> fields = get_fields(table,cursor);
            rslt = cursor.getInt(fields.get(name));
        }
        return rslt;
    }


}   // class Database
