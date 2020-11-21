/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package org.dpppt.android.sdk.internal.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bouncycastle.util.test.Test;
import org.dpppt.android.sdk.BuildConfig;
import org.dpppt.android.sdk.internal.AppConfigManager;
import org.dpppt.android.sdk.internal.BroadcastHelper;
import org.dpppt.android.sdk.internal.crypto.ContactsFactory;
import org.dpppt.android.sdk.internal.crypto.CryptoModule;
import org.dpppt.android.sdk.internal.crypto.EphId;
import org.dpppt.android.sdk.internal.database.models.BtLocToken;
import org.dpppt.android.sdk.internal.database.models.Contact;
import org.dpppt.android.sdk.internal.database.models.DeviceLocation;
import org.dpppt.android.sdk.internal.database.models.ExposureDay;
import org.dpppt.android.sdk.internal.database.models.Handshake;
import org.dpppt.android.sdk.internal.util.DayDate;

import static android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE;

public class Database {
	Context context;
	private DatabaseOpenHelper databaseOpenHelper;
	private DatabaseThread databaseThread;

	public Database(@NonNull Context context) {
		databaseOpenHelper = DatabaseOpenHelper.getInstance(context);
		this.context = context;
		databaseThread = DatabaseThread.getInstance(context);
	}

	public void addKnownCase(Context context, @NonNull byte[] key, long onsetDate, long bucketTime) {
		SQLiteDatabase db = databaseOpenHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(KnownCases.KEY, key);
		values.put(KnownCases.ONSET, onsetDate);
		values.put(KnownCases.BUCKET_TIME, bucketTime);
		databaseThread.post(() -> {
			long idOfAddedCase = db.insertWithOnConflict(KnownCases.TABLE_NAME, null, values, CONFLICT_IGNORE);
			if (idOfAddedCase == -1) {
				//key was already in the database, so we can ignore it
				return;
			}

			CryptoModule cryptoModule = CryptoModule.getInstance(context);
			cryptoModule.checkContacts(key, onsetDate, bucketTime, this::getContacts, (contact) -> {
				ContentValues updateValues = new ContentValues();
				updateValues.put(Contacts.ASSOCIATED_KNOWN_CASE, idOfAddedCase);
				db.update(Contacts.TABLE_NAME, updateValues, Contacts.ID + "=" + contact.getId(), null);
			});

			//compute exposure days
			List<Contact> allMatchedContacts = getAllMatchedContacts();
			HashMap<DayDate, List<Contact>> groupedByDay = new HashMap<>();
			for (Contact contact : allMatchedContacts) {
				DayDate date = new DayDate(contact.getDate());
				if (!groupedByDay.containsKey(date)) {
					groupedByDay.put(date, new ArrayList<>());
				}
				groupedByDay.get(date).add(contact);
			}

			AppConfigManager appConfigManager = AppConfigManager.getInstance(context);
			DayDate maxAgeForExposureDay = new DayDate().subtractDays(CryptoModule.NUMBER_OF_DAYS_TO_KEEP_EXPOSED_DAYS);
			boolean newExposureDaysAdded = false;
			for (Map.Entry<DayDate, List<Contact>> dayEntry : groupedByDay.entrySet()) {
				if (dayEntry.getKey().isBefore(maxAgeForExposureDay)) {
					continue;
				}
				int exposureSumForDay = 0;
				for (Contact contact : dayEntry.getValue()) {
					exposureSumForDay += contact.getWindowCount();
				}
				if (exposureSumForDay >= appConfigManager.getNumberOfWindowsForExposure()) {
					ContentValues exposureDayValues = new ContentValues();
					exposureDayValues.put(ExposureDays.REPORT_DATE, System.currentTimeMillis());
					exposureDayValues.put(ExposureDays.EXPOSED_DATE, dayEntry.getKey().getStartOfDayTimestamp());
					long id = db.insertWithOnConflict(ExposureDays.TABLE_NAME, null, exposureDayValues, CONFLICT_IGNORE);
					if (id != -1) {
						newExposureDaysAdded = true;
					}
				}
			}

			if (newExposureDaysAdded) {
				BroadcastHelper.sendUpdateBroadcast(context);
			}
		});
	}


