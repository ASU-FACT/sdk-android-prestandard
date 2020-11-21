/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package org.dpppt.android.sdk.internal;

import android.content.Context;
import android.database.sqlite.SQLiteException;
import androidx.annotation.NonNull;
import androidx.work.*;

import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.InvalidProtocolBufferException;

import org.dpppt.android.sdk.TracingStatus.ErrorState;
import org.dpppt.android.sdk.backend.SignatureException;
import org.dpppt.android.sdk.backend.models.ApplicationInfo;
import org.dpppt.android.sdk.internal.backend.BackendBucketRepository;
import org.dpppt.android.sdk.internal.backend.ServerTimeOffsetException;
import org.dpppt.android.sdk.internal.backend.StatusCodeException;
import org.dpppt.android.sdk.internal.backend.SyncErrorState;
import org.dpppt.android.sdk.internal.database.Database;
import org.dpppt.android.sdk.internal.logger.Logger;

import static org.dpppt.android.sdk.internal.backend.BackendBucketRepository.BATCH_LENGTH;

public class SyncWorker extends Worker {

	private static final String TAG = "SyncWorker";
	private static final String WORK_TAG = "org.dpppt.android.sdk.internal.SyncWorker";

	private static PublicKey bucketSignaturePublicKey;

	public static void startSyncWorker(Context context) {
		Constraints constraints = new Constraints.Builder()
				.setRequiredNetworkType(NetworkType.CONNECTED)
				.build();

		PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
				.setConstraints(constraints)
				.build();

		WorkManager workManager = WorkManager.getInstance(context);
		workManager.enqueueUniquePeriodicWork(WORK_TAG, ExistingPeriodicWorkPolicy.KEEP, periodicWorkRequest);
	}

	public static void stopSyncWorker(Context context) {
		WorkManager workManager = WorkManager.getInstance(context);
		workManager.cancelAllWorkByTag(WORK_TAG);
	}

