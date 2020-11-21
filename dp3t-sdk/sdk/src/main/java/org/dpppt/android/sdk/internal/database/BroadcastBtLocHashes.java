package org.dpppt.android.sdk.internal.database;

interface BroadcastBtLocHashes {
    String TABLE_NAME = "BroadcastBtLocHashes";
    String ID = "id";
    String TIME = "time";
    String HASH = "hash";
    String[] PROJECTION = {
            ID,
            TIME,
            HASH
    };
    static String create() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                TIME + " INTEGER NOT NULL, " +
                HASH + " TEXT NOT NULL" +
                ")";
    }

    static String drop() {
        return "DROP TABLE IF EXISTS " + TABLE_NAME;
    }

}

