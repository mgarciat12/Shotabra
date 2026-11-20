-- Esquema inicial de Shotabra

CREATE TABLE word (
    id       BIGSERIAL PRIMARY KEY,
    text     VARCHAR(40) NOT NULL,
    category VARCHAR(30) NOT NULL,
    active   BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX ux_word_text ON word (lower(text));

CREATE TABLE party (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(4)  NOT NULL,
    status           VARCHAR(16) NOT NULL CHECK (status IN ('LOBBY', 'PLAYING', 'ROUND_OVER')),
    host_player_id   BIGINT,
    round_number     INTEGER     NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ NOT NULL,
    version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ux_party_code UNIQUE (code)
);
CREATE INDEX ix_party_last_activity ON party (last_activity_at);

CREATE TABLE player (
    id              BIGSERIAL PRIMARY KEY,
    party_id        BIGINT      NOT NULL REFERENCES party (id) ON DELETE CASCADE,
    token           UUID        NOT NULL,
    name            VARCHAR(16) NOT NULL,
    emoji           VARCHAR(16) NOT NULL,
    shots           INTEGER     NOT NULL DEFAULT 0,
    connected       BOOLEAN     NOT NULL DEFAULT FALSE,
    disconnected_at TIMESTAMPTZ,
    joined_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_player_token UNIQUE (token)
);
CREATE INDEX ix_player_party ON player (party_id);
-- Nombre único (sin distinguir mayúsculas) y emoji único dentro de cada party
CREATE UNIQUE INDEX ux_player_party_name ON player (party_id, lower(name));
CREATE UNIQUE INDEX ux_player_party_emoji ON player (party_id, emoji);

ALTER TABLE party
    ADD CONSTRAINT fk_party_host FOREIGN KEY (host_player_id) REFERENCES player (id) ON DELETE SET NULL;

CREATE TABLE round_assignment (
    id               BIGSERIAL PRIMARY KEY,
    party_id         BIGINT  NOT NULL REFERENCES party (id) ON DELETE CASCADE,
    round_number     INTEGER NOT NULL,
    hunter_player_id BIGINT  NOT NULL REFERENCES player (id) ON DELETE CASCADE,
    target_player_id BIGINT  NOT NULL REFERENCES player (id) ON DELETE CASCADE,
    word_id          BIGINT  NOT NULL REFERENCES word (id),
    CONSTRAINT ck_assignment_not_self CHECK (hunter_player_id <> target_player_id),
    CONSTRAINT ux_assignment_hunter UNIQUE (party_id, round_number, hunter_player_id),
    CONSTRAINT ux_assignment_target UNIQUE (party_id, round_number, target_player_id),
    CONSTRAINT ux_assignment_word UNIQUE (party_id, round_number, word_id)
);

CREATE TABLE accusation (
    id           BIGSERIAL PRIMARY KEY,
    party_id     BIGINT      NOT NULL REFERENCES party (id) ON DELETE CASCADE,
    round_number INTEGER     NOT NULL,
    accuser_id   BIGINT      NOT NULL REFERENCES player (id) ON DELETE CASCADE,
    accused_id   BIGINT      NOT NULL REFERENCES player (id) ON DELETE CASCADE,
    word_id      BIGINT      NOT NULL REFERENCES word (id),
    status       VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
    created_at   TIMESTAMPTZ NOT NULL,
    resolved_at  TIMESTAMPTZ
);
CREATE INDEX ix_accusation_party_round ON accusation (party_id, round_number);
-- Red de seguridad a nivel de BD: un solo perdedor por ronda...
CREATE UNIQUE INDEX ux_accusation_one_loser ON accusation (party_id, round_number) WHERE status = 'CONFIRMED';
-- ...y como mucho una acusación pendiente por acusador.
CREATE UNIQUE INDEX ux_accusation_one_pending ON accusation (accuser_id) WHERE status = 'PENDING';
