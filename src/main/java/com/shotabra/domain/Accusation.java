package com.shotabra.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "accusation")
public class Accusation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "accuser_id", nullable = false)
    private Long accuserId;

    @Column(name = "accused_id", nullable = false)
    private Long accusedId;

    @Column(name = "word_id", nullable = false)
    private Long wordId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccusationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Accusation() {
    }

    public Accusation(Long partyId, int roundNumber, Long accuserId, Long accusedId, Long wordId, Instant now) {
        this.partyId = partyId;
        this.roundNumber = roundNumber;
        this.accuserId = accuserId;
        this.accusedId = accusedId;
        this.wordId = wordId;
        this.status = AccusationStatus.PENDING;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public Long getPartyId() { return partyId; }
    public int getRoundNumber() { return roundNumber; }
    public Long getAccuserId() { return accuserId; }
    public Long getAccusedId() { return accusedId; }
    public Long getWordId() { return wordId; }
    public AccusationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }

    public boolean isPending() {
        return status == AccusationStatus.PENDING;
    }

    public void resolve(AccusationStatus newStatus, Instant now) {
        this.status = newStatus;
        this.resolvedAt = now;
    }
}
