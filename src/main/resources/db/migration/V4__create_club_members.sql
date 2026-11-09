CREATE TABLE club_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    club_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    position VARCHAR(50) NOT NULL,
    rating INT NOT NULL,
    age INT NULL,
    notes VARCHAR(1000) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    INDEX idx_club_members_club_id (club_id),
    CONSTRAINT fk_club_members_club FOREIGN KEY (club_id) REFERENCES clubs (id),
    CONSTRAINT chk_club_members_rating CHECK (rating BETWEEN 1 AND 100),
    CONSTRAINT chk_club_members_age CHECK (age IS NULL OR age >= 0)
);