	public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
	}

	public static void setBucketSignaturePublicKey(PublicKey publicKey) {
		bucketSignaturePublicKey = publicKey;
	}

	@NonNull
	@Override
	public Result doWork() {
		Logger.d(TAG, "start SyncWorker");
		Context context = getApplicationContext();

		long scanInterval = AppConfigManager.getInstance(getApplicationContext()).getScanInterval();
		TracingService.scheduleNextClientRestart(context, scanInterval);
		TracingService.scheduleNextServerRestart(context);

		try {
			doSync(context);
		} catch (IOException | StatusCodeException | ServerTimeOffsetException | SignatureException | SQLiteException e) {
			Logger.d(TAG, "SyncWorker finished with exception " + e.getMessage());
			return Result.retry();
		}
		Logger.d(TAG, "SyncWorker finished with success");
		return Result.success();
	}

	public static void doSync(Context context)
			throws IOException, StatusCodeException, ServerTimeOffsetException, SQLiteException, SignatureException {
		try {
//			testMatching(context);
			doSyncInternal(context);
			Logger.i(TAG, "synced");
			AppConfigManager.getInstance(context).setLastSyncNetworkSuccess(true);
			SyncErrorState.getInstance().setSyncError(null);
			BroadcastHelper.sendErrorUpdateBroadcast(context);
		} catch (IOException | StatusCodeException | ServerTimeOffsetException | SignatureException | SQLiteException e) {
			Logger.e(TAG, e);
			AppConfigManager.getInstance(context).setLastSyncNetworkSuccess(false);
			ErrorState syncError;
			if (e instanceof ServerTimeOffsetException) {
				syncError = ErrorState.SYNC_ERROR_TIMING;
			} else if (e instanceof SignatureException) {
				syncError = ErrorState.SYNC_ERROR_SIGNATURE;
			} else if (e instanceof StatusCodeException || e instanceof InvalidProtocolBufferException) {
				syncError = ErrorState.SYNC_ERROR_SERVER;
			} else if (e instanceof SQLiteException) {
				syncError = ErrorState.SYNC_ERROR_DATABASE;
			} else {
				syncError = ErrorState.SYNC_ERROR_NETWORK;
			}
			SyncErrorState.getInstance().setSyncError(syncError);
			BroadcastHelper.sendErrorUpdateBroadcast(context);
			throw e;
		}
	}

	private static void doSyncInternal(Context context) throws IOException, StatusCodeException, ServerTimeOffsetException {
		AppConfigManager appConfigManager = AppConfigManager.getInstance(context);
		appConfigManager.updateFromDiscoverySynchronous();
		ApplicationInfo appConfig = appConfigManager.getAppConfig();

		Database database = new Database(context);
		// This step generates contacts and deletes handshakes. So comment it out.
//		database.generateContactsFromHandshakes(context);
//		appConfigManager.setLastLoadedBatchReleaseTime(System.currentTimeMillis()-BATCH_LENGTH*100);

		long lastLoadedBatchReleaseTime = appConfigManager.getLastLoadedBatchReleaseTime();
		long nextBatchReleaseTime;
		if (lastLoadedBatchReleaseTime <= 0 || lastLoadedBatchReleaseTime % BATCH_LENGTH != 0) {
			long now = System.currentTimeMillis();
			nextBatchReleaseTime = now - (now % BATCH_LENGTH);
		} else {
			nextBatchReleaseTime = lastLoadedBatchReleaseTime + BATCH_LENGTH;
		}

		BackendBucketRepository backendBucketRepository =
				new BackendBucketRepository(context, appConfig.getBucketBaseUrl(), bucketSignaturePublicKey);

		for (long batchReleaseTime = nextBatchReleaseTime;
			 batchReleaseTime < System.currentTimeMillis();
			 batchReleaseTime += BATCH_LENGTH) {
			HashSet<String> infectedHashes = new HashSet<>();
			System.out.println("Getting exposed hashes from server");

			infectedHashes.addAll(backendBucketRepository.getExposeeHashes(batchReleaseTime));
			System.out.println("Received:"+ infectedHashes);
			int numberOfMatches = 0;
			ArrayList<String> receivedHashes = database.getReceivedBtLocHashes();
			for(String hash: receivedHashes){
				if(infectedHashes.contains(hash)){
					numberOfMatches ++;
				}
			}
			if(numberOfMatches>0){
				// TODO exposure calculation
				System.out.println("YOU MAY BE EXPOSED");
				BroadcastHelper.sendUpdateBroadcast(context);
			}
			appConfigManager.setLastLoadedBatchReleaseTime(batchReleaseTime);
		}

		database.removeOldData();

		appConfigManager.setLastSyncDate(System.currentTimeMillis());
	}
	public static void testMatching(Context context) {
		try {
			System.out.println("In Testing");
			AppConfigManager appConfigManager = AppConfigManager.getInstance(context);
			appConfigManager.updateFromDiscoverySynchronous();
			ApplicationInfo appConfig = appConfigManager.getAppConfig();
			Database database = new Database(context);
			BackendBucketRepository backendBucketRepository = new BackendBucketRepository(context, appConfig.getBucketBaseUrl(), bucketSignaturePublicKey);
//			}appConfigManager.setLastLoadedBatchReleaseTime(System.currentTimeMillis()-BATCH_LENGTH*2);

			// Test time to fetch X hashes from database
			int testIterations = 20;
			long noTestHashes = database.getTestHashesCount();
			System.out.println("Number of local hashes" + noTestHashes);
			if(noTestHashes<5000){
				database.addTestHashes(generateTestHashes(5000-noTestHashes, 10));
			}
			boolean testing = true;
			if(testing){
				ArrayList<String> test = database.getTestHashes(2000);
				System.out.println(test.size()+" "+test.get(0)+" "+test.get(10));
//				backendBucketRepository.getTestExposeeHashes(0);
//				return;
			}
			ArrayList<ArrayList<String>> dbHashes = new ArrayList<>();
			ArrayList<Double> dbFetchTimes = new ArrayList<>();
			System.out.println("\nAverage Time to fetch hashes from database ("+testIterations+"iterations)");
			System.out.println("#\tns\tms:");
			double fetchDbAvg;
			ArrayList<String> receivedHashes;
			for(int j = 1000;j<=3000;j+=1000) {
				fetchDbAvg = 0;
				receivedHashes = null;
				for (int i = 0; i < testIterations; i++){
					long fetchDbStart = System.nanoTime();
					receivedHashes = database.getTestHashes(j);
					long fetchDbEnd = System.nanoTime();
					fetchDbAvg += (fetchDbEnd - fetchDbStart);
//					System.out.print(fetchDbEnd - fetchDbStart + " ");
				}
				fetchDbAvg = fetchDbAvg / testIterations;
				System.out.println(receivedHashes.size() + " " + (fetchDbAvg)+" "+String.format("%.4f",(fetchDbAvg)/1000000));
				dbHashes.add(receivedHashes);
				dbFetchTimes.add(fetchDbAvg);
			}
//			1000 500000 1308022.8333333333 1.3080 0
//			1000 750000 143854.12777777776 0.1439 0
//			1000 1000000 98074.63759259258 0.0981 0
//			2000 500000 179727.43333333332 0.1797 0
//			2000 750000 210560.4477777778 0.2106 0
//			2000 1000000 188352.08159259259 0.1884 0
//			3000 500000 273008.6 0.2730 0
//			3000 750000 350534.32 0.3505 0
//			3000 1000000 307189.7106666667 0.3072 0


			// Test time to fetch Y hashes from server
			ArrayList<HashSet<String>> serverHashes = new ArrayList<>();
			ArrayList<Double> serverFetchTimes = new ArrayList<>();

			double fetchServerAvg;
			HashSet<String> infectedHashes = null;
			for(int j = 500000;j<=1000000;j+=250000) {
				fetchServerAvg = 0;
//				Thread.sleep(2000);
				for (int i = 0; i < testIterations; i++) {
					infectedHashes = null;
					Thread.sleep(1000);
					long fetchServerStart = System.nanoTime();
					infectedHashes = backendBucketRepository.getTestExposeeHashes(j);
					long fetchServerEnd = System.nanoTime();
					fetchServerAvg += (fetchServerEnd - fetchServerStart);
//					System.out.print(fetchDbEnd - fetchDbStart + " ");
				}
				fetchServerAvg = fetchServerAvg / testIterations;
				serverHashes.add(infectedHashes);
				serverFetchTimes.add(fetchServerAvg);
			}
			System.out.println("\nAverage Time to fetch hashes from server ("+testIterations+"iterations)");
			System.out.println("#\tns\tms:");
			for(int i=0;i<serverFetchTimes.size();i++){
				System.out.println(serverHashes.get(i).size() + " " + serverFetchTimes.get(i)+" "+String.format("%.4f",(serverFetchTimes.get(i)/1000000)));
			}

			// Test time to match X hashes from database with Y hashes server
			System.out.println("\nAverage Time to match #X local hashes with #Y hashes from server ("+testIterations+"iterations)");
			System.out.println("#X\t#Y\tns\tms\t#matches:");
			double matchAvg;
			int numberOfMatches;
			for(ArrayList<String> dbHashList: dbHashes){
				numberOfMatches = 0;
				matchAvg = 0;
				for(HashSet<String> serverHashList: serverHashes){
					for(int i = 0;i<testIterations;i++) {
						long matchStart = System.nanoTime();
						numberOfMatches = 0;
						for (String hash : dbHashList) {
							if (serverHashList.contains(hash)) {
								numberOfMatches++;
							}
						}
						long matchEnd = System.nanoTime();
						matchAvg += (matchEnd - matchStart);
					}
					matchAvg = matchAvg/30;
					System.out.println(dbHashList.size()+" "+serverHashList.size()+" "+matchAvg+" "+String.format("%.4f",matchAvg/1000000)+" "+numberOfMatches);
				}
			}
		} catch (StatusCodeException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
//		catch (InterruptedException e) {
//			e.printStackTrace();
//		}
	}

	private static ArrayList<String> generateTestHashes(long count, int hashBytes) {
		char[] hexDigits = {'0', '1', '2', '3', '4', '5',
				'6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
		Random random = new Random();
		ArrayList<String> randomHashes = new ArrayList<>();
		for (int j = 0; j < count; j++){
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < hashBytes*2; i++) {
				sb.append(hexDigits[random.nextInt(hexDigits.length)]);
			}
			randomHashes.add(sb.toString());
//			System.out.println(sb.toString());
	}
	return randomHashes;
	}

}
