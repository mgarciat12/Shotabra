package com.shotabra.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.shotabra.IntegrationTest;
import com.shotabra.MutableClock;
import com.shotabra.domain.AccusationStatus;
import com.shotabra.domain.PartyStatus;
import com.shotabra.game.event.GameEvent;
import com.shotabra.game.view.JoinResult;
import com.shotabra.game.view.OutgoingAccusationView;
import com.shotabra.game.view.RoundResultView;
import com.shotabra.game.view.StateView;
import com.shotabra.repository.AccusationRepository;
import com.shotabra.repository.PartyRepository;
import com.shotabra.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@RecordApplicationEvents
class GameServiceTest extends IntegrationTest {

    @Autowired GameService game;
    @Autowired PartyRepository parties;
    @Autowired PlayerRepository players;
    @Autowired AccusationRepository accusations;
    @Autowired MutableClock clock;
    @Autowired ApplicationEvents events;

    // ------------------------------------------------------------------ crear / unirse

    @Test
    void creatingAPartyMakesTheCreatorHost() {
        JoinResult host = game.createParty("  Ana ", "🍕");

        StateView state = game.getState(host.playerToken());
        assertThat(host.code()).matches("[A-Z]{4}");
        assertThat(state.me().name()).isEqualTo("Ana");
        assertThat(state.me().host()).isTrue();
        assertThat(state.party().status()).isEqualTo(PartyStatus.LOBBY);
    }

    @Test
    void joinValidatesNameAndEmojiUniqueness() {
        JoinResult host = game.createParty("Ana", "🍕");

        assertError(() -> game.joinParty(host.code(), "ANA", "🐶"), GameError.NAME_TAKEN);
        assertError(() -> game.joinParty(host.code(), "Luis", "🍕"), GameError.EMOJI_TAKEN);
        assertError(() -> game.joinParty(host.code(), "Luis", "🍌"), GameError.INVALID_EMOJI);
        assertError(() -> game.joinParty(host.code(), "x".repeat(17), "🐶"), GameError.INVALID_NAME);
        assertError(() -> game.joinParty("ZZZZ", "Luis", "🐶"), GameError.PARTY_NOT_FOUND);

        JoinResult luis = game.joinParty(host.code().toLowerCase(), "Luis", "🐶");
        assertThat(game.getState(luis.playerToken()).players()).extracting("name").containsExactly("Ana", "Luis");
        assertThat(game.partyInfo(host.code()).takenEmojis()).containsExactlyInAnyOrder("🍕", "🐶");
        assertThat(events.stream(GameEvent.PlayerJoined.class)).anyMatch(e -> e.player().name().equals("Luis"));
    }

    // ------------------------------------------------------------------ inicio y asignaciones

    @Test
    void onlyTheHostCanStartAndNeedsAtLeastThreePlayers() {
        Table t = table(2);
        assertError(() -> game.startGame(t.token(1)), GameError.NOT_HOST);
        assertError(() -> game.startGame(t.host()), GameError.NOT_ENOUGH_PLAYERS);

        game.joinParty(t.code(), "Tercero", "🦊");
        game.startGame(t.host());
        assertThat(game.getState(t.host()).party().status()).isEqualTo(PartyStatus.PLAYING);
    }

    @Test
    void startingDealsADerangementWithDistinctWordsAndEachPlayerOnlySeesTheirOwnCard() {
        Table t = table(6);
        game.startGame(t.host());

        Set<Long> targets = new HashSet<>();
        Set<String> words = new HashSet<>();
        for (JoinResult p : t.players()) {
            StateView s = game.getState(p.playerToken());
            assertThat(s.assignment()).isNotNull();
            assertThat(s.assignment().target().id()).isNotEqualTo(p.playerId());
            assertThat(s.result()).as("sin revelación durante el juego").isNull();
            targets.add(s.assignment().target().id());
            words.add(s.assignment().word());
        }
        assertThat(targets).hasSize(6);
        assertThat(words).hasSize(6);

        // El evento de inicio lleva una tarjeta privada por jugador (el notifier las reparte por separado).
        GameEvent.RoundStarted started = events.stream(GameEvent.RoundStarted.class)
                .filter(e -> e.partyCode().equals(t.code())).findFirst().orElseThrow();
        assertThat(started.assignments()).hasSize(6);
    }

