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
import java.util.UUID
import kotlin.test.*

@DataJpaTest(properties = [
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
    "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:social-login-schema.sql",
    "spring.jpa.properties.hibernate.generate_statistics=true",
])
@Import(MatchService::class, ClubMemberService::class, TeamAssignmentService::class, TeamBalancer::class, ClubService::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MatchServiceTest {
    @Autowired lateinit var service: MatchService
    @Autowired lateinit var memberService: ClubMemberService
    @Autowired lateinit var clubService: ClubService
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var clubs: ClubRepository
    @Autowired lateinit var members: ClubMemberRepository
    @Autowired lateinit var matches: MatchRepository
    @Autowired lateinit var teams: MatchTeamRepository
    @Autowired lateinit var participants: MatchTeamMemberRepository
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var entityManagerFactory: EntityManagerFactory
    private var ownerId = 0L
    private var clubId = 0L
    private lateinit var ids: List<Long>
    private lateinit var roster: List<List<Long>>

    @BeforeEach
    fun setup() {
        participants.deleteAllInBatch(); teams.deleteAllInBatch(); matches.deleteAllInBatch()
        members.deleteAllInBatch(); clubs.deleteAllInBatch(); users.deleteAllInBatch()
        val owner = users.save(User("Owner")); ownerId = owner.id!!
        val club = clubs.save(Club(owner, "Club", SportType.BASKETBALL)); clubId = club.id!!
        ids = (1..4).map { memberService.create(ownerId, clubId, "Player $it", "PG", it * 20, null, null).id }
        roster = listOf(ids.take(2), ids.drop(2))
    }

    private fun confirm(requestId: UUID = UUID.randomUUID()) =
        service.confirm(ownerId, clubId, requestId, ids, roster, Instant.parse("2026-11-09T01:00:00Z"), "Game")

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
        assertEquals(3, statistics.prepareStatementCount)
        memberService.deactivate(ownerId, clubId, ids.first())
        assertEquals(3, clubService.getMyClubs(ownerId).first { it.id == clubId }.activeMemberCount)
        assertEquals(3, clubService.getMyClub(ownerId, clubId).activeMemberCount)
    }
}
