package org.dpppt.android.sdk.internal.database;

interface DeviceLocations {
    String TABLE_NAME = "locations";

    String ID = "id";
    String TIME = "time";
    String LATITUDE = "latitude";
    String LONGITUDE = "longitude";
    String HASHES = "hashes";

//    String ALTITUDE = "altitude";
//    String SPEED = "speed";
//    String ACCURACY = "accuracy";
//    String BEARING = "bearing";
//    String PROVIDER = "provider";
//    String MOCKFLAGS = "mockFlags";
//    String SOURCE = "source";

    String[] PROJECTION = {
            ID,
            TIME,
            LATITUDE,
            LONGITUDE,
            HASHES
//            ,
//            ALTITUDE,
//            SPEED,
//            ACCURACY,
//            BEARING,
//            PROVIDER,
//            MOCKFLAGS,
//            SOURCE
    };

    static String create() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                TIME + " INTEGER NOT NULL, " +
                LATITUDE + " DOUBLE NOT NULL, " +
                LONGITUDE + " LONGITUDE NOT NULL, " +
                HASHES +" TEXT"+
//                ","+
//                ALTITUDE + " DOUBLE,"+
//                SPEED + " FLOAT,"+
//                ACCURACY + " FLOAT,"+
//                BEARING + " FLOAT,"+
//                PROVIDER + " TEXT,"+
//                MOCKFLAGS + " INTEGER,"+
//                SOURCE + " INTEGER,"+
                ")";
    }

    static String drop() {
        return "DROP TABLE IF EXISTS " + TABLE_NAME;
    }

}
