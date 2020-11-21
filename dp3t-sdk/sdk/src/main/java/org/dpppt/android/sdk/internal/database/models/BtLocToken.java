package org.dpppt.android.sdk.internal.database.models;

import org.dpppt.android.sdk.internal.crypto.EphId;

public class BtLocToken {
    private EphId ephId;
    private DeviceLocation deviceLocation;

    public BtLocToken(EphId ephId, DeviceLocation deviceLocation) {
        this.ephId = ephId;
        this.deviceLocation = deviceLocation;
    }

    public EphId getEphId() {
        return ephId;
    }

    public void setEphId(EphId ephId) {
        this.ephId = ephId;
    }

    public DeviceLocation getDeviceLocation() {
        return deviceLocation;
    }

    public void setDeviceLocation(DeviceLocation deviceLocation) {
        this.deviceLocation = deviceLocation;
    }
    public long getTime(){
        return this.deviceLocation.getTime();
    }
//    public double getLatitude(){
//        return this.deviceLocation.getLatitude();
//    }
//    public double getLongitude(){
//        return this.deviceLocation.getLongitude();
//    }
}
