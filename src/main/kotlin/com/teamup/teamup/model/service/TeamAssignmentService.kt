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
    private val naturalConditions: NaturalConditionService,
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
    fun swap(ownerId: Long, clubId: Long, memberIds: List<Long?>, teams: List<List<Long?>?>, firstId: Long, secondId: Long, conditionText: String? = null): TeamAssignmentResponse {
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
        val result = validate(ownerId, clubId, memberIds, swapped)
        val conditions = naturalConditions.interpret(conditionText, result.teams.flatMap { it.members })
        val teamByMember = result.teams.flatMap { team -> team.members.map { it.memberId to team.teamNumber } }.toMap()
        if (conditions.together.any { (a, b) -> teamByMember[a] != teamByMember[b] } ||
            conditions.apart.any { (a, b) -> teamByMember[a] == teamByMember[b] }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Swap violates team conditions")
        }
        return result.copy(interpretedConditions = conditions.descriptions)
    }

    @Transactional(readOnly = true)
    fun generate(ownerId: Long, clubId: Long, memberIds: List<Long?>, teamCount: Int, conditionText: String? = null): TeamAssignmentResponse {
        val members = memberService.validateParticipants(ownerId, clubId, memberIds)
        if (teamCount < 2 || teamCount > members.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Team count must be between 2 and the participant count")
        }
        val participants = members.map { TeamParticipant(it.id, it.name, it.position, it.rating) }
        val conditions = naturalConditions.interpret(conditionText, participants)
        val result = try { teamBalancer.balance(participants, teamCount, conditions) }
            catch (e: IllegalArgumentException) { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Team conditions cannot be satisfied", e) }
        return summarize(clubId, result).copy(interpretedConditions = conditions.descriptions)
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
