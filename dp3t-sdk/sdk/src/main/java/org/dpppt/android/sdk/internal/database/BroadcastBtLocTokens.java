package org.dpppt.android.sdk.internal.database;

interface BroadcastBtLocTokens {
    String TABLE_NAME = "broadcastbtloctokens";
    String ID = "id";
    String EPHID = "ephId";
    String TIME = "time";
    String LATITUDE = "latitude";
    String LONGITUDE = "longitude";
    String[] PROJECTION = {
            ID,
            EPHID,
            TIME,
            LATITUDE,
            LONGITUDE
    };
    static String create() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                TIME + " INTEGER NOT NULL, " +
                EPHID + " BLOB NOT NULL, " +
                LATITUDE + " DOUBLE NOT NULL, " +
                LONGITUDE + " DOUBLE NOT NULL "+
                ")";
    }

    static String drop() {
        return "DROP TABLE IF EXISTS " + TABLE_NAME;
    }

}
