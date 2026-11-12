package com.teamup.teamup.model.service

import com.teamup.teamup.view.TeamAnalysisResponse
import com.teamup.teamup.view.TeamInsightResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class TeamAnalysisService(
    private val assignments: TeamAssignmentService,
    private val clubs: ClubService,
    private val claude: ClaudeTeamAnalysisClient,
) {
    fun analyze(userId: Long, clubId: Long, memberIds: List<Long?>, teams: List<List<Long?>?>): TeamAnalysisResponse {
        val assignment = assignments.validate(userId, clubId, memberIds, teams)
        if (assignment.teams.size > 10 || memberIds.size > 100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many teams or participants to analyze")
        }
        val sport = clubs.getMyClub(userId, clubId).sportType
        val insights = claude.analyze(sport, assignment.teams)
        val byNumber = insights.groupBy { it.teamNumber }
        if (byNumber.keys != assignment.teams.map { it.teamNumber }.toSet() || byNumber.values.any { it.size != 1 }) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid AI analysis")
        }
        return TeamAnalysisResponse(assignment.teams.map { team ->
            val insight = byNumber.getValue(team.teamNumber).single()
            if (team.members.none { it.memberId == insight.keyPlayerId } ||
                insight.keyPlayerReason.isBlank() || insight.keyPlayerReason.length > 120 ||
                insight.strength.isBlank() || insight.strength.length > 160) {
                throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid AI analysis")
            }
            TeamInsightResponse(team.teamNumber, insight.keyPlayerId, insight.keyPlayerReason.trim(), insight.strength.trim())
        })
    }
}
