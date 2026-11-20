package com.shotabra.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.shotabra.domain.Party;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartyRepository extends JpaRepository<Party, Long> {

    Optional<Party> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Carga la party con {@code SELECT ... FOR UPDATE}: todas las acciones de juego sobre
     * una misma party quedan serializadas hasta que termine la transacción.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Party p where p.id = :id")
    Optional<Party> findByIdForUpdate(@Param("id") Long id);

    @Query("select p.id from Party p where p.lastActivityAt < :cutoff")
    List<Long> findIdsInactiveSince(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("update Party p set p.hostPlayerId = null where p.id in :ids")
    int clearHosts(@Param("ids") List<Long> ids);

    @Modifying
    @Query("delete from Party p where p.id in :ids")
    int deleteAllByIdIn(@Param("ids") List<Long> ids);
}
