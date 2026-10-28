package com.hyperbrains.platform.service.kuma;

/** Status of one monitored application, as HBS shows it. */
public enum KumaStatus {
    /** Kuma reports status 1. */
    UP,
    /** Kuma reports status 0. */
    DOWN,
    /** Monitor id absent from /metrics: paused or deleted in Kuma (indistinguishable). */
    NO_DATA,
    /** Kuma unreachable, snapshot stale, or a status code we have not observed. Never shown as DOWN. */
    UNKNOWN
}