CREATE TABLE matches (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    club_id BIGINT NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    sport_snapshot VARCHAR(30) NOT NULL,
    played_at TIMESTAMP(6) NOT NULL,
    memo VARCHAR(1000) NULL,
    CONSTRAINT uk_matches_request UNIQUE (club_id, request_id),
    CONSTRAINT fk_matches_club FOREIGN KEY (club_id) REFERENCES clubs(id),
    INDEX idx_matches_club_date (club_id, played_at, id)
);
CREATE TABLE match_teams (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    match_id BIGINT NOT NULL,
    team_number INT NOT NULL,
    CONSTRAINT uk_match_team UNIQUE (match_id, team_number),
    CONSTRAINT fk_match_teams_match FOREIGN KEY (match_id) REFERENCES matches(id)
);
CREATE TABLE match_team_members (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    match_id BIGINT NOT NULL,
    match_team_id BIGINT NOT NULL,
    club_member_id BIGINT NOT NULL,
    name_snapshot VARCHAR(100) NOT NULL,
    position_snapshot VARCHAR(50) NOT NULL,
    rating_snapshot INT NOT NULL,
    CONSTRAINT uk_match_participant UNIQUE (match_id, club_member_id),
    CONSTRAINT fk_match_members_match FOREIGN KEY (match_id) REFERENCES matches(id),
    CONSTRAINT fk_match_members_team FOREIGN KEY (match_team_id) REFERENCES match_teams(id),
    CONSTRAINT fk_match_members_member FOREIGN KEY (club_member_id) REFERENCES club_members(id)
);