	public void removeOldData() {
		databaseThread.post(() -> {
			SQLiteDatabase db = databaseOpenHelper.getWritableDatabase();
			DayDate lastDayToKeep = new DayDate().subtractDays(CryptoModule.NUMBER_OF_DAYS_TO_KEEP_DATA);
			db.delete(KnownCases.TABLE_NAME, KnownCases.BUCKET_TIME + " < ?",
					new String[] { Long.toString(lastDayToKeep.getStartOfDayTimestamp()) });
			db.delete(Contacts.TABLE_NAME, Contacts.DATE + " < ?",
					new String[] { Long.toString(lastDayToKeep.getStartOfDayTimestamp()) });
			DayDate lastDayToKeepMatchedContacts =
					new DayDate().subtractDays(CryptoModule.NUMBER_OF_DAYS_TO_KEEP_EXPOSED_DAYS);
			db.delete(ExposureDays.TABLE_NAME, ExposureDays.REPORT_DATE + " < ?",
					new String[] { Long.toString(lastDayToKeepMatchedContacts.getStartOfDayTimestamp()) });
			// TODO delete old location data
			// TODO delete old broadcastBtLocHashes
			// TODO delete old receivedBtLocHashes
		});
	}

	public ContentValues addHandshake(Context context, Handshake handshake) {
		SQLiteDatabase db = databaseOpenHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(Handshakes.EPHID, handshake.getEphId().getData());
		values.put(Handshakes.TIMESTAMP, handshake.getTimestamp());
		values.put(Handshakes.TX_POWER_LEVEL, handshake.getTxPowerLevel());
		values.put(Handshakes.RSSI, handshake.getRssi());
		values.put(Handshakes.PHY_PRIMARY, handshake.getPrimaryPhy());
		values.put(Handshakes.PHY_SECONDARY, handshake.getSecondaryPhy());
		values.put(Handshakes.TIMESTAMP_NANOS, handshake.getTimestampNanos());
		DeviceLocation deviceLocation = handshake.getDeviceLocation();
		values.put(Handshakes.LATITUDE, deviceLocation.getLatitude());
		values.put(Handshakes.LONGITUDE, deviceLocation.getLongitude());
		// TODO Consider calculating hashes for each contact rather than for each handshake
		databaseThread.post(() -> {
			final long handshakeId = db.insert(Handshakes.TABLE_NAME, null, values);
			addReceivedBtLocHashes(new BtLocToken(handshake.getEphId(),handshake.getDeviceLocation()),handshakeId);
			BroadcastHelper.sendUpdateBroadcast(context);
		});
//

		return values;
	}

	private void addReceivedBtLocHashes(BtLocToken btLocToken, long handshakeId) {
		System.out.println("Saving received BT token + location hash");
		System.out.println("Handshake Id = "+handshakeId);
		SQLiteDatabase db = databaseOpenHelper.getWritableDatabase();
		ArrayList<String> hashes = CryptoModule.getInstance(context).getHashes(btLocToken);
		for(String hash: hashes) {
			ContentValues values = new ContentValues();
			values.put(ReceivedBtLocHashes.TIME, btLocToken.getDeviceLocation().getTime());
			values.put(ReceivedBtLocHashes.HASH,hash);
			values.put(ReceivedBtLocHashes.HANDSHAKE_ID, handshakeId);
			db.insertWithOnConflict(ReceivedBtLocHashes.TABLE_NAME, null, values, CONFLICT_IGNORE);
		}
	}
	public void addTestHashes(ArrayList<String> hashes) {
		SQLiteDatabase db = databaseOpenHelper.getWritableDatabase();
		for(String hash: hashes) {
			ContentValues values = new ContentValues();
			values.put(TestHashes.TIME, System.currentTimeMillis());
			values.put(TestHashes.HASH,hash);
			db.insertWithOnConflict(TestHashes.TABLE_NAME, null, values, CONFLICT_IGNORE);
		}
	}
	public ArrayList<String> getTestHashes(int count){
		SQLiteDatabase db = databaseOpenHelper.getReadableDatabase();
		Cursor cursor;
		cursor = db.query(TestHashes.TABLE_NAME, TestHashes.PROJECTION, null, null, null, null, TestHashes.ID,String.valueOf(count));
		return getTestHashesFromCursor(cursor);
	}

