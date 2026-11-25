package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.*
import com.teamup.teamup.model.repository.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate

data class VotePerson(val userId: Long, val name: String, val choice: AttendanceChoice)
data class VoteCommentView(val id: Long, val authorName: String, val content: String, val createdAt: Instant)
data class UnavailableAttendee(val name: String, val reason: String)
data class VoteTeamSetup(val memberIds: List<Long>, val unavailable: List<UnavailableAttendee>, val matchId: Long?)
data class VoteView(val id: Long, val title: String, val location: String, val exerciseDate: LocalDate,
                    val closesAt: Instant, val myChoice: AttendanceChoice?, val responses: List<VotePerson>, val closed: Boolean,
                    val comments: List<VoteCommentView>, val matchId: Long?)

@Service
class AttendanceVoteService(
    private val clubs: ClubRepository,
    private val memberships: ClubMembershipRepository,
    private val users: UserRepository,
    private val votes: AttendanceVoteRepository,
    private val responses: AttendanceResponseRepository,
    private val comments: AttendanceVoteCommentRepository,
    private val members: ClubMemberRepository,
    private val matches: MatchRepository,
) {
    private fun visibleClub(clubId: Long, userId: Long): Club {
        val club = clubs.findById(clubId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found") }
        if (club.owner.id != userId && !memberships.existsByClubIdAndUserId(clubId, userId))
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        return club
    }

    private fun writableClub(clubId: Long, userId: Long): Club {
        clubs.findForUpdate(clubId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        return visibleClub(clubId, userId)
    }

    @Transactional(readOnly = true)
    fun list(clubId: Long, userId: Long): List<VoteView> {
        visibleClub(clubId, userId)
        val items = votes.findAllByClubIdOrderByClosesAtDesc(clubId)
        val byVote = if (items.isEmpty()) emptyMap() else responses.findWithUsers(items.map { checkNotNull(it.id) }).groupBy { it.vote.id }
        val commentsByVote = if (items.isEmpty()) emptyMap() else comments.findWithAuthors(items.map { checkNotNull(it.id) }).groupBy { it.vote.id }
        val matchByVote = if (items.isEmpty()) emptyMap() else matches.findAllByAttendanceVoteIdIn(items.map { checkNotNull(it.id) }).associate { it.attendanceVote!!.id to it.id }
        val userIds = byVote.values.flatten().map { checkNotNull(it.user.id) }.distinct()
        val namesByUser = if (userIds.isEmpty()) emptyMap() else members.findAllByClubIdAndUserIdIn(clubId, userIds)
            .associate { checkNotNull(it.user?.id) to it.name }
        return items.map { vote ->
            val people = byVote[vote.id].orEmpty().map {
                VotePerson(checkNotNull(it.user.id), namesByUser[it.user.id] ?: it.user.name, it.choice)
            }
            VoteView(checkNotNull(vote.id), vote.title, vote.location, vote.exerciseDate, vote.closesAt,
                people.find { it.userId == userId }?.choice, people, !Instant.now().isBefore(vote.closesAt),
                commentsByVote[vote.id].orEmpty().map(::commentView), matchByVote[vote.id])
        }
    }

    @Transactional(readOnly = true)
    fun teamSetup(clubId: Long, voteId: Long, userId: Long): VoteTeamSetup {
        clubs.findManaged(clubId, userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        val vote = votes.findByIdAndClubId(voteId, clubId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Vote not found")
        if (Instant.now().isBefore(vote.closesAt)) throw ResponseStatusException(HttpStatus.CONFLICT, "Vote is not closed")
        val matchId = matches.findByAttendanceVoteId(voteId)?.id
        val attending = responses.findByVoteAndChoice(voteId, AttendanceChoice.ATTENDING)
        val userIds = attending.map { checkNotNull(it.user.id) }
        val byUser = if (userIds.isEmpty()) emptyMap() else members.findAllByClubIdAndUserIdIn(clubId, userIds).associateBy { it.user?.id }
        val eligible = mutableListOf<Long>()
        val unavailable = mutableListOf<UnavailableAttendee>()
        for (response in attending) {
            val member = byUser[response.user.id]
            when {
                member == null -> unavailable.add(UnavailableAttendee(response.user.name, "PROFILE_MISSING"))
                !member.active -> unavailable.add(UnavailableAttendee(member.name, "INACTIVE"))
                else -> eligible.add(checkNotNull(member.id))
            }
        }
        return VoteTeamSetup(eligible, unavailable, matchId)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun create(clubId: Long, userId: Long, title: String, location: String, date: LocalDate, closesAt: Instant) {
        val club = writableClub(clubId, userId)
        if (clubs.findManaged(clubId, userId) == null) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the club owner can create votes")
        if (title.isBlank() || title.trim().length > 150 || location.isBlank() || location.trim().length > 100 || closesAt.isBefore(Instant.now().plusSeconds(4 * 3600)))
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid vote details")
        votes.save(AttendanceVote(club, title.trim(), location.trim(), date, closesAt))
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun respond(clubId: Long, voteId: Long, userId: Long, choice: AttendanceChoice) {
        writableClub(clubId, userId)
        val vote = votes.findForUpdate(voteId, clubId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Vote not found")
        if (!Instant.now().isBefore(vote.closesAt)) throw ResponseStatusException(HttpStatus.CONFLICT, "Vote is closed")
        val existing = responses.findByVoteIdAndUserId(voteId, userId)
        if (existing != null) existing.choice = choice
        else responses.save(AttendanceResponse(vote, users.getReferenceById(userId), choice))
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun changeDeadline(clubId: Long, voteId: Long, userId: Long, closesAt: Instant) {
        val club = writableClub(clubId, userId)
        if (clubs.findManaged(clubId, userId) == null) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the club owner can change the deadline")
        val vote = votes.findForUpdate(voteId, clubId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Vote not found")
        if (!Instant.now().isBefore(vote.closesAt)) throw ResponseStatusException(HttpStatus.CONFLICT, "Vote is closed")
        if (!closesAt.isAfter(Instant.now())) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Deadline must be in the future")
        vote.changeDeadline(closesAt)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun close(clubId: Long, voteId: Long, userId: Long) {
        val club = writableClub(clubId, userId)
        if (clubs.findManaged(clubId, userId) == null) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the club owner can close votes")
        val vote = votes.findForUpdate(voteId, clubId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Vote not found")
        val now = Instant.now()
        if (now.isBefore(vote.closesAt)) vote.changeDeadline(now)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun addComment(clubId: Long, voteId: Long, userId: Long, content: String): VoteCommentView {
        writableClub(clubId, userId)
        val vote = votes.findByIdAndClubId(voteId, clubId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Vote not found")
        val trimmed = content.trim()
        if (trimmed.isEmpty() || trimmed.length > 500) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid comment")
        val author = users.findById(userId).orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found") }
        return commentView(comments.save(AttendanceVoteComment(vote, author, trimmed)))
    }

    private fun commentView(comment: AttendanceVoteComment) = VoteCommentView(
        checkNotNull(comment.id), comment.author.name, comment.content, comment.createdAt,
    )
}
