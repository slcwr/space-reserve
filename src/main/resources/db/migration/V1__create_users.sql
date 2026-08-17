-- V1__create_users.sql
CREATE TABLE users (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  email         VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name  VARCHAR(100) NOT NULL,
  role          VARCHAR(20)  NOT NULL,   -- USER / ADMIN
  enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at    DATETIME(6)  NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email)
);
