package org.dpppt.android.sdk.internal.backend.models;

/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

import java.util.ArrayList;

public class Exposee {

    private Integer Id;

    private String key;

    private long keyDate;

    private ArrayList<String> hashes;

    public ArrayList<String> getHashes() {
        return hashes;
    }

    public void setHashes(ArrayList<String> hashes) {
        this.hashes = new ArrayList<String>();
        this.hashes.addAll(hashes);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }


    public Integer getId() {
        return Id;
    }

    public void setId(Integer id) {
        Id = id;
    }

    public long getKeyDate() {
        return keyDate;
    }

    public void setKeyDate(long keyDate) {
        this.keyDate = keyDate;
    }
}

