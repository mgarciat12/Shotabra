package com.shotabra.realtime;

/** Sobre común de todos los mensajes WebSocket: {@code {"type": "...", "data": {...}}}. */
public record WsMessage(String type, Object data) {
}
