package org.dpppt.android.sdk.internal.database.models;

import org.dpppt.android.sdk.internal.crypto.EphId;
import static org.dpppt.android.sdk.internal.util.Base64Util.toBase64;

public class BroadcastBtLocToken {
    private int id;
    private EphId ephId;
    private long time;
    private double latitude;
    private double longitude;

    public BroadcastBtLocToken(EphId ephId,long time, double latitude, double longitude) {
        this.ephId = ephId;
        this.time = time;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public EphId getEphId() {
        return ephId;
    }

    public void setEphId(EphId ephId) {
        this.ephId = ephId;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public double getLatitude() {
        return latitude;
    }
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    @Override
    public String toString() {
        return "BroadcastBtLocToken{" +
                "id=" + id +
                ", ephId=" + toBase64(ephId.getData()) +
                ", time=" + time +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                '}';
    }
}
