package com.shotabra.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/** {@code @DynamicUpdate}: solo se escriben las columnas modificadas (la presencia se actualiza aparte). */
@Entity
@DynamicUpdate
@Table(name = "player")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(nullable = false, unique = true)
    private UUID token;

    @Column(nullable = false, length = 16)
    private String name;

    @Column(nullable = false, length = 16)
    private String emoji;

    @Column(nullable = false)
    private int shots;

    @Column(nullable = false)
    private boolean connected;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected Player() {
    }

    public Player(Long partyId, String name, String emoji, Instant now) {
        this.partyId = partyId;
        this.token = UUID.randomUUID();
        this.name = name;
        this.emoji = emoji;
        this.joinedAt = now;
        this.disconnectedAt = now;
    }

    public Long getId() { return id; }
    public Long getPartyId() { return partyId; }
    public UUID getToken() { return token; }
    public String getName() { return name; }
    public String getEmoji() { return emoji; }
    public int getShots() { return shots; }
    public void addShot() { this.shots++; }
    public boolean isConnected() { return connected; }
    public Instant getDisconnectedAt() { return disconnectedAt; }
    public Instant getJoinedAt() { return joinedAt; }

    public void markConnected() {
        this.connected = true;
        this.disconnectedAt = null;
    }

    public void markDisconnected(Instant now) {
        this.connected = false;
        this.disconnectedAt = now;
    }
}
