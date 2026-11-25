ALTER TABLE matches ADD COLUMN attendance_vote_id BIGINT NULL;
ALTER TABLE matches ADD CONSTRAINT fk_matches_attendance_vote FOREIGN KEY (attendance_vote_id) REFERENCES attendance_votes(id);
ALTER TABLE matches ADD CONSTRAINT uk_matches_attendance_vote UNIQUE (attendance_vote_id);
