package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.*
import com.teamup.teamup.model.repository.*
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

@DataJpaTest(properties = [
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
    "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:social-login-schema.sql",
    "spring.jpa.properties.hibernate.generate_statistics=true",
])
@Import(MatchService::class, ClubMemberService::class, TeamAssignmentService::class, TeamBalancer::class, ClubService::class, NaturalConditionService::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MatchServiceTest {
    @Autowired lateinit var service: MatchService
    @Autowired lateinit var assignments: TeamAssignmentService
    @org.springframework.test.context.bean.override.mockito.MockitoBean lateinit var embeddings: EmbeddingConstraintClient
    @Autowired lateinit var memberService: ClubMemberService
    @Autowired lateinit var clubService: ClubService
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var clubs: ClubRepository
    @Autowired lateinit var members: ClubMemberRepository
    @Autowired lateinit var memberships: ClubMembershipRepository
    @Autowired lateinit var matches: MatchRepository
    @Autowired lateinit var teams: MatchTeamRepository
    @Autowired lateinit var participants: MatchTeamMemberRepository
    @Autowired lateinit var votes: AttendanceVoteRepository
    @Autowired lateinit var voteResponses: AttendanceResponseRepository
    @Autowired lateinit var voteComments: AttendanceVoteCommentRepository
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var entityManagerFactory: EntityManagerFactory
    private var ownerId = 0L
    private var clubId = 0L
    private lateinit var ids: List<Long>
    private lateinit var roster: List<List<Long>>

    @BeforeEach
    fun setup() {
        participants.deleteAllInBatch(); teams.deleteAllInBatch(); matches.deleteAllInBatch()
        voteComments.deleteAllInBatch(); voteResponses.deleteAllInBatch(); votes.deleteAllInBatch(); memberships.deleteAllInBatch()
        members.deleteAllInBatch(); clubs.deleteAllInBatch(); users.deleteAllInBatch()
        val owner = users.save(User("Owner")); ownerId = owner.id!!
        val club = clubs.save(Club(owner, "Club", SportType.BASKETBALL)); clubId = club.id!!
        ids = (1..4).map { memberService.create(ownerId, clubId, "Player $it", "PG", it * 20, null, null).id }
        roster = listOf(ids.take(2), ids.drop(2))
    }

    private fun confirm(requestId: UUID = UUID.randomUUID()) =
        service.confirm(ownerId, clubId, requestId, ids, roster, Instant.parse("2026-11-09T01:00:00Z"), "Game")

    @Test
    fun `closed vote is linked to one match and later confirmation returns the existing record`() {
        val vote = votes.save(AttendanceVote(clubs.findById(clubId).orElseThrow(), "title", "place", LocalDate.now(), Instant.now().minusSeconds(1)))
        val first = service.confirm(ownerId, clubId, UUID.randomUUID(), ids, roster, null, null, vote.id!!)
        val second = service.confirm(ownerId, clubId, UUID.randomUUID(), ids, roster, null, null, vote.id!!)
        assertEquals(first, second); assertEquals(1, matches.count())
        assertEquals(first.id, matches.findByAttendanceVoteId(vote.id!!)?.id)
        assertEquals(first.id, service.detail(ownerId, clubId, first.id).id)
    }

    @Test
    fun `vote match rejects open foreign and unauthorized vote requests`() {
        val club = clubs.findById(clubId).orElseThrow()
        val open = votes.save(AttendanceVote(club, "open", "place", LocalDate.now(), Instant.now().plusSeconds(18000)))
        val otherClub = clubs.save(Club(users.findById(ownerId).orElseThrow(), "other", SportType.BASKETBALL))
        val foreign = votes.save(AttendanceVote(otherClub, "foreign", "place", LocalDate.now(), Instant.now().minusSeconds(1)))
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> { service.confirm(ownerId, clubId, UUID.randomUUID(), ids, roster, null, null, open.id!!) }.statusCode)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { service.confirm(ownerId, clubId, UUID.randomUUID(), ids, roster, null, null, foreign.id!!) }.statusCode)
        val otherUser = users.save(User("other user"))
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { service.confirm(otherUser.id!!, clubId, UUID.randomUUID(), ids, roster, null, null, open.id!!) }.statusCode)
        assertEquals(0, matches.count())
    }

    @Test
    fun `concurrent confirmations for the same vote return one match`() {
        val vote = votes.save(AttendanceVote(clubs.findById(clubId).orElseThrow(), "title", "place", LocalDate.now(), Instant.now().minusSeconds(1)))
        val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2)
        try {
            val tasks = (1..2).map { pool.submit<Long> { start.await(); service.confirm(ownerId, clubId, UUID.randomUUID(), ids, roster, null, null, vote.id!!).id } }
            start.countDown()
            assertEquals(1, tasks.map { it.get(15, TimeUnit.SECONDS) }.distinct().size)
            assertEquals(1, matches.count()); assertEquals(2, teams.count()); assertEquals(4, participants.count())
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `ordinary member can generate validate swap and confirm quick teams but not scheduled teams`() {
        val user = users.save(User("Member"))
        val saved = confirm()
        memberships.save(ClubMembership(clubs.findById(clubId).orElseThrow(), user))
        try {
            assertEquals(saved.id, service.list(user.id!!, clubId, 0, 20).matches.single().id)
            assertEquals(saved, service.detail(user.id!!, clubId, saved.id))
            val generated = assignments.generate(user.id!!, clubId, ids, 2)
            val generatedRoster = generated.teams.map { it.members.map { member -> member.memberId } }
            assignments.validate(user.id!!, clubId, ids, generatedRoster)
            val swapped = assignments.swap(user.id!!, clubId, ids, generatedRoster, generatedRoster[0][0], generatedRoster[1][0])
            val changedRoster = swapped.teams.map { it.members.map { member -> member.memberId } }
            val requestId = UUID.randomUUID()
            val quick = service.confirm(user.id!!, clubId, requestId, ids, changedRoster, null, null)
            assertEquals(quick, service.confirm(user.id!!, clubId, requestId, ids, changedRoster, null, null))
            assertEquals(2, matches.count())
            assertNull(matches.findById(quick.id).orElseThrow().attendanceVote)
            val vote = votes.save(AttendanceVote(clubs.findById(clubId).orElseThrow(), "Closed", "Gym", LocalDate.now(), Instant.now().minusSeconds(1)))
            val error = assertFailsWith<ResponseStatusException> { service.confirm(user.id!!, clubId, UUID.randomUUID(), ids, roster, null, null, vote.id!!) }
            assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
            assertNull(matches.findByAttendanceVoteId(vote.id!!))
            memberships.deleteAllInBatch()
            assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> {
                assignments.generate(user.id!!, clubId, ids, 2)
            }.statusCode)
            assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> {
                service.confirm(user.id!!, clubId, UUID.randomUUID(), ids, roster, null, null)
            }.statusCode)
        } finally { memberships.deleteAllInBatch() }
    }

    @Test
    fun `ordinary member quick formation still rejects inactive and foreign players`() {
        val user = users.save(User("Member"))
        memberships.save(ClubMembership(clubs.findById(clubId).orElseThrow(), user))
        val other = clubs.save(Club(users.findById(ownerId).orElseThrow(), "Other", SportType.BASKETBALL))
        val foreignId = memberService.create(ownerId, other.id!!, "Foreign", "PG", 50, null, null).id
        memberService.deactivate(ownerId, clubId, ids.last())
        for (selection in listOf(ids, ids.take(3) + foreignId)) {
            assertEquals(HttpStatus.BAD_REQUEST, assertFailsWith<ResponseStatusException> {
                assignments.generate(user.id!!, clubId, selection, 2)
            }.statusCode)
        }
        assertEquals(0, matches.count())
    }

    @Test
    fun `server generated match timestamp is stable after database round trip`() {
        val key = UUID.randomUUID()
        val saved = service.confirm(ownerId, clubId, key, ids, roster, null, null)
        assertEquals(saved, service.detail(ownerId, clubId, saved.id))
        assertEquals(saved, service.confirm(ownerId, clubId, key, ids, roster, null, null))
    }

    @Test
    fun `snapshots survive member edits deactivation and club sport changes`() {
        val saved = confirm()
        memberService.update(ownerId, clubId, ids.first(), "Changed", "C", 100, null, null)
        memberService.deactivate(ownerId, clubId, ids.last())
        clubService.update(ownerId, clubId, "Changed club", SportType.FUTSAL)
        assertEquals(saved, service.detail(ownerId, clubId, saved.id))
        assertEquals(2, teams.count()); assertEquals(4, participants.count())
        assertEquals(SportType.BASKETBALL, saved.sportType)
    }

    @Test
    fun `same request ID replays existing match without duplicate rows`() {
        val key = UUID.randomUUID()
        val first = confirm(key)
        memberService.deactivate(ownerId, clubId, ids.first())
        assertEquals(first, confirm(key))
        assertEquals(1, matches.count()); assertEquals(2, teams.count()); assertEquals(4, participants.count())
    }

    @Test
    fun `history includes schedule cards for confirmed votes and keeps quick records without a schedule`() {
        val club = clubs.findById(clubId).orElseThrow()
        val scheduled = (1..3).map { index ->
            val vote = votes.save(AttendanceVote(club, "Schedule $index", "Gym $index", LocalDate.parse("2026-11-27"), Instant.parse("2026-11-25T01:00:00Z")))
            service.confirm(ownerId, clubId, UUID.randomUUID(), ids, roster, null, null, vote.id!!).id to vote
        }
        val quick = confirm()
        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.clear()
        val history = service.list(ownerId, clubId, 0, 20)
        // Authorization + one paged query that fetches optional schedule metadata.
        assertEquals(2, statistics.prepareStatementCount)
        assertEquals(4, history.matches.size)
        assertNull(history.matches.single { it.id == quick.id }.attendanceVote)
        for ((matchId, vote) in scheduled) {
            val card = assertNotNull(history.matches.single { it.id == matchId }.attendanceVote)
            assertEquals(vote.id, card.id); assertEquals(vote.title, card.title)
            assertEquals(vote.location, card.location); assertEquals(vote.exerciseDate, card.exerciseDate)
            assertEquals(vote.closesAt, card.closesAt)
        }
        assertEquals(2, service.list(ownerId, clubId, 0, 2).matches.size)
        assertTrue(service.list(ownerId, clubId, 0, 2).hasNext)
    }

    @Test
    fun `list is paginated and sorted and does not load unrelated club records`() {
        val first = confirm(); val last = confirm()
        val page = service.list(ownerId, clubId, 0, 1)
        assertEquals(last.id, page.matches.single().id); assertTrue(page.hasNext)
        val next = service.list(ownerId, clubId, 1, 1)
        assertEquals(first.id, next.matches.single().id); assertFalse(next.hasNext)
        val emptyClub = clubs.save(Club(users.findById(ownerId).orElseThrow(), "Empty", SportType.FUTSAL))
        assertTrue(service.list(ownerId, emptyClub.id!!, 0, 20).matches.isEmpty())
        assertFailsWith<ResponseStatusException> { service.detail(ownerId, emptyClub.id!!, first.id) }
    }

    @Test
    fun `non owner cannot list read or confirm records`() {
        val saved = confirm(); val otherId = users.save(User("Other")).id!!
        val attempts = listOf<() -> Any>(
            { service.detail(otherId, clubId, saved.id) }, { service.list(otherId, clubId, 0, 20) },
            { service.confirm(otherId, clubId, UUID.randomUUID(), ids, roster, null, null) },
        )
        for (attempt in attempts) assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { attempt() }.statusCode)
        assertEquals(1, matches.count())
    }

    @Test
    fun `invalid roster never commits match or teams`() {
        for (invalid in listOf(listOf(ids.take(1), ids.drop(2)), listOf(ids, ids), listOf(ids.take(3), ids.takeLast(1)))) {
            assertEquals(HttpStatus.BAD_REQUEST, assertFailsWith<ResponseStatusException> {
                service.confirm(ownerId, clubId, UUID.randomUUID(), ids, invalid, null, null)
            }.statusCode)
        }
        assertEquals(0, matches.count()); assertEquals(0, teams.count())
    }

    @Test
    fun `participant write failure rolls back all match rows`() {
        jdbc.execute("ALTER TABLE match_team_members ADD CONSTRAINT test_reject_snapshot CHECK (rating_snapshot < 1)")
        try {
            assertFails { confirm() }
            assertEquals(0, matches.count()); assertEquals(0, teams.count()); assertEquals(0, participants.count())
        } finally { jdbc.execute("ALTER TABLE match_team_members DROP CONSTRAINT test_reject_snapshot") }
    }

    @Test
    fun `club list and detail counts include only active members without per club queries`() {
        val owner = users.findById(ownerId).orElseThrow()
        clubs.save(Club(owner, "Empty 1", SportType.BASKETBALL))
        clubs.save(Club(owner, "Empty 2", SportType.FUTSAL))
        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.clear()
        val listed = clubService.getMyClubs(ownerId)
        assertEquals(3, listed.size)
        assertEquals(4, listed.first { it.id == clubId }.activeMemberCount)
        // User check, visible clubs, batched roles, batched active counts.
        assertEquals(4, statistics.prepareStatementCount)
        memberService.deactivate(ownerId, clubId, ids.first())
        assertEquals(3, clubService.getMyClubs(ownerId).first { it.id == clubId }.activeMemberCount)
        assertEquals(3, clubService.getMyClub(ownerId, clubId).activeMemberCount)
    }
}
