/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package org.dpppt.android.sdk.internal.backend.models;

import org.dpppt.android.sdk.backend.models.ExposeeAuthMethodJson;

import java.util.ArrayList;

public class ExposeeRequest {

	private String key;
	private ArrayList<String> hashes;
	private long keyDate;
	private ExposeeAuthMethodJson authData;
	private int fake;

	public ExposeeRequest(String key, ArrayList<String> hashes, long keyDate, ExposeeAuthMethodJson authData) {
		this(key, hashes, keyDate, 0, authData);
	}

	public ExposeeRequest(String key, ArrayList<String> hashes, long keyDate, int fake, ExposeeAuthMethodJson authData) {
		this.key = key;
		if(hashes!=null){
			this.hashes = new ArrayList<>();
			this.hashes.addAll(hashes);
		}
		this.keyDate = keyDate;
		this.authData = authData;
		this.fake = fake;
	}

	public String getKey() {
		return key;
	}

	public long getKeyDate() {
		return keyDate;
	}

	public ArrayList<String> getHashes() {
		return hashes;
	}

	public void setHashes(ArrayList<String> hashes) {
		this.hashes = hashes;
	}

	public int getFake() {
		return fake;
	}

	public ExposeeAuthMethodJson getAuthData() {
		return authData;
	}

}
