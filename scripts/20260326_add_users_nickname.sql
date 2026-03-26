ALTER TABLE users
    ADD COLUMN nickname VARCHAR(30) NOT NULL DEFAULT '' COMMENT 'display nickname' AFTER username;

UPDATE users
SET nickname = username
WHERE nickname = '' OR nickname IS NULL;
