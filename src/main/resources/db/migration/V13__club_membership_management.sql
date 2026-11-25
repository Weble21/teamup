ALTER TABLE club_memberships ADD COLUMN manager BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE club_members ADD COLUMN departed BOOLEAN NOT NULL DEFAULT FALSE;

-- Preserve every existing owner's membership and authority.
INSERT INTO club_memberships (club_id, user_id, manager)
SELECT c.id, c.owner_id, TRUE FROM clubs c
WHERE NOT EXISTS (SELECT 1 FROM club_memberships m WHERE m.club_id = c.id AND m.user_id = c.owner_id);
UPDATE club_memberships m JOIN clubs c ON c.id = m.club_id AND c.owner_id = m.user_id
SET m.manager = TRUE;
