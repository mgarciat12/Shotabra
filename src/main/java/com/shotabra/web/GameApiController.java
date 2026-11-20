package com.shotabra.web;

import java.util.List;
import java.util.UUID;

import com.shotabra.game.Emojis;
import com.shotabra.game.GameService;
import com.shotabra.game.view.JoinResult;
import com.shotabra.game.view.PartyInfoView;
import com.shotabra.game.view.StateView;
import com.shotabra.web.dto.JoinRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST: las acciones van por aquí (respuesta y errores inmediatos) y los cambios
 * se difunden por WebSocket. Las acciones devuelven el estado actualizado del jugador.
 */
@RestController
@RequestMapping("/api")
public class GameApiController {

    private final GameService game;
    private final PlayerTokens tokens;

    public GameApiController(GameService game, PlayerTokens tokens) {
        this.game = game;
        this.tokens = tokens;
    }

    @GetMapping("/emojis")
    public List<String> emojis() {
        return Emojis.ALL;
    }

    @GetMapping("/parties/{code}")
    public PartyInfoView partyInfo(@PathVariable String code) {
        return game.partyInfo(code);
    }

    @PostMapping("/parties")
    @ResponseStatus(HttpStatus.CREATED)
    public JoinResult create(@Valid @RequestBody JoinRequest body, HttpServletRequest req, HttpServletResponse res) {
        JoinResult result = game.createParty(body.name(), body.emoji());
        tokens.write(req, res, result.playerToken());
        return result;
    }

    @PostMapping("/parties/{code}/players")
    @ResponseStatus(HttpStatus.CREATED)
    public JoinResult join(@PathVariable String code, @Valid @RequestBody JoinRequest body,
                           HttpServletRequest req, HttpServletResponse res) {
        JoinResult result = game.joinParty(code, body.name(), body.emoji());
        tokens.write(req, res, result.playerToken());
        return result;
    }

    /** Estado completo para (re)conectar: lobby, tarjeta propia, modal pendiente o resultado. */
    @GetMapping("/me/state")
    public StateView state(HttpServletRequest req) {
        return game.getState(tokens.require(req));
    }

    @PostMapping("/game/start")
    public StateView start(HttpServletRequest req) {
        UUID token = tokens.require(req);
        game.startGame(token);
        return game.getState(token);
    }

    @PostMapping("/game/restart")
    public StateView restart(HttpServletRequest req) {
        UUID token = tokens.require(req);
        game.restartRound(token);
        return game.getState(token);
    }

    @PostMapping("/game/lobby")
    public StateView backToLobby(HttpServletRequest req) {
        UUID token = tokens.require(req);
        game.backToLobby(token);
        return game.getState(token);
    }

    @DeleteMapping("/game/players/{playerId}")
    public StateView kick(@PathVariable long playerId, HttpServletRequest req) {
        UUID token = tokens.require(req);
        game.kick(token, playerId);
        return game.getState(token);
    }

    @PostMapping("/game/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(HttpServletRequest req, HttpServletResponse res) {
        game.leave(tokens.require(req));
        tokens.clear(req, res);
    }

    @PostMapping("/game/accusations")
    public StateView accuse(HttpServletRequest req) {
        UUID token = tokens.require(req);
        game.accuse(token);
        return game.getState(token);
    }

    @PostMapping("/game/accusations/{id}/confirm")
    public StateView confirm(@PathVariable long id, HttpServletRequest req) {
        UUID token = tokens.require(req);
        game.confirm(token, id);
        return game.getState(token);
    }

    @PostMapping("/game/accusations/{id}/reject")
    public StateView reject(@PathVariable long id, HttpServletRequest req) {
        UUID token = tokens.require(req);
        game.reject(token, id);
        return game.getState(token);
    }
}
