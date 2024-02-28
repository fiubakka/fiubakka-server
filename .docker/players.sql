CREATE TABLE IF NOT EXISTS akka.players (
    id BIGSERIAL PRIMARY KEY,
    username varchar(64) NOT NULL,
    password varchar(128) NOT NULL,
    CONSTRAINT unique_username_idx UNIQUE (username)
);
