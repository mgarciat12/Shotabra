package com.shotabra.game.view;

import java.util.UUID;

public record JoinResult(String code, UUID playerToken, long playerId) {
}
