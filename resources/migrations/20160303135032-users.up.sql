CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS users
(user_id     UUID         NOT NULL PRIMARY KEY DEFAULT uuid_generate_v4(),
 username    VARCHAR(30)  NOT NULL UNIQUE,
 email       VARCHAR(100) NOT NULL,
 password    VARCHAR(200) NOT NULL,
 created_at  TIMESTAMP    NOT NULL DEFAULT (now() AT TIME ZONE 'utc'));

--;;

CREATE TABLE IF NOT EXISTS images
(image_id          UUID         NOT NULL UNIQUE PRIMARY KEY,
 listing_id        UUID         NOT NULL,
 created_at        TIMESTAMP    NOT NULL DEFAULT (now() AT TIME ZONE 'utc'));

--;;

CREATE TABLE IF NOT EXISTS listings
(listing_id  UUID         NOT NULL PRIMARY KEY,
 user_id     UUID         NOT NULL,
 created_at  TIMESTAMP    NOT NULL DEFAULT (now() AT TIME ZONE 'utc'));
