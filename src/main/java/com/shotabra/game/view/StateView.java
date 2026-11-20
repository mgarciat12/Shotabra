package com.shotabra.game.view;

import java.util.List;

/**
 * Todo lo que un jugador necesita para pintar su pantalla tras (re)conectar.
 * Solo contiene SU asignación; las de los demás solo aparecen en {@code result} al acabar la ronda.
 */
public record StateView(
        PlayerView me,
        PartyView party,
        List<PlayerView> players,
        AssignmentView assignment,
        OutgoingAccusationView myAccusation,
        IncomingAccusationView incomingAccusation,
        RoundResultView result) {
}
