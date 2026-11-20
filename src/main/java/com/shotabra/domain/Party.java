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
import jakarta.persistence.Version;

@Entity
@Table(name = "party")
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 4, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PartyStatus status;

    @Column(name = "host_player_id")
    private Long hostPlayerId;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Version
    private long version;

    protected Party() {
    }

    public Party(String code, Instant now) {
        this.code = code;
        this.status = PartyStatus.LOBBY;
        this.createdAt = now;
        this.lastActivityAt = now;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public PartyStatus getStatus() { return status; }
    public void setStatus(PartyStatus status) { this.status = status; }
    public Long getHostPlayerId() { return hostPlayerId; }
    public void setHostPlayerId(Long hostPlayerId) { this.hostPlayerId = hostPlayerId; }
    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public void touch(Instant now) { this.lastActivityAt = now; }
    public long getVersion() { return version; }

    public boolean isHost(Player player) {
        return player != null && player.getId().equals(hostPlayerId);
    }
}
