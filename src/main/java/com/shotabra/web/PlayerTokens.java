package com.shotabra.web;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.shotabra.game.GameError;
import com.shotabra.game.GameException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Identidad sin login: el {@code playerToken} viaja en una cookie HttpOnly y, como respaldo
 * (por si el navegador la pierde), en la cabecera {@code X-Player-Token} que el cliente guarda en localStorage.
 */
@Component
public class PlayerTokens {

    public static final String COOKIE = "shotabra_player";
    public static final String HEADER = "X-Player-Token";
    public static final String QUERY_PARAM = "token";
    private static final Duration MAX_AGE = Duration.ofDays(2);

    public Optional<UUID> resolve(HttpServletRequest request) {
        Optional<UUID> fromHeader = parse(request.getHeader(HEADER));
        if (fromHeader.isPresent()) {
            return fromHeader;
        }
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (COOKIE.equals(c.getName())) {
                    Optional<UUID> fromCookie = parse(c.getValue());
                    if (fromCookie.isPresent()) {
                        return fromCookie;
                    }
                }
            }
        }
        return parse(request.getParameter(QUERY_PARAM));
    }

    public UUID require(HttpServletRequest request) {
        return resolve(request).orElseThrow(() -> new GameException(GameError.PLAYER_NOT_FOUND));
    }

    public void write(HttpServletRequest request, HttpServletResponse response, UUID token) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(request, token.toString(), MAX_AGE).toString());
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(request, "", Duration.ZERO).toString());
    }

    private static ResponseCookie cookie(HttpServletRequest request, String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private static Optional<UUID> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.strip()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
