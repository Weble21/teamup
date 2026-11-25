package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.*
import com.teamup.teamup.model.repository.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate

data class VotePerson(val userId: Long, val name: String, val choice: AttendanceChoice)
data class VoteView(val id: Long, val title: String, val location: String, val exerciseDate: LocalDate,
                    val closesAt: Instant, val myChoice: AttendanceChoice?, val responses: List<VotePerson>)

@Service
class AttendanceVoteService(
    private val clubs: ClubRepository,
    private val memberships: ClubMembershipRepository,
    private val users: UserRepository,
    private val votes: AttendanceVoteRepository,
    private val responses: AttendanceResponseRepository,
) {
    private fun visibleClub(clubId: Long, userId: Long): Club {
        val club = clubs.findById(clubId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found") }
        if (club.owner.id != userId && !memberships.existsByClubIdAndUserId(clubId, userId))
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        return club
    }

    @Transactional(readOnly = true)
    fun list(clubId: Long, userId: Long): List<VoteView> {
        visibleClub(clubId, userId)
        val items = votes.findAllByClubIdOrderByClosesAtDesc(clubId)
        val byVote = if (items.isEmpty()) emptyMap() else responses.findWithUsers(items.map { checkNotNull(it.id) }).groupBy { it.vote.id }
        return items.map { vote ->
            val people = byVote[vote.id].orEmpty().map { VotePerson(checkNotNull(it.user.id), it.user.name, it.choice) }
            VoteView(checkNotNull(vote.id), vote.title, vote.location, vote.exerciseDate, vote.closesAt,
                people.find { it.userId == userId }?.choice, people)
        }
    }

    @Transactional
    fun create(clubId: Long, userId: Long, title: String, location: String, date: LocalDate, closesAt: Instant) {
        val club = visibleClub(clubId, userId)
        if (club.owner.id != userId) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the club owner can create votes")
        if (title.isBlank() || title.trim().length > 150 || location.isBlank() || location.trim().length > 100 || closesAt.isBefore(Instant.now().plusSeconds(4 * 3600)))
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid vote details")
        votes.save(AttendanceVote(club, title.trim(), location.trim(), date, closesAt))
    }

    @Transactional
    fun respond(clubId: Long, voteId: Long, userId: Long, choice: AttendanceChoice) {
        visibleClub(clubId, userId)
        val vote = votes.findForUpdate(voteId, clubId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Vote not found")
        if (!Instant.now().isBefore(vote.closesAt)) throw ResponseStatusException(HttpStatus.CONFLICT, "Vote is closed")
        val existing = responses.findByVoteIdAndUserId(voteId, userId)
        if (existing != null) existing.choice = choice
        else responses.save(AttendanceResponse(vote, users.getReferenceById(userId), choice))
    }

    @Transactional
    fun changeDeadline(clubId: Long, voteId: Long, userId: Long, closesAt: Instant) {
        val club = visibleClub(clubId, userId)
        if (club.owner.id != userId) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the club owner can change the deadline")
        val vote = votes.findForUpdate(voteId, clubId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Vote not found")
        if (!closesAt.isAfter(Instant.now())) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Deadline must be in the future")
        vote.changeDeadline(closesAt)
    }
}
