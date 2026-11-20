package com.shotabra.game.view;

import java.util.List;

public record RoundResultView(
        int round,
        PlayerRef loser,
        PlayerRef catcher,
        String word,
        List<RevealView> assignments,
        List<PlayerView> scoreboard) {
}