	private ArrayList<String> getTestHashesFromCursor(Cursor cursor) {
		ArrayList<String> hashes = new ArrayList<>();
		while (cursor.moveToNext()) {
			String hash = cursor.getString(cursor.getColumnIndexOrThrow(TestHashes.HASH));
			hashes.add(hash);
		}
		cursor.close();
		return hashes;
	}
	public long getTestHashesCount() {
		SQLiteDatabase db = databaseOpenHelper.getReadableDatabase();
		return  DatabaseUtils.queryNumEntries(db, TestHashes.TABLE_NAME);
	}

	public ArrayList<String> getReceivedBtLocHashes(){
		SQLiteDatabase db = databaseOpenHelper.getReadableDatabase();
		Cursor cursor = db
				.query(ReceivedBtLocHashes.TABLE_NAME, ReceivedBtLocHashes.PROJECTION, null, null, null, null, ReceivedBtLocHashes.ID);
		return getReceivedBtLocHashesFromCursor(cursor);
	}
	public ArrayList<String> getReceivedBtLocHashesFromCursor(Cursor cursor){
		ArrayList<String> hashes = new ArrayList<>();
		while (cursor.moveToNext()) {
			String hash = cursor.getString(cursor.getColumnIndexOrThrow(ReceivedBtLocHashes.HASH));
			hashes.add(hash);
		}
		cursor.close();
		return hashes;
	}
	public List<Handshake> getHandshakes() {
		SQLiteDatabase db = databaseOpenHelper.getReadableDatabase();
		Cursor cursor = db.query(Handshakes.TABLE_NAME, Handshakes.PROJECTION, null, null, null, null, Handshakes.ID);
		return getHandshakesFromCursor(cursor);
	}

	public List<Handshake> getHandshakes(long maxTime) {
		SQLiteDatabase db = databaseOpenHelper.getReadableDatabase();
		Cursor cursor = db.query(Handshakes.TABLE_NAME, Handshakes.PROJECTION, Handshakes.TIMESTAMP + " < ?",
				new String[] { "" + maxTime }, null, null, Handshakes.ID);
		return getHandshakesFromCursor(cursor);
	}

	public void getHandshakes(@NonNull ResultListener<List<Handshake>> resultListener) {
		databaseThread.post(new Runnable() {
			List<Handshake> handshakes = new ArrayList<>();

			@Override
			public void run() {
				handshakes = getHandshakes();
				databaseThread.onResult(() -> resultListener.onResult(handshakes));
			}
		});
	}

	private List<Handshake> getHandshakesFromCursor(Cursor cursor) {
		List<Handshake> handshakes = new ArrayList<>();
		while (cursor.moveToNext()) {
			int id = cursor.getInt(cursor.getColumnIndexOrThrow(Handshakes.ID));
			long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(Handshakes.TIMESTAMP));
			EphId ephId = new EphId(cursor.getBlob(cursor.getColumnIndexOrThrow(Handshakes.EPHID)));
			int txPowerLevel = cursor.getInt(cursor.getColumnIndexOrThrow(Handshakes.TX_POWER_LEVEL));
			int rssi = cursor.getInt(cursor.getColumnIndexOrThrow(Handshakes.RSSI));
			String primaryPhy = cursor.getString(cursor.getColumnIndexOrThrow(Handshakes.PHY_PRIMARY));
			String secondaryPhy = cursor.getString(cursor.getColumnIndexOrThrow(Handshakes.PHY_SECONDARY));
			long timestampNanos = cursor.getLong(cursor.getColumnIndexOrThrow(Handshakes.TIMESTAMP_NANOS));
			Double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(Handshakes.LATITUDE));
			Double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(Handshakes.LONGITUDE));
			DeviceLocation deviceLocation = new DeviceLocation(timestamp,latitude,longitude);
			Handshake handShake = new Handshake(id, timestamp, ephId, txPowerLevel, rssi, primaryPhy, secondaryPhy,
					timestampNanos,deviceLocation);
