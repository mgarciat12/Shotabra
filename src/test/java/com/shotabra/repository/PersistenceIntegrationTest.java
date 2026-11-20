package com.shotabra.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import com.shotabra.IntegrationTest;
import com.shotabra.domain.Accusation;
import com.shotabra.domain.AccusationStatus;
import com.shotabra.domain.Party;
import com.shotabra.domain.Player;
import com.shotabra.domain.RoundAssignment;
import com.shotabra.domain.Word;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** Migraciones Flyway + mapeo JPA + restricciones de BD contra PostgreSQL real. */
@Transactional
class PersistenceIntegrationTest extends IntegrationTest {

    @Autowired PartyRepository parties;
    @Autowired PlayerRepository players;
    @Autowired WordRepository words;
    @Autowired RoundAssignmentRepository assignments;
    @Autowired AccusationRepository accusations;

    private final Instant now = Instant.parse("2026-01-01T20:00:00Z");

    @Test
    void seedHasAtLeast300ActiveWords() {
        assertThat(words.countByActiveTrue()).isGreaterThanOrEqualTo(300);
        assertThat(words.findByActiveTrue()).extracting(Word::getText).contains("piña", "coche", "cerveza", "móvil");
    }

    @Test
    void savesAndLoadsPartyWithPlayers() {
        Party party = parties.save(new Party("ABCD", now));
        Player ana = players.save(new Player(party.getId(), "Ana", "🍕", now));
        party.setHostPlayerId(ana.getId());
        parties.flush();

        assertThat(parties.findByCode("ABCD")).get().extracting(Party::getHostPlayerId).isEqualTo(ana.getId());
        assertThat(parties.findByIdForUpdate(party.getId())).isPresent();
        assertThat(players.findByToken(ana.getToken())).get().extracting(Player::getName).isEqualTo("Ana");
        assertThat(players.existsByPartyIdAndNameIgnoreCase(party.getId(), "ANA")).isTrue();
    }

    @Test
    void nameIsUniqueIgnoringCaseWithinParty() {
        Party party = parties.save(new Party("BCDE", now));
        players.saveAndFlush(new Player(party.getId(), "Ana", "🍕", now));

        assertThatThrownBy(() -> players.saveAndFlush(new Player(party.getId(), "ana", "🐶", now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void emojiIsUniqueWithinParty() {
        Party party = parties.save(new Party("CDEF", now));
        players.saveAndFlush(new Player(party.getId(), "Ana", "🍕", now));

        assertThatThrownBy(() -> players.saveAndFlush(new Player(party.getId(), "Luis", "🍕", now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseAllowsOnlyOneConfirmedAccusationPerRound() {
        Party party = parties.save(new Party("DEFG", now));
        List<Player> ps = players.saveAllAndFlush(List.of(
                new Player(party.getId(), "Ana", "🍕", now),
                new Player(party.getId(), "Luis", "🐶", now),
                new Player(party.getId(), "Eva", "🎸", now)));
        Long word = words.findByActiveTrue().getFirst().getId();

        Accusation first = new Accusation(party.getId(), 1, ps.get(0).getId(), ps.get(1).getId(), word, now);
        first.resolve(AccusationStatus.CONFIRMED, now);
        accusations.saveAndFlush(first);

        Accusation second = new Accusation(party.getId(), 1, ps.get(1).getId(), ps.get(2).getId(), word, now);
        second.resolve(AccusationStatus.CONFIRMED, now);
        assertThatThrownBy(() -> accusations.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void assignmentCannotTargetSelf() {
        Party party = parties.save(new Party("EFGH", now));
        Player ana = players.saveAndFlush(new Player(party.getId(), "Ana", "🍕", now));
        Long word = words.findByActiveTrue().getFirst().getId();

        assertThatThrownBy(() -> assignments.saveAndFlush(
                new RoundAssignment(party.getId(), 1, ana.getId(), ana.getId(), word)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
