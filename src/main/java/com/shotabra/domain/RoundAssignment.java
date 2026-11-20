package com.shotabra.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** "En la ronda N, {@code hunter} debe hacer que {@code target} diga {@code word}". */
@Entity
@Table(name = "round_assignment")
public class RoundAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "hunter_player_id", nullable = false)
    private Long hunterPlayerId;

    @Column(name = "target_player_id", nullable = false)
    private Long targetPlayerId;

    @Column(name = "word_id", nullable = false)
    private Long wordId;

    protected RoundAssignment() {
    }

    public RoundAssignment(Long partyId, int roundNumber, Long hunterPlayerId, Long targetPlayerId, Long wordId) {
        this.partyId = partyId;
        this.roundNumber = roundNumber;
        this.hunterPlayerId = hunterPlayerId;
        this.targetPlayerId = targetPlayerId;
        this.wordId = wordId;
    }

    public Long getId() { return id; }
    public Long getPartyId() { return partyId; }
    public int getRoundNumber() { return roundNumber; }
    public Long getHunterPlayerId() { return hunterPlayerId; }
    public Long getTargetPlayerId() { return targetPlayerId; }
    public Long getWordId() { return wordId; }
}
