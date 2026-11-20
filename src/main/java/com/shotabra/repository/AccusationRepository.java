package com.shotabra.repository;

import java.time.Instant;
import java.util.Optional;

import com.shotabra.domain.Accusation;
import com.shotabra.domain.AccusationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccusationRepository extends JpaRepository<Accusation, Long> {

    Optional<Accusation> findFirstByAccuserIdAndStatus(Long accuserId, AccusationStatus status);

    Optional<Accusation> findFirstByAccusedIdAndStatus(Long accusedId, AccusationStatus status);

    Optional<Accusation> findFirstByPartyIdAndRoundNumberAndStatus(Long partyId, int roundNumber, AccusationStatus status);

    /** Cancela todas las acusaciones pendientes de una party (fin de ronda o vuelta al lobby). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Accusation a set a.status = com.shotabra.domain.AccusationStatus.CANCELLED, a.resolvedAt = :now
            where a.partyId = :partyId and a.status = com.shotabra.domain.AccusationStatus.PENDING
            """)
    int cancelPending(@Param("partyId") Long partyId, @Param("now") Instant now);
}
