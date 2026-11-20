package com.shotabra.domain;

public enum AccusationStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    /** Pendiente cuando la ronda terminó por otra acusación o el host volvió al lobby. */
    CANCELLED
}
