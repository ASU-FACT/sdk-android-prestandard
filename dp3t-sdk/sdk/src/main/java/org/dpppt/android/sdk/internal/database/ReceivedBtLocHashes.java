package org.dpppt.android.sdk.internal.database;

interface ReceivedBtLocHashes {
    String TABLE_NAME = "ReceivedBtLocHashes";
    String ID = "id";
    String TIME = "time";
    String HASH = "hash";
    String HANDSHAKE_ID = "handshake_id";
    String[] PROJECTION = {
            ID,
            TIME,
            HASH,
            HANDSHAKE_ID
    };
    static String create() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                TIME + " INTEGER NOT NULL, " +
                HASH + " TEXT NOT NULL, " +
                HANDSHAKE_ID + " INTEGER NOT NULL"+
                ")";
    }

    static String drop() {
        return "DROP TABLE IF EXISTS " + TABLE_NAME;
    }

}

