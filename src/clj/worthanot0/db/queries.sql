-- name: create-user!
-- creates a new user record
INSERT INTO users 
(username, email, password)
VALUES (:username, :email, :password)

-- name: update-user!
-- update an existing user record
UPDATE users
SET username = :username, password = :password, date_created = :date_created, last_login = :last_login
WHERE username = :username

-- name: get-user
-- retrieve a user given the id.
SELECT * 
FROM users
WHERE username = :username

-- name: get-email
-- retrieve a email given the id.
SELECT email 
FROM users
WHERE email = :email

-- name: delete-user!
-- delete a user given the id
DELETE FROM users
WHERE username = :username



-- name: create-image!
-- creates a new image record
INSERT INTO images
(listing_id, image_id)
VALUES (:listing_id, :image_id)

-- name: get-listing-images
-- retrieve images given the listing id.
-- multiple images can belong to the same listing_id
SELECT image_id 
FROM images
WHERE listing_id = :listing_id

-- name: delete-images!
-- delete image given the listing_id
-- multiple images can belong to the same listing_id
DELETE FROM images
WHERE listing_id = :listing_id



-- name: create-listing!
-- creates a new listing record
INSERT INTO listings
(listing_id, user_id)
VALUES (:listing_id, :user_id)

-- name: get-user-listings
-- retrieve all listing ids given the user id
SELECT listing_id
FROM listings
WHERE user_id = :user_id

-- name: delete-listing!
-- delete a listing
-- in select * from listings where user_id = 'db85ff95-a202-47d5-afb7-e790274c46a7';
DELETE FROM listings
WHERE listing_id = :listing_id
