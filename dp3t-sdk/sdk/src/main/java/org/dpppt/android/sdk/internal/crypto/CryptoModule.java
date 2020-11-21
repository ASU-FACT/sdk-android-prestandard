/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package org.dpppt.android.sdk.internal.crypto;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.IOException;
import java.security.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.dpppt.android.sdk.backend.models.ExposeeAuthMethod;
import org.dpppt.android.sdk.backend.models.ExposeeAuthMethodJson;
import org.dpppt.android.sdk.internal.backend.models.ExposeeRequest;
import org.dpppt.android.sdk.internal.database.Database;
import org.dpppt.android.sdk.internal.database.models.BtLocToken;
import org.dpppt.android.sdk.internal.database.models.Contact;
import org.dpppt.android.sdk.internal.database.models.DeviceLocation;
import org.dpppt.android.sdk.internal.logger.Logger;
import org.dpppt.android.sdk.internal.util.DayDate;
import org.dpppt.android.sdk.internal.util.Json;

import static org.dpppt.android.sdk.internal.util.Base64Util.toBase64;

import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

public class CryptoModule {

	public static final int EPHID_LENGTH = 16;

	public static final int NUMBER_OF_DAYS_TO_KEEP_DATA = 21;
	public static final int NUMBER_OF_DAYS_TO_KEEP_EXPOSED_DAYS = 10;
	private static final int NUMBER_OF_EPOCHS_PER_DAY = 24 * 4;
	public static final int MILLISECONDS_PER_EPOCH = 24 * 60 * 60 * 1000 / NUMBER_OF_EPOCHS_PER_DAY;
	private static final byte[] BROADCAST_KEY = "broadcast key".getBytes();

	private static final String KEY_SK_LIST_JSON = "SK_LIST_JSON";
	private static final String KEY_EPHIDS_TODAY_JSON = "EPHIDS_TODAY_JSON";
	private byte[] SALT = "salt".getBytes();

	private static String TAG = CryptoModule.class.getCanonicalName();
	private static CryptoModule instance;

