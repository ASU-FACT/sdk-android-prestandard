package org.dpppt.android.sdk.internal.backend.models;

import java.util.ArrayList;
import java.util.List;

public class ExposedOverview {
    private Long batchReleaseTime;

    private List<Exposee> exposed = new ArrayList<>();

    public ExposedOverview() {
    }

    public Long getBatchReleaseTime() {
        return batchReleaseTime;
    }

    public void setBatchReleaseTime(Long batchReleaseTime) {
        this.batchReleaseTime = batchReleaseTime;
    }

    public ExposedOverview(List<Exposee> exposed) {
        this.exposed = exposed;
    }

    public List<Exposee> getExposed() {
        return exposed;
    }

    public void setExposed(List<Exposee> exposed) {
        this.exposed = exposed;
    }
}
