package com.shotabra.realtime;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.shotabra.domain.Player;
import com.shotabra.repository.PartyRepository;
import com.shotabra.repository.PlayerRepository;
import com.shotabra.web.PlayerTokens;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Solo acepta conexiones de jugadores existentes (token por cookie HttpOnly, cabecera o {@code ?token=}).
 * Guarda el token y el código de su party en los atributos de la sesión.
 */
@Component
public class PlayerHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_TOKEN = "playerToken";
    static final String ATTR_PARTY_CODE = "partyCode";

    private final PlayerTokens tokens;
    private final PlayerRepository players;
    private final PartyRepository parties;

    public PlayerHandshakeInterceptor(PlayerTokens tokens, PlayerRepository players, PartyRepository parties) {
        this.tokens = tokens;
        this.players = players;
        this.parties = parties;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servlet) {
            Optional<UUID> token = tokens.resolve(servlet.getServletRequest());
            Optional<Player> player = token.flatMap(players::findByToken);
            Optional<String> code = player.flatMap(p -> parties.findById(p.getPartyId())).map(p -> p.getCode());
            if (code.isPresent()) {
                attributes.put(ATTR_TOKEN, token.get());
                attributes.put(ATTR_PARTY_CODE, code.get());
                return true;
            }
        }
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
