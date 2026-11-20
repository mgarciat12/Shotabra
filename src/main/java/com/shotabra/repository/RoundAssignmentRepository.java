package com.shotabra.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.shotabra.domain.RoundAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoundAssignmentRepository extends JpaRepository<RoundAssignment, Long> {

    List<RoundAssignment> findByPartyIdAndRoundNumber(Long partyId, int roundNumber);

    Optional<RoundAssignment> findByPartyIdAndRoundNumberAndHunterPlayerId(Long partyId, int roundNumber, Long hunterPlayerId);

    /** Palabras ya usadas en cualquier ronda de esta party. */
    @Query("select distinct a.wordId from RoundAssignment a where a.partyId = :partyId")
    Set<Long> findUsedWordIds(@Param("partyId") Long partyId);
}