	private SharedPreferences esp;
	private static Context mContext;
	public static CryptoModule getInstance(Context context) {
		if (instance == null) {
			instance = new CryptoModule();
			mContext = context;
			try {
				String KEY_ALIAS = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
				instance.esp = EncryptedSharedPreferences.create("dp3t_store",
						KEY_ALIAS,
						context,
						EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
						EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
			} catch (GeneralSecurityException | IOException ex) {
				ex.printStackTrace();
			}
		}
		return instance;
	}

	public boolean init() {
		try {
			String stringKey = esp.getString(KEY_SK_LIST_JSON, null);
			if (stringKey != null) return true; //key already exists
			SKList skList = new SKList();
			skList.add(Pair.create(new DayDate(System.currentTimeMillis()), getNewRandomKey()));
			storeSKList(skList);
			return true;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return false;
	}

	public byte[] getNewRandomKey() throws NoSuchAlgorithmException {
		KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");
		SecretKey secretKey = keyGenerator.generateKey();
		return secretKey.getEncoded();
	}

	protected SKList getSKList() {
		String skListJson = esp.getString(KEY_SK_LIST_JSON, null);
		return Json.safeFromJson(skListJson, SKList.class, SKList::new);
	}

	private void storeSKList(SKList skList) {
		esp.edit().putString(KEY_SK_LIST_JSON, Json.toJson(skList)).apply();
	}

	protected byte[] getSKt1(byte[] SKt0) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] SKt1 = digest.digest(SKt0);
			return SKt1;
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm must be present!");
		}
	}

	private void rotateSK() {
		SKList skList = getSKList();
		DayDate nextDay = skList.get(0).first.getNextDay();
		byte[] SKt1 = getSKt1(skList.get(0).second);
		skList.add(0, Pair.create(nextDay, SKt1));
		List<Pair<DayDate, byte[]>> subList = skList.subList(0, Math.min(NUMBER_OF_DAYS_TO_KEEP_DATA, skList.size()));
		skList = new SKList();
		skList.addAll(subList);
		storeSKList(skList);
	}

	protected byte[] getCurrentSK(DayDate day) {
		SKList SKList = getSKList();
		while (SKList.get(0).first.isBefore(day)) {
			rotateSK();
			SKList = getSKList();
		}
		assert SKList.get(0).first.equals(day);
		return SKList.get(0).second;
	}

	protected List<EphId> createEphIds(byte[] SK, boolean shuffle) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(SK, "HmacSHA256"));
			mac.update(BROADCAST_KEY);
			byte[] prf = mac.doFinal();

			//generate EphIDs
			SecretKeySpec keySpec = new SecretKeySpec(prf, "AES");
			Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
			byte[] counter = new byte[16];
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(counter));
			ArrayList<EphId> ephIds = new ArrayList<>();
			byte[] emptyArray = new byte[EPHID_LENGTH];
			for (int i = 0; i < NUMBER_OF_EPOCHS_PER_DAY; i++) {
				ephIds.add(new EphId(cipher.update(emptyArray)));
			}
			if (shuffle) {
				Collections.shuffle(ephIds, new SecureRandom());
			}
			return ephIds;
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e) {
			throw new IllegalStateException("HmacSHA256 and AES algorithms must be present!", e);
		}
	}

	private static int getEpochCounter(long time) {
		DayDate day = new DayDate(time);
		return (int) (time - day.getStartOfDayTimestamp()) / MILLISECONDS_PER_EPOCH;
	}

	public long getCurrentEpochStart() {
		long now = System.currentTimeMillis();
		return getEpochStart(now);
	}

	public static long getEpochStart(long time) {
		DayDate currentDay = new DayDate(time);
		return currentDay.getStartOfDayTimestamp() + getEpochCounter(time) * MILLISECONDS_PER_EPOCH;
	}

	private EphIdsForDay getStoredEphIdsForToday() {
		String ephIdsJson = esp.getString(KEY_EPHIDS_TODAY_JSON, "null");
		return Json.safeFromJson(ephIdsJson, EphIdsForDay.class, () -> null);
	}

	private void storeEphIdsForToday(EphIdsForDay ephIdsForDay) {
		esp.edit().putString(KEY_EPHIDS_TODAY_JSON, Json.toJson(ephIdsForDay)).apply();
	}

	protected List<EphId> getEphIdsForToday(DayDate currentDay) {
		EphIdsForDay ephIdsForDay = getStoredEphIdsForToday();
		if (ephIdsForDay == null || !ephIdsForDay.dayDate.equals(currentDay)) {
			byte[] SK = getCurrentSK(currentDay);
			ephIdsForDay = new EphIdsForDay();
			ephIdsForDay.dayDate = currentDay;
			ephIdsForDay.ephIds = createEphIds(SK, true);
			storeEphIdsForToday(ephIdsForDay);
		}
		return ephIdsForDay.ephIds;
	}

	public EphId getCurrentEphId() {
		long now = System.currentTimeMillis();
		DayDate currentDay = new DayDate(now);
		return getEphIdsForToday(currentDay).get(getEpochCounter(now));
	}

	public void checkContacts(byte[] sk, long onsetDate, long bucketTime, GetContactsCallback contactCallback,
			MatchCallback matchCallback) {
		DayDate dayToTest = new DayDate(onsetDate);
		byte[] skForDay = sk;
		while (dayToTest.isBeforeOrEquals(bucketTime)) {
			long contactTimeFrom = dayToTest.getStartOfDayTimestamp();
			long contactTimeUntil = Math.min(dayToTest.getNextDay().getStartOfDayTimestamp(), bucketTime);
			List<Contact> contactsOnDay = contactCallback.getContacts(contactTimeFrom, contactTimeUntil);
			if (contactsOnDay.size() > 0) {
				//generate all ephIds for day
				HashSet<EphId> ephIdHashSet = new HashSet<>(createEphIds(skForDay, false));

				//check all contacts if they match any of the ephIds
				for (Contact contact : contactsOnDay) {
					if (ephIdHashSet.contains(contact.getEphId())) {
						matchCallback.contactMatched(contact);
					}
				}
			}

			//update day to next day and rotate sk accordingly
			dayToTest = dayToTest.getNextDay();
			skForDay = getSKt1(skForDay);
		}
	}

	public ExposeeRequest getSecretKeyForPublishing(DayDate date, ExposeeAuthMethod exposeeAuthMethod) {
		SKList skList = getSKList();
		ExposeeAuthMethodJson jsonAuth =
				exposeeAuthMethod instanceof ExposeeAuthMethodJson ? (ExposeeAuthMethodJson) exposeeAuthMethod : null;
		for (Pair<DayDate, byte[]> daySKPair : skList) {
			if (daySKPair.first.equals(date)) {
				return new ExposeeRequest(
						toBase64(daySKPair.second), null,
						daySKPair.first.getStartOfDayTimestamp(),
						jsonAuth);
			}
		}
		if (date.isBefore(skList.get(skList.size() - 1).first)) {
			return new ExposeeRequest(
					toBase64(skList.get(skList.size() - 1).second), null,
					skList.get(skList.size() - 1).first.getStartOfDayTimestamp(),
					jsonAuth);
		}
		return null;
	}
