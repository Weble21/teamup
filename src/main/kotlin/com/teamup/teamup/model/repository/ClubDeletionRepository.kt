package com.teamup.teamup.model.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/** Delete dependants before parents; caller holds the club lock and transaction. */
@Repository
class ClubDeletionRepository(private val jdbc: JdbcTemplate) {
    fun deleteClub(clubId: Long) {
        jdbc.update("DELETE FROM match_team_members WHERE match_id IN (SELECT id FROM matches WHERE club_id = ?)", clubId)
        jdbc.update("DELETE FROM match_teams WHERE match_id IN (SELECT id FROM matches WHERE club_id = ?)", clubId)
        jdbc.update("DELETE FROM matches WHERE club_id = ?", clubId)
        jdbc.update("DELETE FROM attendance_vote_comments WHERE vote_id IN (SELECT id FROM attendance_votes WHERE club_id = ?)", clubId)
        jdbc.update("DELETE FROM attendance_responses WHERE vote_id IN (SELECT id FROM attendance_votes WHERE club_id = ?)", clubId)
        jdbc.update("DELETE FROM attendance_votes WHERE club_id = ?", clubId)
        jdbc.update("DELETE FROM club_invitations WHERE club_id = ?", clubId)
        jdbc.update("DELETE FROM club_members WHERE club_id = ?", clubId)
        jdbc.update("DELETE FROM club_memberships WHERE club_id = ?", clubId)
        jdbc.update("DELETE FROM clubs WHERE id = ?", clubId)
    }
}
