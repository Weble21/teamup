CREATE TABLE clubs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    sport_type VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_clubs_owner_id (owner_id),
    CONSTRAINT fk_clubs_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);
