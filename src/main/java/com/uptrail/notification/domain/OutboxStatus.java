package com.uptrail.notification.domain;

public enum OutboxStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED
}
