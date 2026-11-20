package com.shotabra.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import com.shotabra.IntegrationTest;
import com.shotabra.game.Emojis;
import com.shotabra.game.GameService;
import com.shotabra.game.view.JoinResult;
import com.shotabra.game.view.OutgoingAccusationView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/** Cliente STOMP real contra el servidor: privacidad de las tarjetas y eventos en tiempo real. */
class WebSocketIntegrationTest extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired GameService game;
    @Autowired SimpUserRegistry userRegistry;

    private final List<StompSession> sessions = new ArrayList<>();
    private final WebSocketStompClient stomp = new WebSocketStompClient(
            new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));

    @AfterEach
    void disconnect() {
        sessions.forEach(StompSession::disconnect);
    }

    @Test
    void eachPlayerReceivesOnlyTheirOwnCardAndPublicEventsCarryNoWords() throws Exception {
        JoinResult host = game.createParty("Host", Emojis.ALL.get(0));
        List<JoinResult> players = new ArrayList<>(List.of(host,
                game.joinParty(host.code(), "Bea", Emojis.ALL.get(1)),
                game.joinParty(host.code(), "Carlos", Emojis.ALL.get(2))));

        List<BlockingQueue<String>> privateQueues = new ArrayList<>();
        List<BlockingQueue<String>> topics = new ArrayList<>();
        for (JoinResult p : players) {
            StompSession s = connect(p.playerToken());
            privateQueues.add(subscribe(s, "/user/queue/events"));
            topics.add(subscribe(s, "/topic/party/" + host.code()));
            awaitSubscriptions(p.playerToken(), 2);
        }
        assertThat(game.getState(host.playerToken()).players()).allMatch(pl -> pl.connected());

        game.startGame(host.playerToken());

        List<String> words = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            String started = next(topics.get(i), "GAME_STARTED");
            assertThat(started).doesNotContain("word");

            String card = next(privateQueues.get(i), "ASSIGNMENT");
            String myWord = game.getState(players.get(i).playerToken()).assignment().word();
            assertThat((String) JsonPath.read(card, "$.data.word")).isEqualTo(myWord);
            assertThat(privateQueues.get(i)).as("una sola tarjeta por jugador").isEmpty();
            words.add(myWord);
        }
        assertThat(words).doesNotHaveDuplicates();

        // Acusación: el modal llega solo al acusado.
        OutgoingAccusationView acc = game.accuse(host.playerToken());
        int accusedIdx = indexOf(players, acc.target().id());
        String received = next(privateQueues.get(accusedIdx), "ACCUSATION_RECEIVED");
        assertThat((String) JsonPath.read(received, "$.data.accuser.name")).isEqualTo("Host");
        assertThat((String) JsonPath.read(received, "$.data.word")).isEqualTo(acc.word());

        game.reject(players.get(accusedIdx).playerToken(), acc.id());
        next(privateQueues.get(0), "ACCUSATION_REJECTED");

        OutgoingAccusationView again = game.accuse(host.playerToken());
        next(privateQueues.get(accusedIdx), "ACCUSATION_RECEIVED");
        game.confirm(players.get(accusedIdx).playerToken(), again.id());
        for (BlockingQueue<String> topic : topics) {
            String over = next(topic, "ROUND_OVER");
            assertThat((Integer) JsonPath.read(over, "$.data.loser.id")).isEqualTo((int) acc.target().id());
        }
    }

    @Test
    void connectionWithoutAValidTokenIsRefused() {
        assertThatThrownBy(() -> connect(UUID.randomUUID())).isNotNull();
    }

    // ------------------------------------------------------------------ utilidades

    private StompSession connect(UUID token) throws Exception {
        StompSession s = stomp.connectAsync("http://localhost:" + port + "/ws?token=" + token, new StompSessionHandlerAdapter() {
        }).get(5, TimeUnit.SECONDS);
        sessions.add(s);
        return s;
    }

    private static BlockingQueue<String> subscribe(StompSession s, String destination) {
        BlockingQueue<String> q = new LinkedBlockingQueue<>();
        s.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                q.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        return q;
    }

    /** Espera a que el servidor registre las suscripciones (evita carreras con el primer evento). */
    private void awaitSubscriptions(UUID token, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            var user = userRegistry.getUser(token.toString());
            int subs = user == null ? 0 : user.getSessions().stream().mapToInt(s -> s.getSubscriptions().size()).sum();
            if (subs >= expected) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Suscripciones no registradas para " + token);
    }

    /** Siguiente mensaje del tipo indicado (descarta los de otros tipos, p. ej. PRESENCE_CHANGED). */
    private static String next(BlockingQueue<String> q, String type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            String msg = q.poll(100, TimeUnit.MILLISECONDS);
            if (msg != null && type.equals(JsonPath.read(msg, "$.type"))) {
                return msg;
            }
        }
        throw new AssertionError("No llegó ningún " + type);
    }

    private static int indexOf(List<JoinResult> players, long id) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).playerId() == id) {
                return i;
            }
        }
        throw new IllegalArgumentException();
    }
}
