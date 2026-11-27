CREATE TABLE vote_push_subscriptions (
    endpoint_hash CHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    endpoint VARCHAR(2048) NOT NULL,
    public_key VARCHAR(100) NOT NULL,
    auth_secret VARCHAR(32) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_vote_push_user (user_id)
);
