CREATE TABLE user_sport_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    sport_type VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    position VARCHAR(50) NOT NULL,
    rating INT NOT NULL,
    age INT NULL,
    notes VARCHAR(1000) NULL,
    CONSTRAINT uk_user_sport_profiles_user_sport UNIQUE (user_id, sport_type),
    CONSTRAINT fk_user_sport_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_user_sport_profiles_rating CHECK (rating BETWEEN 1 AND 100),
    CONSTRAINT chk_user_sport_profiles_age CHECK (age IS NULL OR age BETWEEN 0 AND 130)
);
