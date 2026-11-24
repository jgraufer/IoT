CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username      VARCHAR(64)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(255),
    is_active     BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ
);

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash    ON refresh_tokens(token_hash);

CREATE TABLE edges (
    id        UUID PRIMARY KEY,
    name      VARCHAR(255),
    paired_at TIMESTAMPTZ,
    last_seen TIMESTAMPTZ,
    local_url VARCHAR(255)
);

CREATE TABLE edge_pairing_codes (
    code       VARCHAR(16) PRIMARY KEY,
    edge_id    UUID        NOT NULL REFERENCES edges(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used       BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_edge_access (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    edge_id    UUID        NOT NULL REFERENCES edges(id) ON DELETE CASCADE,
    role       VARCHAR(16) NOT NULL CHECK (role IN ('OWNER','ADMIN','MEMBER')),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by UUID REFERENCES users(id),
    UNIQUE (user_id, edge_id)
);
CREATE UNIQUE INDEX one_owner_per_edge ON user_edge_access(edge_id) WHERE role = 'OWNER';

CREATE TABLE edge_invitations (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    edge_id     UUID        NOT NULL REFERENCES edges(id) ON DELETE CASCADE,
    invited_by  UUID REFERENCES users(id),
    status      VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED','EXPIRED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES users(id)
);
CREATE INDEX idx_invitations_pending ON edge_invitations(edge_id, status) WHERE status = 'PENDING';

CREATE TABLE device (
    id             UUID PRIMARY KEY,
    edge_id        UUID         NOT NULL REFERENCES edges(id) ON DELETE CASCADE,
    name           VARCHAR(128) NOT NULL,
    type           VARCHAR(32)  NOT NULL CHECK (type IN ('LIGHT','THERMOSTAT','SENSOR','OUTLET','OTHER')),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    last_synced_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_device_edge_id ON device(edge_id);

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
    edge_id         UUID        NOT NULL REFERENCES edges(id) ON DELETE CASCADE,
    capability_type VARCHAR(32) NOT NULL CHECK (capability_type IN (
        'POWER_SWITCH','BRIGHTNESS','TEMPERATURE','HUMIDITY','MOTION_DETECTED','BATTERY_LEVEL'
    )),
    value           JSONB       NOT NULL,
    timestamp       TIMESTAMPTZ NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_telemetry_device_time ON telemetry(device_id, timestamp DESC);
CREATE INDEX idx_telemetry_edge_time   ON telemetry(edge_id, timestamp DESC);

CREATE TABLE cloud_outbox (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    edge_id         UUID        NOT NULL,
    message_type    VARCHAR(32) NOT NULL,
    payload         JSONB       NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ,
    attempts        INT         NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    last_error      TEXT
);
CREATE INDEX idx_outbox_pending ON cloud_outbox(created_at) WHERE sent_at IS NULL;
