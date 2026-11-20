package com.shotabra.realtime;

import com.shotabra.config.AppProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP sobre WebSocket (con SockJS de respaldo) en {@code /ws}.
 * <ul>
 *   <li>{@code /topic/party/{code}}: eventos públicos de la party.</li>
 *   <li>{@code /user/queue/events}: eventos privados del jugador (tarjeta, acusaciones).</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties props;
    private final PlayerHandshakeInterceptor handshakeInterceptor;
    private final PlayerChannelInterceptor channelInterceptor;

    public WebSocketConfig(AppProperties props, PlayerHandshakeInterceptor handshakeInterceptor,
                           PlayerChannelInterceptor channelInterceptor) {
        this.props = props;
        this.handshakeInterceptor = handshakeInterceptor;
        this.channelInterceptor = channelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(props.allowedOrigins().toArray(String[]::new))
                .addInterceptors(handshakeInterceptor)
                .withSockJS()
                // Sin CDN: el iframe de SockJS carga la librería desde nuestros WebJars
                .setClientLibraryUrl("/webjars/sockjs-client/dist/sockjs.min.js");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler heartbeat = new ThreadPoolTaskScheduler();
        heartbeat.setPoolSize(1);
        heartbeat.setThreadNamePrefix("ws-heartbeat-");
        heartbeat.initialize();

        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(heartbeat);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(channelInterceptor);
    }
}
