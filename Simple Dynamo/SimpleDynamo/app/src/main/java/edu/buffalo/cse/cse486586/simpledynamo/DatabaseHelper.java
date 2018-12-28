package edu.buffalo.cse.cse486586.simpledynamo;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Reference : https://developer.android.com/guide/topics/providers/content-provider-creating.html
 */


public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TABLE_NAME = "MessageStore";
    private static final String DBNAME = "Dht";

    private static final String SQL_CREATE_TABLE = "CREATE TABLE " +
            TABLE_NAME +                       // Table's name
            "(" +                           // The columns in the table
            " key TEXT PRIMARY KEY, " +
            " value TEXT )";

    /*
     * Instantiates an open helper for the provider's SQLite data repository
     * Do not do database creation and upgrade here.
     */
    DatabaseHelper(Context context) {
        super(context, DBNAME, null, 1);
    }

    /*
     * Creates the data repository. This is called when the provider attempts to open the
     * repository and SQLite reports that it doesn't exist.
     */
    public void onCreate(SQLiteDatabase db) {
        // Creates the table
        db.execSQL(SQL_CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }
}