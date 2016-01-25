-- name: create-user!
-- creates a new user record
INSERT INTO users 
(username, email, password)
VALUES (:username, :email, :password)

-- name: update-user!
-- update an existing user record
UPDATE users
SET username = :username, password = :password, date_created = :date_created, last_login = :last_login
WHERE id = :id

-- name: get-user
-- retrieve a user given the id.
SELECT * FROM users
WHERE id = :id

-- name: delete-user!
-- delete a user given the id
DELETE FROM users
WHERE id = :id
