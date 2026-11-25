CREATE TABLE attendance_votes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    club_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    location VARCHAR(100) NOT NULL,
    exercise_date DATE NOT NULL,
    closes_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_attendance_votes_club FOREIGN KEY (club_id) REFERENCES clubs(id),
    INDEX idx_attendance_votes_club (club_id)
);

CREATE TABLE attendance_responses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    vote_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    choice VARCHAR(20) NOT NULL,
    CONSTRAINT uk_attendance_response UNIQUE (vote_id, user_id),
    CONSTRAINT fk_attendance_response_vote FOREIGN KEY (vote_id) REFERENCES attendance_votes(id),
    CONSTRAINT fk_attendance_response_user FOREIGN KEY (user_id) REFERENCES users(id)
);
