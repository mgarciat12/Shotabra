package com.shotabra.game.view;

public record PlayerView(long id, String name, String emoji, int shots, boolean connected, boolean host) {
}
