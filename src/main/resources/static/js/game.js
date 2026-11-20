/*
 * Shotabra — cliente (JavaScript vanilla).
 *
 * Módulos:
 *   Store     token del jugador en localStorage (respaldo de la cookie HttpOnly)
 *   Api       llamadas REST (las acciones del juego)
 *   Realtime  STOMP sobre SockJS con reconexión automática
 *   Events    reacción a los eventos WebSocket (vibración, avisos) + refresco de estado
 *   Ui        utilidades de interfaz (toasts, vibración, confeti, conexión, wake lock)
 *   Screens   pintado de cada pantalla a partir del StateView del servidor
 *   JoinForm  pantalla de crear/unirse
 *   Actions   botones con data-action
 *   App       arranque, estado actual y reconexión
 *
 * Regla de oro: el servidor es la fuente de verdad. Ante cualquier evento se vuelve a pedir
 * GET /api/me/state, así un móvil que se bloqueó o recargó recupera exactamente su pantalla.
 * Los datos de usuario se insertan SIEMPRE con textContent (nunca innerHTML).
 */
(() => {
    'use strict';

    const $ = (selector) => document.querySelector(selector);

    /** Crea un elemento: el('li', {className: 'x'}, 'texto', otroNodo). Los strings van como texto. */
    function el(tag, props = {}, ...children) {
        const node = document.createElement(tag);
        for (const [key, value] of Object.entries(props)) {
            if (key === 'dataset') Object.assign(node.dataset, value);
            else if (key in node) node[key] = value;
            else node.setAttribute(key, value);
        }
        for (const child of children.flat()) {
            if (child == null || child === false) continue;
            node.append(child instanceof Node ? child : document.createTextNode(String(child)));
        }
        return node;
    }

    // ================================================================== Store
    const Store = {
        TOKEN: 'shotabra.playerToken',
        NAME: 'shotabra.lastName',
        get(key = this.TOKEN) {
            try { return localStorage.getItem(key); } catch { return null; }
        },
        set(value, key = this.TOKEN) {
            try { localStorage.setItem(key, value); } catch { /* modo privado */ }
        },
        clear(key = this.TOKEN) {
            try { localStorage.removeItem(key); } catch { /* modo privado */ }
        },
    };

    // ================================================================== Api
    class ApiError extends Error {
        constructor(status, code, message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    const Api = {
        async request(method, path, body) {
            const headers = { Accept: 'application/json' };
            const token = Store.get();
            if (token) headers['X-Player-Token'] = token;
            if (body !== undefined) headers['Content-Type'] = 'application/json';
            let res;
            try {
                res = await fetch(path, {
                    method,
                    headers,
                    body: body === undefined ? undefined : JSON.stringify(body),
                    credentials: 'same-origin',
                    cache: 'no-store',
                });
            } catch {
                throw new ApiError(0, 'NETWORK', 'Sin conexión con el servidor.');
            }
            if (res.status === 204) return null;
            const data = await res.json().catch(() => null);
            if (!res.ok) {
                throw new ApiError(res.status, data?.code ?? 'ERROR', data?.message ?? 'Algo ha fallado. Inténtalo de nuevo.');
            }
            return data;
        },
        state: () => Api.request('GET', '/api/me/state'),
        partyInfo: (code) => Api.request('GET', `/api/parties/${encodeURIComponent(code)}`),
        create: (name, emoji) => Api.request('POST', '/api/parties', { name, emoji }),
        join: (code, name, emoji) => Api.request('POST', `/api/parties/${encodeURIComponent(code)}/players`, { name, emoji }),
        start: () => Api.request('POST', '/api/game/start'),
        restart: () => Api.request('POST', '/api/game/restart'),
        lobby: () => Api.request('POST', '/api/game/lobby'),
        leave: () => Api.request('POST', '/api/game/leave'),
        kick: (playerId) => Api.request('DELETE', `/api/game/players/${playerId}`),
        accuse: () => Api.request('POST', '/api/game/accusations'),
        confirm: (id) => Api.request('POST', `/api/game/accusations/${id}/confirm`),
        reject: (id) => Api.request('POST', `/api/game/accusations/${id}/reject`),
    };

    // ================================================================== Realtime
    const Realtime = {
        client: null,
        key: null,

        /** Conecta (o mantiene) la sesión STOMP de este jugador en esta party. */
        connect(partyCode, playerId) {
            const key = `${partyCode}:${playerId}`;
            if (this.client && this.key === key) {
                if (!this.client.active) this.client.activate();
                return;
            }
            this.disconnect();
            this.key = key;
            Ui.setConnection('connecting');

            const token = Store.get();
            const url = token ? `/ws?token=${encodeURIComponent(token)}` : '/ws';
            const client = new StompJs.Client({
                webSocketFactory: () => new SockJS(url),
                reconnectDelay: 2000,
                heartbeatIncoming: 10000,
                heartbeatOutgoing: 10000,
                debug: () => {},
            });
            client.onConnect = () => {
                Ui.setConnection('online');
                client.subscribe('/user/queue/events', (m) => Events.handle(JSON.parse(m.body)));
                client.subscribe(`/topic/party/${partyCode}`, (m) => Events.handle(JSON.parse(m.body)));
                // Ya suscritos: recuperamos todo lo que pasó mientras estábamos desconectados.
                App.refresh();
            };
            client.onWebSocketClose = () => Ui.setConnection(client.active ? 'connecting' : 'offline');
            client.onStompError = (frame) => console.warn('STOMP:', frame.headers?.message);
            client.activate();
            this.client = client;
        },

        disconnect() {
            if (this.client) this.client.deactivate();
            this.client = null;
            this.key = null;
            Ui.setConnection('offline');
        },
    };

    // ================================================================== Events
    const Events = {
        handle(message) {
            const { type, data } = message ?? {};
            const me = App.state?.me;
            switch (type) {
                case 'ACCUSATION_RECEIVED':
                    Ui.vibrate([200, 100, 200, 100, 400]);
                    break;
                case 'ACCUSATION_REJECTED':
                    Ui.vibrate(150);
                    Ui.toast(`${data.by.emoji} ${data.by.name} dice que no la dijo. ¡Sigue intentándolo!`, 'warning');
                    break;
                case 'KICKED':
                    App.lost('El host te ha sacado de la party.');
                    return;
                case 'PLAYER_JOINED':
                    if (data.id !== me?.id) Ui.toast(`${data.emoji} ${data.name} se ha unido`);
                    break;
                case 'PLAYER_LEFT':
                    if (data.playerId !== me?.id) Ui.toast(`${data.name} ${data.kicked ? 'ha sido expulsado' : 'se ha ido'}`);
                    break;
                case 'HOST_CHANGED':
                    if (data.hostId === me?.id) Ui.toast('👑 Ahora eres el host', 'success');
                    break;
                case 'ROUND_OVER':
                    if (data.loser?.id === me?.id) Ui.vibrate([400, 150, 400, 150, 600]);
                    break;
                default:
                    break;
            }
            App.scheduleRefresh();
        },
    };

    // ================================================================== Ui
    const Ui = {
        wakeLock: null,

        setConnection(status) {
            const dot = $('#conn-dot');
            dot.classList.toggle('online', status === 'online');
            dot.classList.toggle('connecting', status === 'connecting');
            dot.title = { online: 'Conectado', connecting: 'Conectando…', offline: 'Sin conexión' }[status];
        },

        toast(text, variant = 'secondary') {
            const toast = el('div', { className: `toast align-items-center text-bg-${variant} border-0`, role: 'status' },
                el('div', { className: 'd-flex' },
                    el('div', { className: 'toast-body fw-semibold' }, text),
                    el('button', { type: 'button', className: 'btn-close btn-close-white me-2 m-auto', 'aria-label': 'Cerrar', dataset: { bsDismiss: 'toast' } })));
            const container = $('#toasts');
            while (container.children.length >= 2) container.firstElementChild.remove();
            container.append(toast);
            toast.addEventListener('hidden.bs.toast', () => toast.remove());
            bootstrap.Toast.getOrCreateInstance(toast, { delay: 3500 }).show();
        },

        vibrate(pattern) {
            try { navigator.vibrate?.(pattern); } catch { /* no soportado */ }
        },

        confetti() {
            const layer = $('#confetti');
            const pieces = ['🎉', '🥃', '✨', '🍻', '🎊', '🥳'];
            for (let i = 0; i < 40; i++) {
                const piece = el('span', {}, pieces[i % pieces.length]);
                piece.style.left = `${Math.random() * 100}%`;
                piece.style.animationDuration = `${2 + Math.random() * 2}s`;
                piece.style.animationDelay = `${Math.random() * 0.8}s`;
                layer.append(piece);
            }
            setTimeout(() => layer.replaceChildren(), 5500);
        },

        /** Evita que la pantalla se apague durante la partida (si el navegador lo permite). */
        async keepAwake(enabled) {
            try {
                if (enabled && !this.wakeLock && document.visibilityState === 'visible' && 'wakeLock' in navigator) {
                    this.wakeLock = await navigator.wakeLock.request('screen');
                    this.wakeLock.addEventListener('release', () => { this.wakeLock = null; });
                } else if (!enabled && this.wakeLock) {
                    await this.wakeLock.release();
                    this.wakeLock = null;
                }
            } catch { /* sin permiso o no soportado */ }
        },

        playerLine(p, { me, hostView, kickable }) {
            return el('li', {},
                el('span', { className: 'player-emoji' }, p.emoji),
                el('span', { className: 'player-name' }, p.name),
                p.id === me.id ? el('span', { className: 'badge text-bg-light me-badge' }, 'tú') : null,
                p.host ? el('i', { className: 'bi bi-star-fill host-badge', title: 'Host' }) : null,
                el('span', { className: `player-status ${p.connected ? 'online' : ''}`, title: p.connected ? 'Conectado' : 'Desconectado' }),
                hostView && kickable && p.id !== me.id
                    ? el('button', {
                        type: 'button', className: 'btn btn-sm btn-outline-danger', title: `Expulsar a ${p.name}`,
                        dataset: { action: 'kick', playerId: String(p.id), playerName: p.name },
                    }, el('i', { className: 'bi bi-x-lg' }))
                    : null);
        },
    };

    // ================================================================== Screens
    const Screens = {
        current: null,
        cardHidden: false,
        celebratedRound: null,
        modal: null,
        modalAccusationId: null,

        show(id) {
            document.querySelectorAll('.screen').forEach((s) => s.classList.toggle('d-none', s.id !== id));
            if (this.current !== id) window.scrollTo(0, 0);
            this.current = id;
        },

        home() {
            $('#topbar-code').classList.add('d-none');
            $('#conn-dot').classList.add('d-none');
            this.syncModal(null);
            Ui.keepAwake(false);
            this.show('screen-home');
        },

        render(state) {
            const code = $('#topbar-code');
            code.textContent = state.party.code;
            code.classList.remove('d-none');
            $('#conn-dot').classList.remove('d-none');
            switch (state.party.status) {
                case 'LOBBY': this.lobby(state); break;
                case 'PLAYING': this.game(state); break;
                case 'ROUND_OVER': this.result(state); break;
                default: break;
            }
            this.syncModal(state.incomingAccusation);
            Ui.keepAwake(true);
        },

        lobby({ me, party, players }) {
            const isHost = me.host;
            $('#lobby-code').textContent = party.code;
            const qr = $('#lobby-qr');
            const qrSrc = `/api/parties/${party.code}/qr.svg`;
            if (qr.getAttribute('src') !== qrSrc) qr.setAttribute('src', qrSrc);
            $('#lobby-count').textContent = String(players.length);
            $('#lobby-players').replaceChildren(...players.map((p) => Ui.playerLine(p, { me, hostView: isHost, kickable: true })));

            const enough = players.length >= party.minPlayers;
            const start = $('#btn-start');
            start.classList.toggle('d-none', !isHost);
            start.disabled = !enough;
            const missing = party.minPlayers - players.length;
            $('#lobby-hint').textContent = isHost
                ? (enough ? '¡Todo listo! Cuando quieras.' : `Faltan ${missing} jugador${missing === 1 ? '' : 'es'} (mínimo ${party.minPlayers}).`)
                : 'Esperando a que el host inicie el juego…';
            this.show('screen-lobby');
        },

        game({ me, party, players, assignment, myAccusation }) {
            $('#game-round').textContent = String(party.round);
            $('#card-emoji').textContent = assignment?.target.emoji ?? '❓';
            $('#card-target').textContent = assignment?.target.name ?? '…';
            $('#card-word').textContent = assignment?.word ?? '…';
            this.applyCardVisibility();

            const pending = Boolean(myAccusation);
            $('#pending-box').classList.toggle('d-none', !pending);
            $('#pending-target').textContent = myAccusation ? `${myAccusation.target.emoji} ${myAccusation.target.name}` : '';
            $('#btn-accuse').disabled = pending || !assignment;
            $('#btn-game-lobby').classList.toggle('d-none', !me.host);

            $('#game-players').replaceChildren(...players.map((p) => el('li', {},
                el('span', {}, p.emoji),
                el('span', {}, p.name),
                el('span', { className: `player-status ${p.connected ? 'online' : ''}` }))));
            this.show('screen-game');
        },

        result({ me, party, result }) {
            if (!result) return;
            $('#result-loser-emoji').textContent = result.loser?.emoji ?? '🤷';
            $('#result-loser').textContent = result.loser?.name ?? 'Alguien';
            $('#result-catcher').textContent = result.catcher ? `${result.catcher.emoji} ${result.catcher.name}` : 'Alguien';
            $('#result-word').textContent = result.word ?? '';

            $('#result-reveals').replaceChildren(...result.assignments.map((a) => el('li', {
                    className: result.loser && a.target.id === result.loser.id ? 'loser-row' : '',
                },
                el('span', {}, `${a.hunter.emoji} ${a.hunter.name}`),
                el('span', { className: 'arrow' }, '→'),
                el('span', {}, `${a.target.emoji} ${a.target.name}`),
                el('span', { className: 'word-pill' }, a.word))));

            $('#result-scoreboard').replaceChildren(...result.scoreboard.map((p) => el('li', {},
                el('span', { className: 'player-emoji' }, p.emoji),
                el('span', { className: 'player-name' }, p.name),
                el('span', { className: 'shots' }, `${p.shots} 🥃`))));

            $('#btn-restart').classList.toggle('d-none', !me.host);
            $('#btn-result-lobby').classList.toggle('d-none', !me.host);
            $('#result-wait').classList.toggle('d-none', me.host);

            const firstTime = this.celebratedRound !== `${party.code}:${party.round}`;
            this.show('screen-result');
            if (firstTime) {
                this.celebratedRound = `${party.code}:${party.round}`;
                const stage = $('#shot-stage');
                stage.classList.remove('play');
                void stage.offsetWidth; // reinicia la animación
                stage.classList.add('play');
                Ui.confetti();
            }
        },

        toggleCard() {
            this.cardHidden = !this.cardHidden;
            this.applyCardVisibility();
        },

        applyCardVisibility() {
            const card = $('#secret-card');
            card.classList.toggle('is-hidden', this.cardHidden);
            card.setAttribute('aria-pressed', String(this.cardHidden));
            const btn = $('#btn-toggle-card');
            btn.replaceChildren(el('i', { className: `bi ${this.cardHidden ? 'bi-eye' : 'bi-eye-slash'}` }),
                this.cardHidden ? ' Mostrar tarjeta' : ' Ocultar tarjeta');
        },

        /** Muestra el modal de confirmación si hay una acusación contra mí (también tras reconectar). */
        syncModal(incoming) {
            this.modal ??= bootstrap.Modal.getOrCreateInstance($('#accusation-modal'));
            if (incoming) {
                $('#acc-accuser').textContent = `${incoming.accuser.emoji} ${incoming.accuser.name}`;
                $('#acc-word').textContent = incoming.word;
                if (this.modalAccusationId !== incoming.id) {
                    this.modalAccusationId = incoming.id;
                    Ui.vibrate([200, 100, 200]);
                    this.modal.show();
                }
            } else if (this.modalAccusationId !== null) {
                this.modalAccusationId = null;
                this.modal.hide();
            }
        },
    };

    // ================================================================== JoinForm
    const JoinForm = {
        mode: 'join',
        emoji: null,
        lookupSeq: 0,

        open(mode, code = '') {
            this.mode = mode;
            $('#join-title').textContent = mode === 'create' ? 'Crea tu party' : 'Únete a la party';
            $('#join-submit').textContent = mode === 'create' ? 'Crear party' : 'Entrar';
            $('#join-code-group').classList.toggle('d-none', mode === 'create');
            $('#join-code').value = code;
            $('#join-name').value = $('#join-name').value || Store.get(Store.NAME) || '';
            this.setError(null);
            this.markTaken([]);
            $('#topbar-code').classList.add('d-none');
            $('#conn-dot').classList.add('d-none');
            Screens.syncModal(null);
            Screens.show('screen-join');
            if (mode === 'join' && code.length === 4) this.lookup(code);
            (mode === 'join' && code.length !== 4 ? $('#join-code') : $('#join-name')).focus({ preventScroll: true });
        },

        selectEmoji(button) {
            if (button.disabled) return;
            document.querySelectorAll('.emoji-btn').forEach((b) => b.setAttribute('aria-checked', String(b === button)));
            this.emoji = button.dataset.emoji;
        },

        markTaken(taken) {
            document.querySelectorAll('.emoji-btn').forEach((b) => {
                const isTaken = taken.includes(b.dataset.emoji);
                b.disabled = isTaken;
                if (isTaken && b.dataset.emoji === this.emoji) {
                    b.setAttribute('aria-checked', 'false');
                    this.emoji = null;
                }
            });
        },

        onCodeInput(input) {
            const clean = input.value.toUpperCase().replace(/[^A-Z]/g, '').slice(0, 4);
            if (clean !== input.value) input.value = clean;
            this.setError(null);
            if (clean.length === 4) this.lookup(clean);
            else {
                this.markTaken([]);
                $('#join-code-help').textContent = '4 letras. Lo ves en la pantalla del host.';
            }
        },

        async lookup(code) {
            const seq = ++this.lookupSeq;
            try {
                const info = await Api.partyInfo(code);
                if (seq !== this.lookupSeq) return;
                this.markTaken(info.takenEmojis);
                $('#join-code-help').textContent = info.joinable
                    ? `¡Party encontrada! ${info.playerCount} jugador${info.playerCount === 1 ? '' : 'es'} dentro.`
                    : 'La partida ya ha empezado. Pide al host que vuelva al lobby.';
            } catch (e) {
                if (seq !== this.lookupSeq) return;
                $('#join-code-help').textContent = e.status === 404 ? 'No existe ninguna party con ese código.' : e.message;
            }
        },

        setError(message) {
            const box = $('#join-error');
            box.textContent = message ?? '';
            box.classList.toggle('d-none', !message);
        },

        async submit(event) {
            event.preventDefault();
            const code = $('#join-code').value.trim().toUpperCase();
            const name = $('#join-name').value.trim();
            if (this.mode === 'join' && code.length !== 4) return this.setError('Escribe el código de 4 letras.');
            if (!name) return this.setError('Escribe tu nombre.');
            if (!this.emoji) return this.setError('Elige un emoji.');

            const button = $('#join-submit');
            button.disabled = true;
            try {
                const result = this.mode === 'create' ? await Api.create(name, this.emoji) : await Api.join(code, name, this.emoji);
                Store.set(result.playerToken);
                Store.set(name, Store.NAME);
                if (location.pathname !== '/') history.replaceState(null, '', '/');
                await App.refresh();
            } catch (e) {
                this.setError(e.message);
                if (e.code === 'EMOJI_TAKEN' && this.mode === 'join') this.lookup(code);
            } finally {
                button.disabled = false;
            }
        },
    };

    // ================================================================== Actions
    const Actions = {
        /** Ejecuta una acción con el botón deshabilitado (evita dobles toques) y pinta el estado resultante. */
        async run(button, call) {
            if (button) button.disabled = true;
            try {
                const state = await call();
                if (state) App.apply(state);
            } catch (e) {
                if (e.status === 401) App.lost('Ya no estás en esa party.');
                else Ui.toast(e.message, 'danger');
                App.scheduleRefresh();
            } finally {
                if (button) button.disabled = false;
                if (App.state) Screens.render(App.state); // vuelve a aplicar los disabled que dependen del estado
            }
        },

        async handle(action, button) {
            switch (action) {
                case 'home':
                    if (App.state) Screens.render(App.state);
                    else Screens.home();
                    break;
                case 'show-create': JoinForm.open('create'); break;
                case 'show-join': JoinForm.open('join'); break;
                case 'start': await this.run(button, Api.start); break;
                case 'restart': await this.run(button, Api.restart); break;
                case 'lobby': {
                    const playing = App.state?.party.status === 'PLAYING';
                    if (playing && !window.confirm('¿Terminar la ronda sin perdedor y volver al lobby?')) return;
                    await this.run(button, Api.lobby);
                    break;
                }
                case 'kick':
                    if (!window.confirm(`¿Expulsar a ${button.dataset.playerName}?`)) return;
                    await this.run(button, () => Api.kick(button.dataset.playerId));
                    break;
                case 'leave':
                    if (!window.confirm('¿Seguro que quieres salir de la party?')) return;
                    try {
                        await Api.leave();
                        App.lost(null);
                    } catch (e) {
                        Ui.toast(e.message, 'danger');
                    }
                    break;
                case 'accuse':
                    Ui.vibrate(50);
                    await this.run(button, Api.accuse);
                    break;
                case 'confirm':
                case 'reject': {
                    const id = Screens.modalAccusationId;
                    if (id == null) return;
                    const buttons = document.querySelectorAll('#accusation-modal button');
                    buttons.forEach((b) => { b.disabled = true; });
                    try {
                        await this.run(null, () => (action === 'confirm' ? Api.confirm(id) : Api.reject(id)));
                    } finally {
                        buttons.forEach((b) => { b.disabled = false; });
                    }
                    break;
                }
                case 'toggle-card': Screens.toggleCard(); break;
                case 'share': await this.share(); break;
                default: break;
            }
        },

        async share() {
            const code = App.state?.party.code;
            if (!code) return;
            const url = `${location.origin}/j/${code}`;
            try {
                if (navigator.share) {
                    await navigator.share({ title: 'Shotabra', text: `¡Únete a mi party! Código ${code}`, url });
                } else {
                    await navigator.clipboard.writeText(url);
                    Ui.toast('Enlace copiado 📋', 'success');
                }
            } catch { /* cancelado */ }
        },
    };

    // ================================================================== App
    const App = {
        state: null,
        refreshTimer: null,

        async init() {
            this.bind();
            const joinCode = document.body.dataset.joinCode || null;
            try {
                const state = await Api.state();
                if (joinCode && state.party.code !== joinCode) {
                    JoinForm.open('join', joinCode); // abrió el QR de otra party
                } else {
                    if (joinCode) history.replaceState(null, '', '/');
                    this.apply(state);
                }
            } catch (e) {
                if (e.status === 0) Ui.toast(e.message, 'danger');
                Store.clear();
                if (joinCode) JoinForm.open('join', joinCode);
                else Screens.home();
            }
        },

        apply(state) {
            this.state = state;
            Realtime.connect(state.party.code, state.me.id);
            Screens.render(state);
        },

        async refresh() {
            try {
                this.apply(await Api.state());
            } catch (e) {
                if (e.status === 401 || e.status === 404) this.lost('La party ya no existe.');
                // Error de red: el indicador de conexión ya lo refleja; reintentará al reconectar.
            }
        },

        scheduleRefresh() {
            clearTimeout(this.refreshTimer);
            this.refreshTimer = setTimeout(() => this.refresh(), 120);
        },

        /** El jugador ya no pertenece a ninguna party (expulsado, salió o se borró por inactividad). */
        lost(message) {
            Store.clear();
            Realtime.disconnect();
            this.state = null;
            Screens.home();
            if (message) Ui.toast(message, 'danger');
        },

        bind() {
            document.addEventListener('click', (event) => {
                const target = event.target.closest('[data-action]');
                if (target) Actions.handle(target.dataset.action, target);
                const emoji = event.target.closest('.emoji-btn');
                if (emoji) JoinForm.selectEmoji(emoji);
            });
            $('#join-form').addEventListener('submit', (e) => JoinForm.submit(e));
            $('#join-code').addEventListener('input', (e) => JoinForm.onCodeInput(e.target));

            // Al volver de un móvil bloqueado o de otra app: reconectar y recuperar el estado.
            document.addEventListener('visibilitychange', () => {
                if (document.visibilityState !== 'visible' || !this.state) return;
                Realtime.client?.active || Realtime.client?.activate();
                this.refresh();
                Ui.keepAwake(true);
            });
            window.addEventListener('online', () => this.state && this.refresh());
            window.addEventListener('pageshow', (e) => e.persisted && this.state && this.refresh());
        },
    };

    document.addEventListener('DOMContentLoaded', () => App.init());
})();
