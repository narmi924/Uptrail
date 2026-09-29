-- Runs on every start of the demo database: creates the application schema and its user.
CREATE DATABASE IF NOT EXISTS uptrail CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'uptrail'@'127.0.0.1' IDENTIFIED BY 'uptrail-demo';
CREATE USER IF NOT EXISTS 'uptrail'@'localhost' IDENTIFIED BY 'uptrail-demo';
GRANT ALL PRIVILEGES ON uptrail.* TO 'uptrail'@'127.0.0.1';
GRANT ALL PRIVILEGES ON uptrail.* TO 'uptrail'@'localhost';
