package com.shotabra.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shotabra.domain.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByToken(UUID token);

    @Query("select p.partyId from Player p where p.token = :token")
    Optional<Long> findPartyIdByToken(@Param("token") UUID token);

    // Presencia: UPDATE de solo esas columnas para no pisar cambios concurrentes (p. ej. shots).
    @Modifying
    @Query("update Player p set p.connected = true, p.disconnectedAt = null where p.token = :token and p.connected = false")
    int markConnected(@Param("token") UUID token);

    @Modifying
    @Query("update Player p set p.connected = false, p.disconnectedAt = :now where p.token = :token and p.connected = true")
    int markDisconnected(@Param("token") UUID token, @Param("now") Instant now);

    @Modifying
    @Query("update Player p set p.connected = false, p.disconnectedAt = :now where p.connected = true")
    int markAllDisconnected(@Param("now") Instant now);

    List<Player> findByPartyIdOrderByJoinedAtAscIdAsc(Long partyId);

    long countByPartyId(Long partyId);

    boolean existsByPartyIdAndNameIgnoreCase(Long partyId, String name);

    boolean existsByPartyIdAndEmoji(Long partyId, String emoji);

    @Query("select p.emoji from Player p where p.partyId = :partyId")
    List<String> findEmojisByPartyId(@Param("partyId") Long partyId);

    /** Hosts desconectados desde antes de {@code cutoff} (candidatos a ceder el rol). */
    @Query("""
            select pl.partyId from Player pl, Party pa
            where pa.hostPlayerId = pl.id
              and pl.connected = false
              and pl.disconnectedAt < :cutoff
            """)
    List<Long> findPartyIdsWithHostDisconnectedSince(@Param("cutoff") Instant cutoff);
}
