CREATE TABLE club_memberships (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    club_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT uk_club_memberships_club_user UNIQUE (club_id, user_id),
    CONSTRAINT fk_memberships_club FOREIGN KEY (club_id) REFERENCES clubs(id),
    CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_memberships_user (user_id)
);

CREATE TABLE club_invitations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    club_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    CONSTRAINT uk_invitations_club UNIQUE (club_id),
    CONSTRAINT uk_invitations_hash UNIQUE (token_hash),
    CONSTRAINT fk_invitations_club FOREIGN KEY (club_id) REFERENCES clubs(id)
);

ALTER TABLE club_members ADD COLUMN user_id BIGINT NULL;
ALTER TABLE club_members ADD CONSTRAINT fk_club_members_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE club_members ADD CONSTRAINT uk_club_members_club_user UNIQUE (club_id, user_id);