//	public ArrayList<String> hashBtGps(EphId ephId, DeviceLocation deviceLocation, long timestamp){
//		long timeWindow[] = deviceLocation.getTimeWindow();
//		ArrayList<String> locationHashes = deviceLocation.getLocationHashes();
//		System.out.println("Current EphId:"+getCurrentEphId().getData());
//		String ephId = toBase64(getCurrentEphId().getData());
//		ArrayList<String> broadcastBTGpsHashes = new ArrayList<>();
//		for(String locationHash:locationHashes){
//
//
//			System.out.println("EphId:"+ephId+"Location Hash:"+locationHash+"Early:"+timeWindow[0]+"Late:"+timeWindow[1]);
//			broadcastBTGpsHashes.add(ephId+locationHash+timeWindow[0]);
//			broadcastBTGpsHashes.add(ephId+locationHash+timeWindow[1]);
//		}
//		return broadcastBTGpsHashes;
//	}
	@SuppressLint("ApplySharedPref")
	public void reset() {
		try {
			esp.edit().clear().commit();
			init();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	public ArrayList<String> getHashes(BtLocToken btLocToken){

		DeviceLocation deviceLocation = btLocToken.getDeviceLocation();

		long roundedTimestamp = deviceLocation.getRoundedTimestamp();

		EphId ephId = btLocToken.getEphId();
		ArrayList<String> locationHashes = deviceLocation.getLocationHashes();

		ArrayList<String> hashes = new ArrayList<>();
		for(String locHash: locationHashes){
			// encrypt ephid, lochash, time
			try {
				hashes.add(digest(ephId, roundedTimestamp, locHash));
			}
			catch (IOException | DigestException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException | InvalidAlgorithmParameterException e){
				Logger.e(TAG,e);
			}
		}
		return hashes;
	}
	private String digest(EphId ephId, long timestamp, String locHash) throws IOException, DigestException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
//		byte[] source = Bytes.concat(ephId.getData(),locHash.getBytes(), Longs.toByteArray(timestamp));
//		byte[] crpyt = SCrypt.generate(source, SALT, 4096, 8, 1, 8);
//		try{
//			MessageDigest md = MessageDigest.getInstance("SHA-256");
//			md.update(source);
//			MessageDigest tc1 = (MessageDigest) md.clone();
//			byte[] crypt = tc1.digest();
//			StringBuilder sb = new StringBuilder();
//			for(byte b:crypt)
//				sb.append(String.format("%02X",b));
//			return sb.toString();
//		}
//		catch(CloneNotSupportedException | NoSuchAlgorithmException cnse){
//			throw new DigestException("Couldn't make digest of content");
//		}
		BaseEncoding baseEncoding = BaseEncoding.base16();
		byte[] key = ephId.getData();
		byte[] plaintext = Bytes.concat(locHash.getBytes(), Longs.toByteArray(timestamp));
		byte[] iv = new byte[16];
		IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
		SecretKey secretKey = new SecretKeySpec(key, "AES");
		System.out.println("Secret key (BT token) = "+baseEncoding.encode(secretKey.getEncoded()));
		System.out.println("Geohash = "+locHash);
		System.out.println("Rounded time stamp = " + timestamp);
		final Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
		byte[] cipherText = cipher.doFinal(plaintext);
		byte[] truncatedCipherText = Arrays.copyOfRange(cipherText,0,10);
		String encode = baseEncoding.encode(truncatedCipherText);
		System.out.println("AES Encrypted ciphertext = "+ encode +"; Size = "+encode.length());
		return encode;
	}

	public ExposeeRequest getHashesForPublishing(DayDate date, ExposeeAuthMethod exposeeAuthMethod) {
		// Get BroadcastBtGpsTokens from database
		System.out.println("Crypto thread:"+Thread.currentThread());
		Database database = new Database(mContext);
		ArrayList<String> hashes = database.getBroadcastBtLocHashes();

		// return ExposeeRequest with the hashes
		System.out.println("Sent hashes:"+hashes);
		SKList skList = getSKList();
		ExposeeAuthMethodJson jsonAuth =
				exposeeAuthMethod instanceof ExposeeAuthMethodJson ? (ExposeeAuthMethodJson) exposeeAuthMethod : null;
		for (Pair<DayDate, byte[]> daySKPair : skList) {
			if (daySKPair.first.equals(date)) {
				return new ExposeeRequest(
						toBase64(daySKPair.second), hashes,
						daySKPair.first.getStartOfDayTimestamp(),
						jsonAuth);
			}
		}
		if (date.isBefore(skList.get(skList.size() - 1).first)) {
			return new ExposeeRequest(
					toBase64(skList.get(skList.size() - 1).second), hashes,
					skList.get(skList.size() - 1).first.getStartOfDayTimestamp(),
					jsonAuth);
		}
		return null;
    }

    public interface GetContactsCallback {
		/**
		 * @param timeFrom timestamp inclusive
		 * @param timeUntil timestamp exclusive
		 */
		List<Contact> getContacts(long timeFrom, long timeUntil);

	}


	public interface MatchCallback {

		void contactMatched(Contact contact);

	}

}
