-- Existing hash-only invitations remain valid but need manual reissue for retrieval.
ALTER TABLE club_invitations ADD COLUMN token VARCHAR(43) NULL;
