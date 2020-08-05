package org.dpppt.android.sdk.internal;

import android.content.Context;
import android.location.Location;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnSuccessListener;


public class LocationService {
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private Context mContext;
    private long LOCATION_INTERVAL;
    private long FASTEST_INTERVAL;
    private static LocationService instance;
    private boolean isReceivingUpdates = false;
    private Location bestLocation;
    protected LocationService(Context context,long location_interval,long fastest_interval){
        mContext = context;
        LOCATION_INTERVAL = location_interval;  /* 10 secs */
        FASTEST_INTERVAL = fastest_interval;
    }
//    protected void init(){
//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext);
//        mLocationRequest = new LocationRequest();
//        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
//        mLocationRequest.setInterval(LOCATION_INTERVAL);
//        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
//    }
    public static LocationService getInstance(Context context) {
        if(instance==null){
            instance = new LocationService(context,10*1000,10*1000);

        }
        return instance;
    }
    public boolean startLocationUpdates(LocationCallback locationCallback){
//         Create the location request to start receiving updates
        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(LOCATION_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mLocationCallback = locationCallback;
        // Create LocationSettingsRequest object using location request
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        LocationSettingsRequest locationSettingsRequest = builder.build();
        // Check whether location settings are satisfied
        // https://developers.google.com/android/reference/com/google/android/gms/location/SettingsClient
        SettingsClient settingsClient = LocationServices.getSettingsClient(mContext);
        settingsClient.checkLocationSettings(locationSettingsRequest);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext);
        LocationCallback callback = new LocationCallback(){
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    bestLocation = locationResult.getLastLocation();
                    mLocationCallback.onLocationResult(locationResult);
                }
        };
        try{
            fusedLocationClient.requestLocationUpdates(mLocationRequest, callback, Looper.myLooper());
            isReceivingUpdates = true;
        }
        catch(SecurityException e){
            System.out.println("Not Enough permissions.");
            isReceivingUpdates = false;
        }
        return isReceivingUpdates;
    }
    public void stopLocationUpdates(){
        if(isReceivingUpdates && fusedLocationClient!=null)
        {
            fusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
    }
    public Location getLastLocation(){
        return bestLocation;
    }
}
