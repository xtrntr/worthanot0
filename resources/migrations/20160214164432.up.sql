CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS users
(user_id     UUID         PRIMARY KEY uuid_generate_v4,
 username    VARCHAR(30)  NOT NULL UNIQUE,
 email       VARCHAR(100),
 password    VARCHAR(200),
 created_at  TIMESTAMP    NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
 updated_at  TIMESTAMP    NOT NULL DEFAULT (now() AT TIME ZONE 'utc'));

--;;

CREATE TABLE IF NOT EXISTS votes
(user_id     UUID,
 item_id     UUID,
 price       NUMERIC(10,2),
 created_at  TIMESTAMP   NOT NULL DEFAULT (now() AT TIME ZONE 'utc'));

--;;

CREATE TABLE IF NOT EXISTS images
(item_id     UUID         PRIMARY KEY uuid_generate_v4,
 listing_id  UUID,
 image_id    UUID,
 created_at  TIMESTAMP    NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
 updated_at  TIMESTAMP    NOT NULL DEFAULT (now() AT TIME ZONE 'utc'));

CREATE TABLE IF NOT EXISTS listings
(listing_id  UUID      PRIMARY KEY uuid_generate_v4,
 user_id     UUID);
