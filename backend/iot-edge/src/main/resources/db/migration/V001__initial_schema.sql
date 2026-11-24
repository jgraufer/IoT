CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE edge_identity (
    id                      INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    edge_id                 UUID         NOT NULL,
    jwt_secret              VARCHAR(255) NOT NULL,
    pairing_code            VARCHAR(16),
    pairing_code_expires_at TIMESTAMPTZ,
    paired_at               TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE local_user (
    id             UUID PRIMARY KEY,
    username       VARCHAR(64)  NOT NULL UNIQUE,
    email          VARCHAR(255) NOT NULL UNIQUE,
    name           VARCHAR(255),
    password_hash  VARCHAR(255) NOT NULL,
    role           VARCHAR(16)  NOT NULL CHECK (role IN ('OWNER','ADMIN','MEMBER')),
    last_synced_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID        NOT NULL REFERENCES local_user(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_edge_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_edge_refresh_tokens_hash    ON refresh_tokens(token_hash);

CREATE TABLE device (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name       VARCHAR(128) NOT NULL,
    type       VARCHAR(32)  NOT NULL CHECK (type IN ('LIGHT','THERMOSTAT','SENSOR','OUTLET','OTHER')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE device_capability (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id       UUID        NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    capability_type VARCHAR(32) NOT NULL CHECK (capability_type IN (
        'POWER_SWITCH','BRIGHTNESS','TEMPERATURE','HUMIDITY','MOTION_DETECTED','BATTERY_LEVEL'
    )),
    CONSTRAINT uq_device_capability UNIQUE (device_id, capability_type)
);
CREATE INDEX idx_device_capability_device_id ON device_capability(device_id);

CREATE TABLE telemetry (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id       UUID        NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    capability_type VARCHAR(32) NOT NULL CHECK (capability_type IN (
        'POWER_SWITCH','BRIGHTNESS','TEMPERATURE','HUMIDITY','MOTION_DETECTED','BATTERY_LEVEL'
    )),
    value           JSONB       NOT NULL,
    timestamp       TIMESTAMPTZ NOT NULL,
    synced          BOOLEAN     NOT NULL DEFAULT false
);
CREATE INDEX idx_telemetry_device_time ON telemetry(device_id, timestamp DESC);
CREATE INDEX idx_telemetry_unsynced    ON telemetry(timestamp) WHERE synced = false;

CREATE TABLE device_command (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id       UUID        NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    capability_type VARCHAR(32) NOT NULL CHECK (capability_type IN (
        'POWER_SWITCH','BRIGHTNESS','TEMPERATURE','HUMIDITY','MOTION_DETECTED','BATTERY_LEVEL'
    )),
    value           JSONB       NOT NULL,
    issued_by       UUID        NOT NULL,
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_device_command_device_time ON device_command(device_id, issued_at DESC);

CREATE TABLE edge_outbox (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type            VARCHAR(32) NOT NULL,
    payload         JSONB       NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ,
    attempts        INT         NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    last_error      TEXT
);
CREATE INDEX idx_edge_outbox_pending ON edge_outbox(created_at) WHERE sent_at IS NULL;