    @Test
    void wordsAreNotRepeatedAcrossRoundsWhileThereAreUnusedOnes() {
        Table t = table(5);
        Set<String> seen = new HashSet<>();
        for (int round = 1; round <= 5; round++) {
            if (round == 1) {
                game.startGame(t.host());
            } else {
                game.restartRound(t.host());
            }
            for (JoinResult p : t.players()) {
                assertThat(seen.add(game.getState(p.playerToken()).assignment().word()))
                        .as("palabra repetida en la ronda %d", round).isTrue();
            }
            loseRound(t);
        }
    }

    // ------------------------------------------------------------------ acusaciones

    @Test
    void aPlayerCannotHaveTwoPendingAccusations() {
        Table t = table(3);
        game.startGame(t.host());

        game.accuse(t.host());
        assertError(() -> game.accuse(t.host()), GameError.ACCUSATION_ALREADY_PENDING);
    }

    @Test
    void nobodyCanConfirmOrRejectSomeoneElsesAccusation() {
        Table t = table(4);
        game.startGame(t.host());
        OutgoingAccusationView acc = game.accuse(t.host());
        UUID accused = tokenOf(t, acc.target().id());
        UUID bystander = t.players().stream().map(JoinResult::playerToken)
                .filter(tok -> !tok.equals(accused) && !tok.equals(t.host())).findFirst().orElseThrow();

        assertError(() -> game.confirm(bystander, acc.id()), GameError.NOT_YOUR_ACCUSATION);
        assertError(() -> game.confirm(t.host(), acc.id()), GameError.NOT_YOUR_ACCUSATION);
        assertError(() -> game.reject(bystander, acc.id()), GameError.NOT_YOUR_ACCUSATION);
        assertThat(game.getState(t.host()).party().status()).isEqualTo(PartyStatus.PLAYING);
    }

    @Test
    void accusedSeesTheModalStateAndRejectingLetsTheAccuserTryAgain() {
        Table t = table(3);
        game.startGame(t.host());
        OutgoingAccusationView acc = game.accuse(t.host());
        UUID accused = tokenOf(t, acc.target().id());

        StateView accusedState = game.getState(accused);
        assertThat(accusedState.incomingAccusation()).isNotNull();
        assertThat(accusedState.incomingAccusation().word()).isEqualTo(acc.word());
        assertThat(game.getState(t.host()).myAccusation().id()).isEqualTo(acc.id());

        game.reject(accused, acc.id());

        assertThat(game.getState(accused).incomingAccusation()).isNull();
        assertThat(game.getState(t.host()).myAccusation()).isNull();
        assertThat(events.stream(GameEvent.AccusationRejected.class)).anyMatch(e -> e.accusationId() == acc.id());
        assertThat(game.accuse(t.host()).id()).isNotEqualTo(acc.id());
    }

    @Test
    void confirmingEndsTheRoundRevealsEverythingAndCancelsOtherAccusations() {
        Table t = table(4);
        game.startGame(t.host());
        OutgoingAccusationView first = game.accuse(t.host());
        UUID loser = tokenOf(t, first.target().id());
        UUID otherHunter = t.players().stream().map(JoinResult::playerToken)
                .filter(tok -> !tok.equals(t.host())).findFirst().orElseThrow();
        OutgoingAccusationView second = game.accuse(otherHunter);

        RoundResultView result = game.confirm(loser, first.id());

        assertThat(result.loser().id()).isEqualTo(first.target().id());
        assertThat(result.catcher().id()).isEqualTo(t.players().getFirst().playerId());
        assertThat(result.word()).isEqualTo(first.word());
        assertThat(result.assignments()).hasSize(4);
        assertThat(result.scoreboard().getFirst().shots()).isEqualTo(1);
        assertThat(accusations.findById(second.id()).orElseThrow().getStatus()).isEqualTo(AccusationStatus.CANCELLED);

        StateView after = game.getState(otherHunter);
        assertThat(after.party().status()).isEqualTo(PartyStatus.ROUND_OVER);
        assertThat(after.result()).isNotNull();
        assertThat(after.myAccusation()).isNull();
        assertError(() -> game.accuse(t.host()), GameError.INVALID_STATE);
    }

