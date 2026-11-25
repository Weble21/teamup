CREATE TABLE attendance_vote_comments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    vote_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_vote_comments_vote FOREIGN KEY (vote_id) REFERENCES attendance_votes(id),
    CONSTRAINT fk_vote_comments_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_vote_comments_vote_id (vote_id, id)
);
