package com.shotabra.realtime;

import java.security.Principal;

/** Principal STOMP: su nombre es el playerToken, así {@code /user/queue/events} llega solo a ese jugador. */
public record PlayerPrincipal(String token) implements Principal {

    @Override
    public String getName() {
        return token;
    }
}
