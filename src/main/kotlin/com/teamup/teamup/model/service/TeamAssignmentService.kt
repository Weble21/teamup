package com.teamup.teamup.model.service

import com.teamup.teamup.model.team.TeamParticipant
import com.teamup.teamup.view.AssignedTeamResponse
import com.teamup.teamup.view.TeamAssignmentResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.RoundingMode

@Service
class TeamAssignmentService(
    private val memberService: ClubMemberService,
    private val teamBalancer: TeamBalancer,
) {
    @Transactional(readOnly = true)
    fun generate(ownerId: Long, clubId: Long, memberIds: List<Long?>, teamCount: Int): TeamAssignmentResponse {
        val members = memberService.validateParticipants(ownerId, clubId, memberIds)
        if (teamCount < 2 || teamCount > members.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Team count must be between 2 and the participant count")
        }
        val participants = members.map { TeamParticipant(it.id, it.name, it.position, it.rating) }
        val teams = teamBalancer.balance(participants, teamCount).mapIndexed { index, team ->
            val total = team.sumOf { it.rating.toLong() }
            AssignedTeamResponse(
                index + 1, team, total,
                total.toBigDecimal().divide(team.size.toBigDecimal(), 2, RoundingMode.HALF_UP),
            )
        }
        return TeamAssignmentResponse(clubId, teams)
    }
}
