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
    fun validate(ownerId: Long, clubId: Long, memberIds: List<Long?>, teams: List<List<Long?>?>): TeamAssignmentResponse {
        val members = memberService.validateParticipants(ownerId, clubId, memberIds)
        val assigned = teams.flatMap { it ?: emptyList() }
        if (teams.size < 2 || teams.any { it.isNullOrEmpty() } ||
            assigned.size != memberIds.size || assigned.toSet() != memberIds.toSet() ||
            assigned.distinct().size != assigned.size ||
            teams.maxOf { it!!.size } - teams.minOf { it!!.size } > 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid team roster or team sizes")
        }
        val byId = members.associateBy { it.id }
        return summarize(clubId, teams.map { team -> team!!.map { id ->
            val member = byId.getValue(checkNotNull(id))
            TeamParticipant(member.id, member.name, member.position, member.rating)
        } })
    }

    @Transactional(readOnly = true)
    fun swap(ownerId: Long, clubId: Long, memberIds: List<Long?>, teams: List<List<Long?>?>, firstId: Long, secondId: Long): TeamAssignmentResponse {
        validate(ownerId, clubId, memberIds, teams)
        val firstTeam = teams.indexOfFirst { it!!.contains(firstId) }
        val secondTeam = teams.indexOfFirst { it!!.contains(secondId) }
        if (firstTeam < 0 || secondTeam < 0 || firstTeam == secondTeam) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Select two members from different teams")
        }
        val swapped = teams.map { team -> team!!.map { id -> when (id) {
            firstId -> secondId
            secondId -> firstId
            else -> id
        } } }
        return validate(ownerId, clubId, memberIds, swapped)
    }

    @Transactional(readOnly = true)
    fun generate(ownerId: Long, clubId: Long, memberIds: List<Long?>, teamCount: Int): TeamAssignmentResponse {
        val members = memberService.validateParticipants(ownerId, clubId, memberIds)
        if (teamCount < 2 || teamCount > members.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Team count must be between 2 and the participant count")
        }
        val participants = members.map { TeamParticipant(it.id, it.name, it.position, it.rating) }
        return summarize(clubId, teamBalancer.balance(participants, teamCount))
    }

    private fun summarize(clubId: Long, assignment: List<List<TeamParticipant>>): TeamAssignmentResponse {
        val teams = assignment.mapIndexed { index, team ->
            val total = team.sumOf { it.rating.toLong() }
            AssignedTeamResponse(
                index + 1, team, total,
                total.toBigDecimal().divide(team.size.toBigDecimal(), 2, RoundingMode.HALF_UP),
            )
        }
        return TeamAssignmentResponse(clubId, teams)
    }
}