    // ------------------------------------------------------------------ transiciones de estado

    @Test
    void stateTransitionsAreEnforced() {
        Table t = table(3);
        assertError(() -> game.accuse(t.host()), GameError.INVALID_STATE);        // LOBBY
        assertError(() -> game.restartRound(t.host()), GameError.INVALID_STATE);  // LOBBY
        assertError(() -> game.backToLobby(t.host()), GameError.INVALID_STATE);   // LOBBY

        game.startGame(t.host());                                                  // → PLAYING
        assertError(() -> game.startGame(t.host()), GameError.INVALID_STATE);
        assertError(() -> game.restartRound(t.host()), GameError.INVALID_STATE);
        assertError(() -> game.joinParty(t.code(), "Tarde", "🦄"), GameError.GAME_IN_PROGRESS);
        assertError(() -> game.leave(t.token(1)), GameError.INVALID_STATE);
        assertError(() -> game.kick(t.host(), t.players().get(1).playerId()), GameError.INVALID_STATE);

        loseRound(t);                                                              // → ROUND_OVER
        assertError(() -> game.restartRound(t.token(1)), GameError.NOT_HOST);
        game.restartRound(t.host());                                               // → PLAYING (ronda 2)
        assertThat(game.getState(t.host()).party().round()).isEqualTo(2);

        game.backToLobby(t.host());                                                // → LOBBY
        StateView lobby = game.getState(t.token(1));
        assertThat(lobby.party().status()).isEqualTo(PartyStatus.LOBBY);
        assertThat(lobby.assignment()).isNull();
        game.joinParty(t.code(), "Tarde", "🦄");
        assertThat(game.getState(t.host()).players()).hasSize(4);
    }

    @Test
    void hostCanKickInLobbyAndLeavingHostHandsOverTheRole() {
        Table t = table(3);
        long second = t.players().get(1).playerId();

        assertError(() -> game.kick(t.token(1), t.players().get(2).playerId()), GameError.NOT_HOST);
        assertError(() -> game.kick(t.host(), t.players().getFirst().playerId()), GameError.CANNOT_KICK_SELF);

        game.kick(t.host(), t.players().get(2).playerId());
        assertError(() -> game.getState(t.token(2)), GameError.PLAYER_NOT_FOUND);
        assertThat(events.stream(GameEvent.PlayerLeft.class)).anyMatch(GameEvent.PlayerLeft::kicked);

        game.leave(t.host());
        StateView s = game.getState(t.token(1));
        assertThat(s.party().hostId()).isEqualTo(second);
        assertThat(s.me().host()).isTrue();

        game.leave(t.token(1));
        assertThat(parties.findByCode(t.code())).as("la party vacía se borra").isEmpty();
    }

    // ------------------------------------------------------------------ concurrencia

    @Test
    void twoSimultaneousConfirmationsProduceASingleLoser() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            Table t = table(4);
            game.startGame(t.host());
            OutgoingAccusationView a1 = game.accuse(t.token(0));
            OutgoingAccusationView a2 = game.accuse(t.token(1));
            UUID accused1 = tokenOf(t, a1.target().id());
            UUID accused2 = tokenOf(t, a2.target().id());

            List<Object> outcomes = runConcurrently(
                    () -> game.confirm(accused1, a1.id()),
                    () -> game.confirm(accused2, a2.id()));