//			handShake.setHashes(CryptoModule.getInstance(context).getHashes(new BtLocToken(ephId,deviceLocation)));
			handshakes.add(handShake);
		}
		cursor.close();
		return handshakes;
	}

	public void generateContactsFromHandshakes(Context context) {
		databaseThread.post(() -> {

			long currentEpochStart = CryptoModule.getInstance(context).getCurrentEpochStart();

			List<Handshake> handshakes = getHandshakes(currentEpochStart);
			List<Contact> contacts = ContactsFactory.mergeHandshakesToContacts(context, handshakes);
			for (Contact contact : contacts) {
				addContact(contact);
			}

			SQLiteDatabase db = databaseOpenHelper.getWritableDatabase();
			if (!BuildConfig.FLAVOR.equals("calibration")) {
				//unless in calibration mode, delete handshakes after converting them to contacts
				db.delete(Handshakes.TABLE_NAME, Handshakes.TIMESTAMP + " < ?",
						new String[] { "" + currentEpochStart });
			}
			removeOldData();
		});
	}
	public void saveDeviceLocation(DeviceLocation location){
		SQLiteDatabase db = databaseOpenHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(DeviceLocations.TIME,location.getTime());
		values.put(DeviceLocations.LATITUDE,location.getLatitude());
		values.put(DeviceLocations.LONGITUDE,location.getLongitude());
		long rowId = db.insertWithOnConflict(DeviceLocations.TABLE_NAME, null, values, CONFLICT_IGNORE);

	}
	public void saveBroadcastBtLocHashes(BtLocToken btLocToken){
		SQLiteDatabase db = databaseOpenHelper.getWritableDatabase();
		ArrayList<String> hashes = CryptoModule.getInstance(context).getHashes(btLocToken);
		for(String hash: hashes) {
			ContentValues values = new ContentValues();
			values.put(BroadcastBtLocHashes.TIME, btLocToken.getDeviceLocation().getTime());
			values.put(BroadcastBtLocHashes.HASH,hash);
			db.insertWithOnConflict(BroadcastBtLocHashes.TABLE_NAME, null, values, CONFLICT_IGNORE);
		}
//		addReceivedBtLocHashes(btLocToken);
	}
	public ArrayList<String> getBroadcastBtLocHashes(){
		SQLiteDatabase db = databaseOpenHelper.getReadableDatabase();
		Cursor cursor = db
				.query(BroadcastBtLocHashes.TABLE_NAME, BroadcastBtLocHashes.PROJECTION, null, null, null, null, BroadcastBtLocHashes.ID);
		return getBroadcastBtLocHashesFromCursor(cursor);
	}
	public ArrayList<String> getBroadcastBtLocHashesFromCursor(Cursor cursor){
		ArrayList<String> hashes = new ArrayList<>();
		while (cursor.moveToNext()) {
			String hash = cursor.getString(cursor.getColumnIndexOrThrow(BroadcastBtLocHashes.HASH));
			hashes.add(hash);
		}
		cursor.close();
		return hashes;
	}

	public ArrayList<DeviceLocation> getDeviceLocations(){
		SQLiteDatabase db = databaseOpenHelper.getReadableDatabase();
		Cursor cursor = db
				.query(DeviceLocations.TABLE_NAME, DeviceLocations.PROJECTION, null, null, null, null, DeviceLocations.ID);
		return getDeviceLocationsFromCursor(cursor);
	}
	private ArrayList<DeviceLocation> getDeviceLocationsFromCursor(Cursor cursor){
		ArrayList<DeviceLocation> deviceLocations = new ArrayList<>();
		while (cursor.moveToNext()) {
			long time = cursor.getLong(cursor.getColumnIndexOrThrow(DeviceLocations.TIME));
			Double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(DeviceLocations.LATITUDE));
			Double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(DeviceLocations.LONGITUDE));
