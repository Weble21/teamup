package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.*
import com.teamup.teamup.model.repository.*
import com.teamup.teamup.model.team.TeamParticipant
import com.teamup.teamup.view.*
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class MatchService(
    private val clubs: ClubRepository,
    private val members: ClubMemberRepository,
    private val matches: MatchRepository,
    private val teams: MatchTeamRepository,
    private val participants: MatchTeamMemberRepository,
    private val assignmentService: TeamAssignmentService,
    private val memberships: ClubMembershipRepository,
) {
    private fun ownedClub(ownerId: Long, clubId: Long): Club = clubs.findByIdAndOwnerId(clubId, ownerId)
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")

    private fun requireVisibleClub(userId: Long, clubId: Long) {
        if (clubs.findByIdAndOwnerId(clubId, userId) == null && !memberships.existsByClubIdAndUserId(clubId, userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        }
    }

    @Transactional
    fun confirm(ownerId: Long, clubId: Long, requestId: UUID, memberIds: List<Long?>, roster: List<List<Long?>?>, playedAt: Instant?, memo: String?): MatchResponse {
        val club = ownedClub(ownerId, clubId)
        val existing = matches.findByClubIdAndRequestId(clubId, requestId.toString())
        if (existing != null) return response(existing, clubId)
        if ((memo?.length ?: 0) > 1000) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Memo is too long")
        val validated = assignmentService.validate(ownerId, clubId, memberIds, roster)
        val match = matches.save(MatchRecord(club, requestId.toString(), club.sportType,
            (playedAt ?: Instant.now()).truncatedTo(ChronoUnit.MICROS), memo?.trim()?.takeIf { it.isNotEmpty() }))
        for (assigned in validated.teams) {
            val team = teams.save(MatchTeam(match, assigned.teamNumber))
            participants.saveAll(assigned.members.map { member ->
                MatchTeamMember(match, team, members.getReferenceById(member.memberId), member.name, member.position, member.rating)
            })
        }
        return response(match, clubId)
    }

    @Transactional(readOnly = true)
    fun detail(ownerId: Long, clubId: Long, matchId: Long): MatchResponse {
        requireVisibleClub(ownerId, clubId)
        val match = matches.findByIdAndClubId(matchId, clubId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found")
        return response(match, clubId)
    }

    @Transactional(readOnly = true)
    fun list(ownerId: Long, clubId: Long, page: Int, size: Int): MatchPageResponse {
        requireVisibleClub(ownerId, clubId)
        if (page !in 0..100000 || size !in 1..100) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination")
        val slice = matches.findAllByClubIdOrderByPlayedAtDescIdDesc(clubId, PageRequest.of(page, size))
        return MatchPageResponse(slice.content.map { MatchSummaryResponse(checkNotNull(it.id), it.sportSnapshot, it.playedAt, it.memo) }, page, slice.hasNext())
    }

    private fun response(match: MatchRecord, clubId: Long): MatchResponse {
        val grouped = participants.findAllByMatchIdOrderByTeamTeamNumberAscIdAsc(checkNotNull(match.id)).groupBy { it.team.teamNumber }
        val assignment = grouped.map { (number, rows) ->
            val players = rows.map { TeamParticipant(checkNotNull(it.member.id), it.nameSnapshot, it.positionSnapshot, it.ratingSnapshot) }
            val total = players.sumOf { it.rating.toLong() }
            AssignedTeamResponse(number, players, total, total.toBigDecimal().divide(players.size.toBigDecimal(), 2, RoundingMode.HALF_UP))
        }
        return MatchResponse(checkNotNull(match.id), clubId, match.sportSnapshot, match.playedAt, match.memo, assignment)
    }
}