            assertThat(outcomes).filteredOn(o -> o instanceof RoundResultView).hasSize(1);
            assertThat(outcomes).filteredOn(o -> o instanceof GameException ge && ge.error() == GameError.INVALID_STATE).hasSize(1);
            assertSingleLoser(t);
        }
    }

    @Test
    void doubleTapOnConfirmCountsOnlyOnce() throws Exception {
        Table t = table(3);
        game.startGame(t.host());
        OutgoingAccusationView acc = game.accuse(t.host());
        UUID accused = tokenOf(t, acc.target().id());

        List<Object> outcomes = runConcurrently(
                () -> game.confirm(accused, acc.id()),
                () -> game.confirm(accused, acc.id()));

        assertThat(outcomes).filteredOn(o -> o instanceof RoundResultView).hasSize(1);
        assertSingleLoser(t);
    }

    // ------------------------------------------------------------------ presencia y mantenimiento

    @Test
    void hostRoleMovesToNextConnectedPlayerAfter60SecondsDisconnected() {
        Table t = table(3);
        t.players().forEach(p -> game.playerConnected(p.playerToken()));
        game.playerDisconnected(t.host());

        clock.advance(Duration.ofSeconds(30));
        game.transferStaleHosts();
        assertThat(game.getState(t.host()).me().host()).as("aún dentro del margen").isTrue();

        clock.advance(Duration.ofSeconds(31));
        game.transferStaleHosts();
        assertThat(game.getState(t.host()).me().host()).isFalse();
        assertThat(game.getState(t.token(1)).me().host()).isTrue();
    }

    @Test
    void inactivePartiesAreDeletedAfterSixHours() {
        Table old = table(3);
        clock.advance(Duration.ofHours(6).plusMinutes(1));
        Table fresh = table(3);

        game.cleanupInactiveParties();

        assertThat(parties.findByCode(old.code())).isEmpty();
        assertThat(players.findByToken(old.host())).isEmpty();
        assertThat(parties.findByCode(fresh.code())).isPresent();
    }

    // ------------------------------------------------------------------ utilidades

    record Table(String code, List<JoinResult> players) {
        UUID host() { return players.getFirst().playerToken(); }
        UUID token(int i) { return players.get(i).playerToken(); }
    }

    private Table table(int n) {
        List<JoinResult> list = new ArrayList<>();
        JoinResult host = game.createParty("Host", Emojis.ALL.getFirst());
        list.add(host);
        for (int i = 1; i < n; i++) {
            list.add(game.joinParty(host.code(), "Jugador " + i, Emojis.ALL.get(i)));
        }
        return new Table(host.code(), list);
    }

    private UUID tokenOf(Table t, long playerId) {
        return t.players().stream().filter(p -> p.playerId() == playerId).findFirst().orElseThrow().playerToken();
    }

    /** El host acusa y su objetivo confirma: termina la ronda. */
    private void loseRound(Table t) {
        OutgoingAccusationView acc = game.accuse(t.host());
        game.confirm(tokenOf(t, acc.target().id()), acc.id());
    }

    private void assertSingleLoser(Table t) {
        StateView s = game.getState(t.host());
        assertThat(s.party().status()).isEqualTo(PartyStatus.ROUND_OVER);
        assertThat(s.players().stream().mapToInt(p -> p.shots()).sum()).isEqualTo(1);
        long confirmed = accusations.findAll().stream()
                .filter(a -> a.getStatus() == AccusationStatus.CONFIRMED)
                .filter(a -> parties.findById(a.getPartyId()).map(p -> p.getCode().equals(t.code())).orElse(false))
                .count();
        assertThat(confirmed).isEqualTo(1);
    }

    @SafeVarargs
    private static List<Object> runConcurrently(Callable<Object>... tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(tasks.length)) {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return task.call();
                    } catch (GameException e) {
                        return e;
                    }
                }));
            }
            ready.await();
            go.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> f : futures) {
                results.add(f.get());
            }
            return results;
        }
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, GameError expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GameException.class, e -> assertThat(e.error()).isEqualTo(expected));
    }
}
