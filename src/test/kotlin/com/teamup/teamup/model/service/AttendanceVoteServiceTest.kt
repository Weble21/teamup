package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.*
import com.teamup.teamup.model.repository.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DataJpaTest(properties = ["spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"])
@Import(AttendanceVoteService::class)
class AttendanceVoteServiceTest {
    @Autowired lateinit var service: AttendanceVoteService
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var clubs: ClubRepository
    @Autowired lateinit var memberships: ClubMembershipRepository
    @Autowired lateinit var votes: AttendanceVoteRepository
    @Autowired lateinit var members: ClubMemberRepository
    @Autowired lateinit var matches: MatchRepository
    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun setDatabaseDefaults() {
        jdbc.execute("ALTER TABLE users ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP")
    }

    @Test
    fun `owner creates vote members can change response and outsiders cannot access`() {
        val owner = users.save(User("owner"))
        val member = users.save(User("member"))
        val outsider = users.save(User("outsider"))
        val club = clubs.save(Club(owner, "club", SportType.BASKETBALL))
        memberships.save(ClubMembership(club, member))
        val clubId = club.id!!
        service.create(clubId, owner.id!!, "title", "place", LocalDate.now(), Instant.now().plusSeconds(18000))
        val vote = service.list(clubId, member.id!!).single()
        service.respond(clubId, vote.id, member.id!!, AttendanceChoice.UNDECIDED)
        service.respond(clubId, vote.id, member.id!!, AttendanceChoice.ATTENDING)
        assertEquals(AttendanceChoice.ATTENDING, service.list(clubId, owner.id!!).single().responses.single().choice)
        assertEquals("선수 프로필 미등록", service.list(clubId, owner.id!!).single().responses.single().name)
        members.save(ClubMember(club, "이지훈", "PG", 70, user = member))
        assertEquals("이지훈", service.list(clubId, owner.id!!).single().responses.single().name)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { service.list(clubId, outsider.id!!) }.statusCode)
        assertEquals(HttpStatus.FORBIDDEN, assertFailsWith<ResponseStatusException> {
            service.create(clubId, member.id!!, "title", "place", LocalDate.now(), Instant.now().plusSeconds(18000))
        }.statusCode)
    }

    @Test
    fun `vote names use linked profiles in the current club for every choice and reflect profile edits`() {
        val owner = users.save(User("Owner")); val account = users.save(User("김동건")); val sameName = users.save(User("김동건"))
        val first = clubs.save(Club(owner, "First", SportType.BASKETBALL))
        val second = clubs.save(Club(owner, "Second", SportType.BASKETBALL))
        memberships.save(ClubMembership(first, account)); memberships.save(ClubMembership(second, account))
        memberships.save(ClubMembership(first, sameName))
        val profile = members.save(ClubMember(first, "김동건1", "PG", 70, user = account))
        members.save(ClubMember(second, "김동건2", "C", 70, user = account))
        members.save(ClubMember(first, "다른 선수", "C", 70, user = sameName))
        val firstVote = votes.save(AttendanceVote(first, "First vote", "Gym", LocalDate.now(), Instant.now().plusSeconds(18000)))
        val secondVote = votes.save(AttendanceVote(second, "Second vote", "Gym", LocalDate.now(), Instant.now().plusSeconds(18000)))
        service.respond(first.id!!, firstVote.id!!, sameName.id!!, AttendanceChoice.ATTENDING)
        service.respond(second.id!!, secondVote.id!!, account.id!!, AttendanceChoice.ATTENDING)
        for (choice in AttendanceChoice.entries) {
            service.respond(first.id!!, firstVote.id!!, account.id!!, choice)
            val listed = service.list(first.id!!, account.id!!).single()
            assertEquals("김동건1", listed.responses.single { it.userId == account.id }.name)
            assertEquals("다른 선수", listed.responses.single { it.userId == sameName.id }.name)
            assertEquals(choice, listed.myChoice)
        }
        profile.updateDetails("김동건3", "PG", 70, null, null)
        assertEquals("김동건3", service.list(first.id!!, account.id!!).single().responses.single { it.userId == account.id }.name)
        assertEquals("김동건2", service.list(second.id!!, account.id!!).single().responses.single().name)
        profile.depart()
        assertEquals("김동건3", service.list(first.id!!, owner.id!!).single().responses.single { it.userId == account.id }.name)
    }

    @Test
    fun `rejects early deadlines and changes after closing`() {
        val owner = users.save(User("owner"))
        val club = clubs.save(Club(owner, "club", SportType.BASKETBALL))
        assertEquals(HttpStatus.BAD_REQUEST, assertFailsWith<ResponseStatusException> {
            service.create(club.id!!, owner.id!!, "title", "place", LocalDate.now(), Instant.now().plusSeconds(3600))
        }.statusCode)
        val closed = votes.save(AttendanceVote(club, "title", "place", LocalDate.now(), Instant.now().minusSeconds(1)))
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> {
            service.respond(club.id!!, closed.id!!, owner.id!!, AttendanceChoice.ATTENDING)
        }.statusCode)
    }

    @Test
    fun `owner can change deadline before closing without losing responses`() {
        val owner = users.save(User("owner"))
        val club = clubs.save(Club(owner, "club", SportType.BASKETBALL))
        val vote = votes.save(AttendanceVote(club, "title", "place", LocalDate.now(), Instant.now().plusSeconds(18000)))
        service.respond(club.id!!, vote.id!!, owner.id!!, AttendanceChoice.ATTENDING)
        val newDeadline = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        service.changeDeadline(club.id!!, vote.id!!, owner.id!!, newDeadline)
        assertEquals(newDeadline, service.list(club.id!!, owner.id!!).single().closesAt)
        assertEquals(AttendanceChoice.ATTENDING, service.list(club.id!!, owner.id!!).single().myChoice)
        val closed = votes.save(AttendanceVote(club, "closed", "place", LocalDate.now(), Instant.now().minusSeconds(1)))
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> {
            service.changeDeadline(club.id!!, closed.id!!, owner.id!!, newDeadline)
        }.statusCode)
    }

    @Test
    fun `members and outsiders cannot change deadline and past deadlines are rejected`() {
        val owner = users.save(User("owner"))
        val member = users.save(User("member"))
        val outsider = users.save(User("outsider"))
        val club = clubs.save(Club(owner, "club", SportType.BASKETBALL))
        memberships.save(ClubMembership(club, member))
        val initial = Instant.now().plusSeconds(18000)
        val vote = votes.save(AttendanceVote(club, "title", "place", LocalDate.now(), initial))
        assertEquals(HttpStatus.FORBIDDEN, assertFailsWith<ResponseStatusException> {
            service.changeDeadline(club.id!!, vote.id!!, member.id!!, initial.plusSeconds(3600))
        }.statusCode)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> {
            service.changeDeadline(club.id!!, vote.id!!, outsider.id!!, initial.plusSeconds(3600))
        }.statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, assertFailsWith<ResponseStatusException> {
            service.changeDeadline(club.id!!, vote.id!!, owner.id!!, Instant.now().minusSeconds(1))
        }.statusCode)
        assertEquals(initial, service.list(club.id!!, owner.id!!).single().closesAt)
    }

    @Test
    fun `owner closes vote immediately and existing response remains unchanged`() {
        val owner = users.save(User("owner")); val club = clubs.save(Club(owner, "club", SportType.BASKETBALL))
        val vote = votes.save(AttendanceVote(club, "title", "place", LocalDate.now(), Instant.now().plusSeconds(18000)))
        service.respond(club.id!!, vote.id!!, owner.id!!, AttendanceChoice.ATTENDING)
        service.close(club.id!!, vote.id!!, owner.id!!)
        val closed = service.list(club.id!!, owner.id!!).single()
        assertEquals(true, closed.closed)
        assertEquals(AttendanceChoice.ATTENDING, closed.myChoice)
        service.close(club.id!!, vote.id!!, owner.id!!)
        assertEquals(closed.closesAt, service.list(club.id!!, owner.id!!).single().closesAt)
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> {
            service.respond(club.id!!, vote.id!!, owner.id!!, AttendanceChoice.ABSENT)
        }.statusCode)
    }

    @Test
    fun `only the owner can close a vote`() {
        val owner = users.save(User("owner")); val member = users.save(User("member")); val outsider = users.save(User("outsider"))
        val club = clubs.save(Club(owner, "club", SportType.BASKETBALL)); memberships.save(ClubMembership(club, member))
        val vote = votes.save(AttendanceVote(club, "title", "place", LocalDate.now(), Instant.now().plusSeconds(18000)))
        assertEquals(HttpStatus.FORBIDDEN, assertFailsWith<ResponseStatusException> { service.close(club.id!!, vote.id!!, member.id!!) }.statusCode)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { service.close(club.id!!, vote.id!!, outsider.id!!) }.statusCode)
        assertEquals(false, service.list(club.id!!, owner.id!!).single().closed)
    }

    @Test
    fun `club members can comment with authenticated account name even after voting closes`() {
        val owner = users.save(User("Owner")); val member = users.save(User("Member"))
        val club = clubs.save(Club(owner, "club", SportType.BASKETBALL)); memberships.save(ClubMembership(club, member))
        val vote = votes.save(AttendanceVote(club, "title", "place", LocalDate.now(), Instant.now().minusSeconds(1)))
        val comment = service.addComment(club.id!!, vote.id!!, member.id!!, "  See you next time  ")
        assertEquals("Member", comment.authorName); assertEquals("See you next time", comment.content)
        assertEquals(listOf(comment), service.list(club.id!!, owner.id!!).single().comments)
    }

    @Test
    fun `comments reject outsiders wrong club and invalid content`() {
        val owner = users.save(User("Owner")); val outsider = users.save(User("Outsider"))
        val club = clubs.save(Club(owner, "club", SportType.BASKETBALL))
        val otherClub = clubs.save(Club(owner, "other", SportType.BASKETBALL))
        val vote = votes.save(AttendanceVote(club, "title", "place", LocalDate.now(), Instant.now().plusSeconds(18000)))
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { service.addComment(club.id!!, vote.id!!, outsider.id!!, "hello") }.statusCode)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { service.addComment(otherClub.id!!, vote.id!!, owner.id!!, "hello") }.statusCode)
        for (text in listOf("   ", "a".repeat(501))) assertEquals(HttpStatus.BAD_REQUEST,
            assertFailsWith<ResponseStatusException> { service.addComment(club.id!!, vote.id!!, owner.id!!, text) }.statusCode)
        assertEquals(emptyList(), service.list(club.id!!, owner.id!!).single().comments)
    }

    @Test
    fun `closed vote setup maps attending accounts to active club member ids and reports unavailable profiles`() {
        val owner = users.save(User("owner")); val activeUser = users.save(User("same")); val inactiveUser = users.save(User("same"))
        val missing = users.save(User("missing")); val absent = users.save(User("absent"))
        val club = clubs.save(Club(owner, "club", SportType.BASKETBALL))
        listOf(activeUser, inactiveUser, missing, absent).forEach { memberships.save(ClubMembership(club, it)) }
        val active = members.save(ClubMember(club, "same", "PG", 70, user = activeUser))
        members.save(ClubMember(club, "Inactive player", "C", 70, user = inactiveUser).also { it.deactivate() })
        val vote = votes.save(AttendanceVote(club, "title", "place", LocalDate.now(), Instant.now().plusSeconds(18000)))
        listOf(activeUser, inactiveUser, missing).forEach { service.respond(club.id!!, vote.id!!, it.id!!, AttendanceChoice.ATTENDING) }
        service.respond(club.id!!, vote.id!!, absent.id!!, AttendanceChoice.ABSENT)
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> { service.teamSetup(club.id!!, vote.id!!, owner.id!!) }.statusCode)
        service.close(club.id!!, vote.id!!, owner.id!!)
        val setup = service.teamSetup(club.id!!, vote.id!!, owner.id!!)
        assertEquals(listOf(active.id), setup.memberIds)
        assertEquals(setOf(UnavailableAttendee("Inactive player", "INACTIVE"), UnavailableAttendee("missing", "PROFILE_MISSING")), setup.unavailable.toSet())
        assertEquals(null, setup.matchId)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { service.teamSetup(club.id!!, vote.id!!, activeUser.id!!) }.statusCode)
    }

    @Test
    fun `vote list and setup expose existing linked match`() {
        val owner = users.save(User("owner")); val club = clubs.save(Club(owner, "club", SportType.BASKETBALL))
        val vote = votes.save(AttendanceVote(club, "title", "place", LocalDate.now(), Instant.now().minusSeconds(1)))
        val match = matches.save(MatchRecord(club, java.util.UUID.randomUUID().toString(), club.sportType, Instant.now(), null, vote))
        assertEquals(match.id, service.list(club.id!!, owner.id!!).single().matchId)
        assertEquals(match.id, service.teamSetup(club.id!!, vote.id!!, owner.id!!).matchId)
    }
}
