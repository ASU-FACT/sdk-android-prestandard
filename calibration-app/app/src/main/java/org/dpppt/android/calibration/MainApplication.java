/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package org.dpppt.android.calibration;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.security.PublicKey;

import org.dpppt.android.calibration.util.NotificationUtil;
import org.dpppt.android.calibration.util.PreferencesUtil;
import org.dpppt.android.sdk.DP3T;
import org.dpppt.android.sdk.backend.models.ApplicationInfo;
import org.dpppt.android.sdk.internal.logger.LogLevel;
import org.dpppt.android.sdk.internal.logger.Logger;
import org.dpppt.android.sdk.internal.util.ProcessUtil;
import org.dpppt.android.sdk.util.SignatureUtil;

import okhttp3.CertificatePinner;

public class MainApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		if (ProcessUtil.isMainProcess(this)) {
			registerReceiver(sdkReceiver, DP3T.getUpdateIntentFilter());
			initDP3T(this);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationUtil.createNotificationChannel(this);
		}
		Logger.init(getApplicationContext(), LogLevel.DEBUG);
	}

	public static void initDP3T(Context context) {
		PublicKey publicKey = SignatureUtil.getPublicKeyFromBase64OrThrow(
				"LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0NCk1Ga3dFd1lIS29aSXpqMENBUVlJS29aSXpqMERBUWNEUWdBRTUxb0ZVUTVCTXlXdGRuUEkwUHVZc3QyNXE2dXQNCklRMTU3Yy9uYXN1TkozbEN2T0lFU0lDZFhwT1FUUUdWNisxWDh1WStOWmZ5WlFTR090R3hxTFdOcmc9PQ0KLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0tDQo=");

//		DP3T.init(context, "org.dpppt.demo", true, publicKey);
		// Added this
		DP3T.init(context,new ApplicationInfo("org.dpppt.demo","http://192.168.1.9:8080","http://192.168.1.9:8082","http://192.168.1.9:8082"),publicKey);
		CertificatePinner certificatePinner = new CertificatePinner.Builder()
				.add("http://192.168.1.9", "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg=")
				.build();
		DP3T.setCertificatePinner(certificatePinner);
	}

	@Override
	public void onTerminate() {
		if (ProcessUtil.isMainProcess(this)) {
			unregisterReceiver(sdkReceiver);
		}
		super.onTerminate();
	}

	private BroadcastReceiver sdkReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (DP3T.getStatus(context).getExposureDays().size() > 0 && !PreferencesUtil.isExposedNotificationShown(context)) {
				NotificationUtil.showNotification(context, R.string.push_exposed_title,
						R.string.push_exposed_text, R.drawable.ic_handshakes);
				PreferencesUtil.setExposedNotificationShown(context);
			}
		}
	};

}