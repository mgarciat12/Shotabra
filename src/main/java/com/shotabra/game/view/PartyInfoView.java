package com.shotabra.game.view;

import java.util.List;

import com.shotabra.domain.PartyStatus;

/** Información pública para la pantalla de "unirse". */
public record PartyInfoView(String code, PartyStatus status, boolean joinable, int playerCount, List<String> takenEmojis) {
}
