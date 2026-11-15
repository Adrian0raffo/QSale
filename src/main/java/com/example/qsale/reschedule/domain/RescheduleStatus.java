package com.example.qsale.reschedule.domain;

public enum RescheduleStatus {
    VOTING,
    APPROVED,
    REJECTED,
    APPLIED,
    WITHDRAWN,
    EXPIRED,
    SUPERSEDED;

    public boolean isActive() {
        return this == VOTING || this == APPROVED;
    }
}
