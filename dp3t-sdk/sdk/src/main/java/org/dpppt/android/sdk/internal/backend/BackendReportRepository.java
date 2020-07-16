/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package org.dpppt.android.sdk.internal.backend;

import android.content.Context;
import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;

import org.dpppt.android.sdk.backend.ResponseCallback;
import org.dpppt.android.sdk.backend.models.ExposeeAuthMethod;
import org.dpppt.android.sdk.backend.models.ExposeeAuthMethodAuthorization;
import org.dpppt.android.sdk.internal.backend.models.ExposeeRequest;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class BackendReportRepository implements Repository {

	private ReportService reportService1;
	private ReportService reportService2;

	public BackendReportRepository(@NonNull Context context, String reportBaseUrl1,String reportBaseUrl2) {
		Retrofit reportRetrofit1 = new Retrofit.Builder()
				.baseUrl(reportBaseUrl1)
				.client(getClientBuilder(context).build())
				.addConverterFactory(GsonConverterFactory.create())
				.build();
		Retrofit reportRetrofit2 = new Retrofit.Builder()
				.baseUrl(reportBaseUrl2)
				.client(getClientBuilder(context).build())
				.addConverterFactory(GsonConverterFactory.create())
				.build();

		reportService1 = reportRetrofit1.create(ReportService.class);
		reportService2 = reportRetrofit2.create(ReportService.class);
	}
	public void testGetCall(@NonNull ResponseCallback<Void> responseCallback){
		System.out.println("In BackendReportRepository Test Backend");
		reportService2.getBackend().enqueue(new Callback<Void>() {
			@Override
			public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
				if (response.isSuccessful()) {
					responseCallback.onSuccess(null);
				} else {
					onFailure(call, new StatusCodeException(response.raw()));
				}
			}

			@Override
			public void onFailure(@NonNull Call<Void> call, @NonNull Throwable throwable) {
				responseCallback.onError(throwable);
			}
		});
	}
	public void sendHotspotVectors(ArrayList<Integer> hotspotRandomVectorA,ArrayList<Integer> hotspotRandomVectorB, @NonNull ResponseCallback<Void> responseCallback){
		System.out.println("In BackendReportRepository Test Backend");

		reportService1.addHotspot(hotspotRandomVectorA).enqueue(new Callback<Void>() {
			@Override
			public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
				if (response.isSuccessful()) {
					responseCallback.onSuccess(null);
				} else {
					onFailure(call, new StatusCodeException(response.raw()));
				}
			}

			@Override
			public void onFailure(@NonNull Call<Void> call, @NonNull Throwable throwable) {
				responseCallback.onError(throwable);
			}
		});
		reportService2.addHotspot(hotspotRandomVectorB).enqueue(new Callback<Void>() {
			@Override
			public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
				if (response.isSuccessful()) {
					responseCallback.onSuccess(null);
				} else {
					onFailure(call, new StatusCodeException(response.raw()));
				}
			}

			@Override
			public void onFailure(@NonNull Call<Void> call, @NonNull Throwable throwable) {
				responseCallback.onError(throwable);
			}
		});
	}
	public void addExposee(@NonNull ExposeeRequest exposeeRequest, ExposeeAuthMethod exposeeAuthMethod,
			@NonNull ResponseCallback<Void> responseCallback) {
		String authorizationHeader = exposeeAuthMethod instanceof ExposeeAuthMethodAuthorization
									 ? ((ExposeeAuthMethodAuthorization) exposeeAuthMethod).getAuthorization()
									 : null;
		reportService1.addExposee(exposeeRequest, authorizationHeader).enqueue(new Callback<Void>() {
			@Override
			public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
				if (response.isSuccessful()) {
					responseCallback.onSuccess(null);
				} else {
					onFailure(call, new StatusCodeException(response.raw()));
				}
			}

			@Override
			public void onFailure(@NonNull Call<Void> call, @NonNull Throwable throwable) {
				responseCallback.onError(throwable);
			}
		});
	}

	public void addExposeeSync(@NonNull ExposeeRequest exposeeRequest, ExposeeAuthMethod exposeeAuthMethod) throws IOException {
		String authorizationHeader = exposeeAuthMethod instanceof ExposeeAuthMethodAuthorization
									 ? ((ExposeeAuthMethodAuthorization) exposeeAuthMethod).getAuthorization()
									 : null;
		reportService1.addExposee(exposeeRequest, authorizationHeader).execute();
	}

}
