package com.shotabra.game.view;

import com.shotabra.domain.PartyStatus;

public record PartyView(String code, PartyStatus status, int round, Long hostId, int minPlayers) {
}
