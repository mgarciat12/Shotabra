package com.shotabra.game;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import com.shotabra.config.AppProperties;
import com.shotabra.domain.Accusation;
import com.shotabra.domain.AccusationStatus;
import com.shotabra.domain.Party;
import com.shotabra.domain.PartyStatus;
import com.shotabra.domain.Player;
import com.shotabra.domain.RoundAssignment;
import com.shotabra.domain.Word;
import com.shotabra.game.event.GameEvent;
import com.shotabra.game.view.AssignmentView;
import com.shotabra.game.view.IncomingAccusationView;
import com.shotabra.game.view.JoinResult;
import com.shotabra.game.view.OutgoingAccusationView;
import com.shotabra.game.view.PartyInfoView;
import com.shotabra.game.view.PartyView;
import com.shotabra.game.view.PlayerRef;
import com.shotabra.game.view.PlayerView;
import com.shotabra.game.view.RevealView;
import com.shotabra.game.view.RoundResultView;
import com.shotabra.game.view.StateView;
import com.shotabra.repository.AccusationRepository;
import com.shotabra.repository.PartyRepository;
import com.shotabra.repository.PlayerRepository;
import com.shotabra.repository.RoundAssignmentRepository;
import com.shotabra.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Toda la lógica del juego.
 *
 * <p><b>Concurrencia:</b> cada acción que cambia una party la carga primero con
 * {@code SELECT ... FOR UPDATE} ({@link PartyRepository#findByIdForUpdate}), así que las acciones
 * sobre una misma party se ejecutan de una en una y cada una ve el estado que dejó la anterior.
 * Los índices únicos de la BD (un solo CONFIRMED por ronda, un PENDING por acusador) son la
 * segunda red de seguridad.
 *
 * <p><b>Notificaciones:</b> se publican {@link GameEvent}s; {@code GameNotifier} los envía por
 * WebSocket solo tras el commit.
 */
@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final PartyRepository parties;
    private final PlayerRepository players;
    private final WordRepository words;
    private final RoundAssignmentRepository assignments;
    private final AccusationRepository accusations;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final RandomGenerator random;
    private final AppProperties props;

    public GameService(PartyRepository parties, PlayerRepository players, WordRepository words,
                       RoundAssignmentRepository assignments, AccusationRepository accusations,
                       ApplicationEventPublisher events, TransactionTemplate tx, Clock clock,
                       RandomGenerator random, AppProperties props) {
        this.parties = parties;
        this.players = players;
        this.words = words;
        this.assignments = assignments;
        this.accusations = accusations;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
        this.random = random;
        this.props = props;
    }

    // ------------------------------------------------------------------ crear / unirse / salir

    @Transactional
    public JoinResult createParty(String rawName, String emoji) {
        String name = PlayerNames.normalize(rawName);
        requireValidEmoji(emoji);
        Instant now = clock.instant();

        Party party = parties.save(new Party(newUniqueCode(), now));
        Player host = players.save(new Player(party.getId(), name, emoji, now));
        party.setHostPlayerId(host.getId());
        log.info("Party {} creada por {}", party.getCode(), name);
        return new JoinResult(party.getCode(), host.getToken(), host.getId());
    }

    @Transactional
    public JoinResult joinParty(String rawCode, String rawName, String emoji) {
        String name = PlayerNames.normalize(rawName);
        requireValidEmoji(emoji);
        Party party = lockParty(findPartyByCode(rawCode).getId());

        if (party.getStatus() != PartyStatus.LOBBY) {
            throw new GameException(GameError.GAME_IN_PROGRESS);
        }
        if (players.countByPartyId(party.getId()) >= Emojis.ALL.size()) {
            throw new GameException(GameError.PARTY_FULL);
        }
        if (players.existsByPartyIdAndNameIgnoreCase(party.getId(), name)) {
            throw new GameException(GameError.NAME_TAKEN);
        }
        if (players.existsByPartyIdAndEmoji(party.getId(), emoji)) {
            throw new GameException(GameError.EMOJI_TAKEN);
        }
        Instant now = clock.instant();
        Player player = players.save(new Player(party.getId(), name, emoji, now));
        party.touch(now);
        events.publishEvent(new GameEvent.PlayerJoined(party.getCode(), toView(player, party)));
        return new JoinResult(party.getCode(), player.getToken(), player.getId());
    }

    @Transactional(readOnly = true)
    public PartyInfoView partyInfo(String rawCode) {
        Party party = findPartyByCode(rawCode);
        long count = players.countByPartyId(party.getId());
        boolean joinable = party.getStatus() == PartyStatus.LOBBY && count < Emojis.ALL.size();
        return new PartyInfoView(party.getCode(), party.getStatus(), joinable, (int) count,
                players.findEmojisByPartyId(party.getId()));
    }

    /** Salir voluntariamente (solo en el lobby). Si sale el host, el rol pasa al siguiente. */
    @Transactional
    public void leave(UUID token) {
        Ctx c = lock(token);
        requireStatus(c.party, PartyStatus.LOBBY);
        removePlayer(c.party, c.me, false);
    }

    @Transactional
    public void kick(UUID token, long playerId) {
        Ctx c = lock(token);
        requireHost(c);
        requireStatus(c.party, PartyStatus.LOBBY);
        if (c.me.getId() == playerId) {
            throw new GameException(GameError.CANNOT_KICK_SELF);
        }
        Player victim = players.findById(playerId)
                .filter(p -> p.getPartyId().equals(c.party.getId()))
                .orElseThrow(() -> new GameException(GameError.PLAYER_NOT_FOUND));
        removePlayer(c.party, victim, true);
    }

    private void removePlayer(Party party, Player leaving, boolean kicked) {
        Instant now = clock.instant();
        List<Player> others = players.findByPartyIdOrderByJoinedAtAscIdAsc(party.getId()).stream()
                .filter(p -> !p.getId().equals(leaving.getId()))
                .toList();
        if (others.isEmpty()) {
            party.setHostPlayerId(null);
            players.delete(leaving);
            parties.delete(party);
            log.info("Party {} eliminada: se fue el último jugador", party.getCode());
            return;
        }
        boolean wasHost = party.isHost(leaving);
        if (wasHost) {
            // Primero se cambia el host y luego se borra el jugador (Hibernate hace UPDATE antes que DELETE).
            Player next = others.stream().filter(Player::isConnected).findFirst().orElse(others.getFirst());
            party.setHostPlayerId(next.getId());
        }
        party.touch(now);
        players.delete(leaving);
        events.publishEvent(new GameEvent.PlayerLeft(party.getCode(), leaving.getId(), leaving.getName(), kicked, leaving.getToken()));
        if (wasHost) {
            events.publishEvent(new GameEvent.HostChanged(party.getCode(), party.getHostPlayerId()));
        }
    }

    // ------------------------------------------------------------------ rondas

    @Transactional
    public void startGame(UUID token) {
        Ctx c = lock(token);
        requireHost(c);
        requireStatus(c.party, PartyStatus.LOBBY);
        dealNewRound(c.party);
    }

    @Transactional
    public void restartRound(UUID token) {
        Ctx c = lock(token);
        requireHost(c);
        requireStatus(c.party, PartyStatus.ROUND_OVER);
        dealNewRound(c.party);
    }

    /** El host vuelve al lobby (desde una ronda en juego o terminada) para que entre o salga gente. */
    @Transactional
    public void backToLobby(UUID token) {
        Ctx c = lock(token);
        requireHost(c);
        if (c.party.getStatus() == PartyStatus.LOBBY) {
            throw new GameException(GameError.INVALID_STATE);
        }
        Instant now = clock.instant();
        accusations.cancelPending(c.party.getId(), now);
        c.party.setStatus(PartyStatus.LOBBY);
        c.party.touch(now);
        events.publishEvent(new GameEvent.BackToLobby(c.party.getCode()));
    }

    private void dealNewRound(Party party) {
        List<Player> roster = players.findByPartyIdOrderByJoinedAtAscIdAsc(party.getId());
        if (roster.size() < props.minPlayers()) {
            throw new GameException(GameError.NOT_ENOUGH_PLAYERS);
        }
        int round = party.getRoundNumber() + 1;
        Map<Player, Player> targets = Derangements.assign(roster, random);
        List<Word> picked = WordPicker.pick(words.findByActiveTrue(), assignments.findUsedWordIds(party.getId()),
                roster.size(), random);

        Map<UUID, AssignmentView> privateCards = new LinkedHashMap<>();
        int i = 0;
        for (Map.Entry<Player, Player> e : targets.entrySet()) {
            Player hunter = e.getKey();
            Player target = e.getValue();
            Word word = picked.get(i++);
            assignments.save(new RoundAssignment(party.getId(), round, hunter.getId(), target.getId(), word.getId()));
            privateCards.put(hunter.getToken(), new AssignmentView(round, toRef(target), word.getText()));
        }
        party.setRoundNumber(round);
        party.setStatus(PartyStatus.PLAYING);
        party.touch(clock.instant());
        log.info("Party {}: ronda {} con {} jugadores", party.getCode(), round, roster.size());
        events.publishEvent(new GameEvent.RoundStarted(party.getCode(), round, privateCards));
    }

    // ------------------------------------------------------------------ acusaciones

    /** "¡La dijo!": acusa a mi objetivo de haber dicho mi palabra. */
    @Transactional
    public OutgoingAccusationView accuse(UUID token) {
        Ctx c = lock(token);
        requireStatus(c.party, PartyStatus.PLAYING);
        if (accusations.findFirstByAccuserIdAndStatus(c.me.getId(), AccusationStatus.PENDING).isPresent()) {
            throw new GameException(GameError.ACCUSATION_ALREADY_PENDING);
        }
        RoundAssignment card = assignments
                .findByPartyIdAndRoundNumberAndHunterPlayerId(c.party.getId(), c.party.getRoundNumber(), c.me.getId())
                .orElseThrow(() -> new GameException(GameError.INVALID_STATE));
        Player target = players.findById(card.getTargetPlayerId())
                .orElseThrow(() -> new GameException(GameError.PLAYER_NOT_FOUND));
        String word = wordText(card.getWordId());

        Instant now = clock.instant();
        Accusation acc = accusations.save(new Accusation(c.party.getId(), c.party.getRoundNumber(),
                c.me.getId(), target.getId(), card.getWordId(), now));
        c.party.touch(now);
        events.publishEvent(new GameEvent.AccusationCreated(c.party.getCode(), target.getToken(),
                new IncomingAccusationView(acc.getId(), toRef(c.me), word)));
        return new OutgoingAccusationView(acc.getId(), toRef(target), word, now);
    }

    /** El acusado reconoce que dijo la palabra: pierde la ronda. */
    @Transactional
    public RoundResultView confirm(UUID token, long accusationId) {
        Ctx c = lock(token);
        Accusation acc = accusationAgainstMe(c, accusationId);
        if (!acc.isPending() || c.party.getStatus() != PartyStatus.PLAYING
                || acc.getRoundNumber() != c.party.getRoundNumber()) {
            throw new GameException(GameError.INVALID_STATE);
        }
        Instant now = clock.instant();
        acc.resolve(AccusationStatus.CONFIRMED, now);
        c.me.addShot();
        c.party.setStatus(PartyStatus.ROUND_OVER);
        c.party.touch(now);
        accusations.cancelPending(c.party.getId(), now); // hace flush antes: la CONFIRMED ya no está PENDING

        RoundResultView result = buildResult(c.party);
        log.info("Party {}: {} pierde la ronda {}", c.party.getCode(), c.me.getName(), c.party.getRoundNumber());
        events.publishEvent(new GameEvent.RoundOver(c.party.getCode(), result));
        return result;
    }

    /** El acusado niega haber dicho la palabra: el juego sigue y el acusador puede volver a intentarlo. */
    @Transactional
    public void reject(UUID token, long accusationId) {
        Ctx c = lock(token);
        Accusation acc = accusationAgainstMe(c, accusationId);
        if (!acc.isPending()) {
            throw new GameException(GameError.INVALID_STATE);
        }
        Instant now = clock.instant();
        acc.resolve(AccusationStatus.REJECTED, now);
        c.party.touch(now);
        Player accuser = players.findById(acc.getAccuserId())
                .orElseThrow(() -> new GameException(GameError.PLAYER_NOT_FOUND));
        events.publishEvent(new GameEvent.AccusationRejected(c.party.getCode(), accuser.getToken(), acc.getId(), toRef(c.me)));
    }

    private Accusation accusationAgainstMe(Ctx c, long accusationId) {
        Accusation acc = accusations.findById(accusationId)
                .filter(a -> a.getPartyId().equals(c.party.getId()))
                .orElseThrow(() -> new GameException(GameError.ACCUSATION_NOT_FOUND));
        if (!acc.getAccusedId().equals(c.me.getId())) {
            throw new GameException(GameError.NOT_YOUR_ACCUSATION);
        }
        return acc;
    }

    // ------------------------------------------------------------------ estado (reconexión)

    @Transactional(readOnly = true)
    public StateView getState(UUID token) {
        Player me = players.findByToken(token).orElseThrow(() -> new GameException(GameError.PLAYER_NOT_FOUND));
        Party party = parties.findById(me.getPartyId()).orElseThrow(() -> new GameException(GameError.PARTY_NOT_FOUND));
        List<Player> roster = players.findByPartyIdOrderByJoinedAtAscIdAsc(party.getId());
        Map<Long, Player> byId = roster.stream().collect(Collectors.toMap(Player::getId, Function.identity()));

        AssignmentView assignment = null;
        OutgoingAccusationView mine = null;
        IncomingAccusationView incoming = null;
        RoundResultView result = null;

        if (party.getStatus() != PartyStatus.LOBBY) {
            assignment = assignments
                    .findByPartyIdAndRoundNumberAndHunterPlayerId(party.getId(), party.getRoundNumber(), me.getId())
                    .filter(a -> byId.containsKey(a.getTargetPlayerId()))
                    .map(a -> new AssignmentView(a.getRoundNumber(), toRef(byId.get(a.getTargetPlayerId())), wordText(a.getWordId())))
                    .orElse(null);
        }
        if (party.getStatus() == PartyStatus.PLAYING) {
            mine = accusations.findFirstByAccuserIdAndStatus(me.getId(), AccusationStatus.PENDING)
                    .filter(a -> byId.containsKey(a.getAccusedId()))
                    .map(a -> new OutgoingAccusationView(a.getId(), toRef(byId.get(a.getAccusedId())), wordText(a.getWordId()), a.getCreatedAt()))
                    .orElse(null);
            incoming = accusations.findFirstByAccusedIdAndStatus(me.getId(), AccusationStatus.PENDING)
                    .filter(a -> byId.containsKey(a.getAccuserId()))
                    .map(a -> new IncomingAccusationView(a.getId(), toRef(byId.get(a.getAccuserId())), wordText(a.getWordId())))
                    .orElse(null);
        }
        if (party.getStatus() == PartyStatus.ROUND_OVER) {
            result = buildResult(party);
        }
        return new StateView(toView(me, party), toPartyView(party),
                roster.stream().map(p -> toView(p, party)).toList(),
                assignment, mine, incoming, result);
    }

    private RoundResultView buildResult(Party party) {
        int round = party.getRoundNumber();
        Map<Long, Player> byId = players.findByPartyIdOrderByJoinedAtAscIdAsc(party.getId()).stream()
                .collect(Collectors.toMap(Player::getId, Function.identity()));
        List<RoundAssignment> cards = assignments.findByPartyIdAndRoundNumber(party.getId(), round);
        Map<Long, String> wordTexts = words.findAllById(cards.stream().map(RoundAssignment::getWordId).toList()).stream()
                .collect(Collectors.toMap(Word::getId, Word::getText));

        List<RevealView> reveals = new ArrayList<>();
        for (RoundAssignment a : cards) {
            Player hunter = byId.get(a.getHunterPlayerId());
            Player target = byId.get(a.getTargetPlayerId());
            if (hunter != null && target != null) {
                reveals.add(new RevealView(toRef(hunter), toRef(target), wordTexts.get(a.getWordId())));
            }
        }
        PlayerRef loser = null;
        PlayerRef catcher = null;
        String word = null;
        var confirmed = accusations.findFirstByPartyIdAndRoundNumberAndStatus(party.getId(), round, AccusationStatus.CONFIRMED);
        if (confirmed.isPresent()) {
            Accusation acc = confirmed.get();
            loser = byId.containsKey(acc.getAccusedId()) ? toRef(byId.get(acc.getAccusedId())) : null;
            catcher = byId.containsKey(acc.getAccuserId()) ? toRef(byId.get(acc.getAccuserId())) : null;
            word = wordText(acc.getWordId());
        }
        List<PlayerView> scoreboard = byId.values().stream()
                .sorted(Comparator.comparingInt(Player::getShots).reversed().thenComparing(Player::getJoinedAt))
                .map(p -> toView(p, party))
                .toList();
        return new RoundResultView(round, loser, catcher, word, reveals, scoreboard);
    }

    // ------------------------------------------------------------------ presencia y mantenimiento

    @Transactional
    public void playerConnected(UUID token) {
        if (players.markConnected(token) > 0) {
            publishPresence(token, true);
        }
    }

    @Transactional
    public void playerDisconnected(UUID token) {
        if (players.markDisconnected(token, clock.instant()) > 0) {
            publishPresence(token, false);
        }
    }

    private void publishPresence(UUID token, boolean connected) {
        players.findByToken(token).ifPresent(p -> parties.findById(p.getPartyId()).ifPresent(party ->
                events.publishEvent(new GameEvent.PresenceChanged(party.getCode(), p.getId(), connected))));
    }

    /** Al arrancar no hay ninguna sesión WebSocket viva: todos empiezan desconectados. */
    @Transactional
    public int resetPresence() {
        return players.markAllDisconnected(clock.instant());
    }

    /** Pasa el rol de host al siguiente jugador conectado si el host lleva demasiado tiempo desconectado. */
    public int transferStaleHosts() {
        Instant cutoff = clock.instant().minus(props.hostDisconnectTimeout());
        int changed = 0;
        for (Long partyId : players.findPartyIdsWithHostDisconnectedSince(cutoff)) {
            Boolean done = tx.execute(status -> transferHostIfStale(partyId, cutoff));
            if (Boolean.TRUE.equals(done)) {
                changed++;
            }
        }
        return changed;
    }

    private boolean transferHostIfStale(Long partyId, Instant cutoff) {
        Party party = parties.findByIdForUpdate(partyId).orElse(null);
        if (party == null || party.getHostPlayerId() == null) {
            return false;
        }
        Player host = players.findById(party.getHostPlayerId()).orElse(null);
        if (host == null || host.isConnected() || host.getDisconnectedAt() == null || !host.getDisconnectedAt().isBefore(cutoff)) {
            return false;
        }
        return players.findByPartyIdOrderByJoinedAtAscIdAsc(partyId).stream()
                .filter(p -> p.isConnected() && !p.getId().equals(host.getId()))
                .findFirst()
                .map(next -> {
                    party.setHostPlayerId(next.getId());
                    log.info("Party {}: {} desconectado, nuevo host {}", party.getCode(), host.getName(), next.getName());
                    events.publishEvent(new GameEvent.HostChanged(party.getCode(), next.getId()));
                    return true;
                })
                .orElse(false);
    }

    /** Borra las parties sin actividad durante más de {@code app.inactive-party-ttl}. */
    @Transactional
    public int cleanupInactiveParties() {
        List<Long> ids = parties.findIdsInactiveSince(clock.instant().minus(props.inactivePartyTtl()));
        if (ids.isEmpty()) {
            return 0;
        }
        parties.clearHosts(ids);
        int deleted = parties.deleteAllByIdIn(ids);
        log.info("Limpieza: {} parties inactivas eliminadas", deleted);
        return deleted;
    }

    // ------------------------------------------------------------------ utilidades

    private record Ctx(Party party, Player me) {
    }

    /** Bloquea la party del jugador y lo recarga después del bloqueo (por si lo expulsaron mientras tanto). */
    private Ctx lock(UUID token) {
        Long partyId = players.findPartyIdByToken(token).orElseThrow(() -> new GameException(GameError.PLAYER_NOT_FOUND));
        Party party = lockParty(partyId);
        Player me = players.findByToken(token).orElseThrow(() -> new GameException(GameError.PLAYER_NOT_FOUND));
        return new Ctx(party, me);
    }

    private Party lockParty(Long partyId) {
        return parties.findByIdForUpdate(partyId).orElseThrow(() -> new GameException(GameError.PARTY_NOT_FOUND));
    }

    private Party findPartyByCode(String rawCode) {
        String code = PartyCodeGenerator.normalize(rawCode);
        if (code == null) {
            throw new GameException(GameError.PARTY_NOT_FOUND);
        }
        return parties.findByCode(code).orElseThrow(() -> new GameException(GameError.PARTY_NOT_FOUND));
    }

    private String newUniqueCode() {
        for (int attempt = 0; attempt < 50; attempt++) {
            String code = PartyCodeGenerator.generate(random);
            if (!parties.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de party libre");
    }

    private static void requireValidEmoji(String emoji) {
        if (!Emojis.isValid(emoji)) {
            throw new GameException(GameError.INVALID_EMOJI);
        }
    }

    private static void requireHost(Ctx c) {
        if (!c.party.isHost(c.me)) {
            throw new GameException(GameError.NOT_HOST);
        }
    }

    private static void requireStatus(Party party, PartyStatus expected) {
        if (party.getStatus() != expected) {
            throw new GameException(GameError.INVALID_STATE);
        }
    }

    private String wordText(Long wordId) {
        return words.findById(wordId).map(Word::getText).orElse("?");
    }

    private PartyView toPartyView(Party party) {
        return new PartyView(party.getCode(), party.getStatus(), party.getRoundNumber(), party.getHostPlayerId(), props.minPlayers());
    }

    private static PlayerView toView(Player p, Party party) {
        return new PlayerView(p.getId(), p.getName(), p.getEmoji(), p.getShots(), p.isConnected(), party.isHost(p));
    }

    private static PlayerRef toRef(Player p) {
        return new PlayerRef(p.getId(), p.getName(), p.getEmoji());
    }
}
