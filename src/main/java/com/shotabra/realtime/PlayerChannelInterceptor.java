package com.shotabra.realtime;

import java.util.Map;
import java.util.UUID;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * CONNECT: asocia la sesión STOMP al jugador (vale para WebSocket y para los transportes de respaldo de SockJS).
 * SUBSCRIBE: solo se permite la cola privada propia y el topic de la propia party.
 * SEND: no hay destinos de aplicación; las acciones van por REST.
 */
@Component
public class PlayerChannelInterceptor implements ChannelInterceptor {

    public static final String PRIVATE_QUEUE = "/user/queue/events";
    public static final String PARTY_TOPIC_PREFIX = "/topic/party/";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        Map<String, Object> attrs = accessor.getSessionAttributes();
        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT -> {
                Object token = attrs == null ? null : attrs.get(PlayerHandshakeInterceptor.ATTR_TOKEN);
                if (!(token instanceof UUID uuid)) {
                    throw new MessagingException("Jugador no identificado");
                }
                accessor.setUser(new PlayerPrincipal(uuid.toString()));
            }
            case SUBSCRIBE -> {
                String destination = accessor.getDestination();
                Object code = attrs == null ? null : attrs.get(PlayerHandshakeInterceptor.ATTR_PARTY_CODE);
                boolean allowed = PRIVATE_QUEUE.equals(destination)
                        || (code != null && (PARTY_TOPIC_PREFIX + code).equals(destination));
                if (!allowed) {
                    throw new MessagingException("Suscripción no permitida: " + destination);
                }
            }
            case SEND -> throw new MessagingException("Las acciones se envían por la API REST");
            default -> {
            }
        }
        return message;
    }
}
