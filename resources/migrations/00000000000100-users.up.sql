CREATE TABLE users
(user_id     SERIAL      NOT NULL PRIMARY KEY,
 username    VARCHAR(30) NOT NULL,
 email       VARCHAR(60),
 password    VARCHAR(100),
 created_at TIMESTAMP   NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
 updated_at TIMESTAMP   NOT NULL DEFAULT (now() AT TIME ZONE 'utc'));