//			String hashes = cursor.getString(cursor.getColumnIndexOrThrow(DeviceLocations.HASHES));
//			DeviceLocation deviceLocation = new DeviceLocation(time,latitude,longitude,hashes);
			DeviceLocation deviceLocation = new DeviceLocation(time,latitude,longitude);
			deviceLocations.add(deviceLocation);
		}
		cursor.close();
		return deviceLocations;
	}
	private void addContact(Contact contact) {
		SQLiteDatabase db = databaseOpenHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(Contacts.EPHID, contact.getEphId().getData());
		values.put(Contacts.DATE, contact.getDate());
		values.put(Contacts.WINDOW_COUNT, contact.getWindowCount());
		db.insertWithOnConflict(Contacts.TABLE_NAME, null, values, CONFLICT_IGNORE);
	}

	public List<Contact> getContacts() {
		SQLiteDatabase db = databaseOpenHelper.getReadableDatabase();
		Cursor cursor = db
				.query(Contacts.TABLE_NAME, Contacts.PROJECTION, null, null, null, null, Contacts.ID);
		return getContactsFromCursor(cursor);
	}

	public List<Contact> getContacts(long timeFrom, long timeUntil) {
		SQLiteDatabase db = databaseOpenHelper.getReadableDatabase();
		Cursor cursor = db.query(Contacts.TABLE_NAME, Contacts.PROJECTION, Contacts.DATE + ">=? AND " + Contacts.DATE + "<?",
				new String[] { Long.toString(timeFrom), Long.toString(timeUntil) }, null, null, Contacts.ID);
		return getContactsFromCursor(cursor);
	}

	public List<Contact> getAllMatchedContacts() {
		SQLiteDatabase db = databaseOpenHelper.getReadableDatabase();
		Cursor cursor = db
				.query(Contacts.TABLE_NAME, Contacts.PROJECTION, Contacts.ASSOCIATED_KNOWN_CASE + "!=0", null, null, null,
						Contacts.ID);
		return getContactsFromCursor(cursor);
	}

	private List<Contact> getContactsFromCursor(Cursor cursor) {
		List<Contact> contacts = new ArrayList<>();
		while (cursor.moveToNext()) {
			int id = cursor.getInt(cursor.getColumnIndexOrThrow(Contacts.ID));
			long date = cursor.getLong(cursor.getColumnIndexOrThrow(Contacts.DATE));
			EphId ephid = new EphId(cursor.getBlob(cursor.getColumnIndexOrThrow(Contacts.EPHID)));
			int windowCount = cursor.getInt(cursor.getColumnIndexOrThrow(Contacts.WINDOW_COUNT));
			int associatedKnownCase = cursor.getInt(cursor.getColumnIndexOrThrow(Contacts.ASSOCIATED_KNOWN_CASE));
			Contact contact = new Contact(id, date, ephid, windowCount, associatedKnownCase);
			contacts.add(contact);
		}
		cursor.close();
		return contacts;
	}

	public List<ExposureDay> getExposureDays() {
		List<ExposureDay> exposureDays = new ArrayList<>();
		SQLiteDatabase db = databaseOpenHelper.getReadableDatabase();
		Cursor cursor =
				db.query(ExposureDays.TABLE_NAME, ExposureDays.PROJECTION, null, null, null, null, ExposureDays.EXPOSED_DATE);
		while (cursor.moveToNext()) {
			int id = cursor.getInt(cursor.getColumnIndexOrThrow(ExposureDays.ID));
			DayDate exposedDate = new DayDate(cursor.getLong(cursor.getColumnIndexOrThrow(ExposureDays.EXPOSED_DATE)));
			long reportDate = cursor.getLong(cursor.getColumnIndexOrThrow(ExposureDays.REPORT_DATE));
			ExposureDay day = new ExposureDay(id, exposedDate, reportDate);
			exposureDays.add(day);
		}
		cursor.close();
		return exposureDays;
	}

	public void recreateTables(ResultListener<Void> listener) {
		databaseThread.post(() -> {
			recreateTablesSynchronous();
			listener.onResult(null);
		});
	}

	public void recreateTablesSynchronous() {
		databaseOpenHelper.recreateTables(databaseOpenHelper.getWritableDatabase());
	}

	public void exportTo(Context context, OutputStream targetOut, ResultListener<Void> listener) {
		databaseThread.post(() -> {
			try {
				databaseOpenHelper.exportDatabaseTo(context, targetOut);
			} catch (IOException e) {
				e.printStackTrace();
			}
			listener.onResult(null);
		});
	}

	public void runOnDatabaseThread(Runnable runnable) {
		databaseThread.post(runnable);
	}


}
