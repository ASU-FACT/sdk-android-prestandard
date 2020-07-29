package org.dpppt.android.sdk.internal.database.models;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.github.davidmoten.geo.GeoHash;

public class DeviceLocation {
    private int id;
    private long time;
    private double latitude;
    private double longitude;
    private String hashes;
    private int hashLength = 8;
    private double[][] radii = {{0.0, 0.0},{0.0001, 0.0},{0.00007, 0.00007},{0.0, 0.0001},{-0.00007, 0.00007},{-0.0001, 0.0},{-0.00007, -0.00007},{0.0, -0.0001},{0.00007, -0.00007}};

    public DeviceLocation(long time, double latitude, double longitude) {
        this.time = time;
        this.latitude = latitude;
        this.longitude = longitude;
        this.hashes = geohashes(latitude,longitude);
    }
    public DeviceLocation(long time, double latitude, double longitude,String hashes) {
        this.time = time;
        this.latitude = latitude;
        this.longitude = longitude;
        this.hashes = hashes;
    }
    public String geohashes(double latitude,double longitude){
        Set<String> hashSet = new HashSet<>();
        for (double[] radius : radii) {
            double lat = radius[0] + latitude;
            double lon = radius[1] + longitude;
            hashSet.add(GeoHash.encodeHash(lat, lon, hashLength));

        }
        String hashes = "";
        for(String hash : hashSet) hashes += hash + ";";
        return hashes;
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

    public String getHashes() {
        return hashes;
    }

    public void setHashes(String hashes) {
        this.hashes = hashes;
    }
    @NonNull
    public String toString(){
        return "Time: "+time+"\tLatitude: "+latitude+"\tLongitude: "+longitude+"\tHashes:"+hashes;

    }
    public int getHashLength() {
        return hashLength;
    }

    public void setHashLength(int hashLength) {
        this.hashLength = hashLength;
    }
}
