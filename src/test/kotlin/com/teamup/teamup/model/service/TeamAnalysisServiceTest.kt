package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.team.TeamParticipant
import com.teamup.teamup.view.AssignedTeamResponse
import com.teamup.teamup.view.ClubResponse
import com.teamup.teamup.view.TeamAssignmentResponse
import com.teamup.teamup.view.TeamInsightResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TeamAnalysisServiceTest {
    private val assignments = mock(TeamAssignmentService::class.java)
    private val clubs = mock(ClubService::class.java)
    private val claude = mock(ClaudeTeamAnalysisClient::class.java)
    private val service = TeamAnalysisService(assignments, clubs, claude)
    private val roster = listOf(listOf(5L), listOf(8L))
    private val teams = listOf(
        AssignedTeamResponse(1, listOf(TeamParticipant(5, "A", "Guard", 70)), 70, "70.00".toBigDecimal()),
        AssignedTeamResponse(2, listOf(TeamParticipant(8, "B", "Center", 80)), 80, "80.00".toBigDecimal()),
    )

    @Test
    fun `accepts exactly one insight per team with a real team member`() {
        `when`(assignments.validate(7, 3, listOf(5L, 8L), roster)).thenReturn(TeamAssignmentResponse(3, teams))
        `when`(clubs.getMyClub(7, 3)).thenReturn(ClubResponse(3, "Club", SportType.BASKETBALL, 7))
        `when`(claude.analyze(SportType.BASKETBALL, teams)).thenReturn(listOf(
            TeamInsightResponse(2, 8, "포지션과 능력치", "수비에 강점이 있습니다."),
            TeamInsightResponse(1, 5, "높은 능력치", "균형 잡힌 전력입니다."),
        ))
        assertEquals(listOf(1, 2), service.analyze(7, 3, listOf(5L, 8L), roster).teams.map { it.teamNumber })
    }

    @Test
    fun `rejects AI output that names a player from another team`() {
        `when`(assignments.validate(7, 3, listOf(5L, 8L), roster)).thenReturn(TeamAssignmentResponse(3, teams))
        `when`(clubs.getMyClub(7, 3)).thenReturn(ClubResponse(3, "Club", SportType.BASKETBALL, 7))
        `when`(claude.analyze(SportType.BASKETBALL, teams)).thenReturn(listOf(
            TeamInsightResponse(1, 8, "핵심 선수", "전력 설명"), TeamInsightResponse(2, 5, "핵심 선수", "전력 설명"),
        ))
        assertEquals(HttpStatus.BAD_GATEWAY, assertFailsWith<ResponseStatusException> {
            service.analyze(7, 3, listOf(5L, 8L), roster)
        }.statusCode)
    }
}
