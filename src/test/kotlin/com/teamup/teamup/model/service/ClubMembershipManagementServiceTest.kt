package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.*
import com.teamup.teamup.model.repository.*
import org.junit.jupiter.api.AfterEach
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

@DataJpaTest(properties = ["spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:social-login-schema.sql"])
@Import(ClubMembershipManagementService::class, ClubDeletionRepository::class, ClubService::class,
    ClubMemberService::class, ClubInvitationService::class, AttendanceVoteService::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClubMembershipManagementServiceTest {
    @Autowired lateinit var service: ClubMembershipManagementService
    @Autowired lateinit var clubService: ClubService
    @Autowired lateinit var invites: ClubInvitationService
    @Autowired lateinit var profiles: ClubMemberService
    @Autowired lateinit var voting: AttendanceVoteService
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var clubs: ClubRepository
    @Autowired lateinit var memberships: ClubMembershipRepository
    @Autowired lateinit var members: ClubMemberRepository
    @Autowired lateinit var jdbc: JdbcTemplate

    private val profile = NewClubMember("Player", "PG", 70, null, null)
    private fun user() = users.save(User("Account ${UUID.randomUUID()}"))
    private fun create(owner: User) = clubService.create(owner.id!!, "Club", SportType.BASKETBALL, profile).id
    private fun join(clubId: Long, owner: User, member: User): String {
        val token = invites.issue(owner.id!!, clubId)
        invites.join(member.id!!, token, null, profile)
        return token
    }
    private fun failure(status: HttpStatus, work: () -> Unit) {
        assertEquals(status, assertFailsWith<ResponseStatusException> { work() }.statusCode)
    }

    @AfterEach
    fun clean() {
        listOf("match_team_members", "match_teams", "matches", "attendance_vote_comments", "attendance_responses",
            "attendance_votes", "club_invitations", "club_members", "club_memberships", "clubs", "users")
            .forEach { jdbc.update("DELETE FROM $it") }
    }

    @Test
    fun `additional managers share existing club profile invitation and vote permissions`() {
        val owner = user(); val additional = user(); val id = create(owner)
        join(id, owner, additional)
        service.changeManager(owner.id!!, id, additional.id!!, true, false)
        assertTrue(clubService.getMyClub(additional.id!!, id).manager)
        assertTrue(clubService.getMyClubs(additional.id!!).single().manager)
        clubService.update(additional.id!!, id, "Renamed", SportType.BASKETBALL)
        val player = profiles.create(additional.id!!, id, "New", "PG", 60, null, null)
        profiles.update(additional.id!!, id, player.id, "Changed", "PG", 65, null, null)
        assertEquals(1, profiles.validateParticipants(additional.id!!, id, listOf(player.id)).size)
        assertNotNull(invites.getCurrent(additional.id!!, id).token)
        voting.create(id, additional.id!!, "Vote", "Gym", LocalDate.now(), Instant.now().plusSeconds(20000))
        val vote = voting.list(id, additional.id!!).single()
        voting.changeDeadline(id, vote.id, additional.id!!, Instant.now().plusSeconds(10000))
        voting.close(id, vote.id, additional.id!!)
        assertTrue(voting.list(id, owner.id!!).single().closed)
        service.changeManager(additional.id!!, id, owner.id!!, false, false)
        assertFalse(clubService.getMyClub(owner.id!!, id).manager)
        assertEquals(additional.id, clubService.getMyClub(additional.id!!, id).ownerId)
        failure(HttpStatus.NOT_FOUND) { clubService.update(owner.id!!, id, "Forbidden", SportType.FUTSAL) }
    }

    @Test
    fun `transfer grants target authority and removes actor authority atomically`() {
        val owner = user(); val target = user(); val id = create(owner)
        join(id, owner, target)
        service.changeManager(owner.id!!, id, target.id!!, true, true)
        assertFalse(clubService.getMyClub(owner.id!!, id).manager)
        assertTrue(clubService.getMyClub(target.id!!, id).manager)
        service.leave(owner.id!!, id)
        assertTrue(clubService.getMyClubs(owner.id!!).isEmpty())
    }

    @Test
    fun `last manager cannot leave or lose authority and outsiders cannot manage`() {
        val owner = user(); val ordinary = user(); val foreign = user(); val id = create(owner)
        join(id, owner, ordinary)
        failure(HttpStatus.CONFLICT) { service.leave(owner.id!!, id) }
        failure(HttpStatus.CONFLICT) { service.changeManager(owner.id!!, id, owner.id!!, false, false) }
        failure(HttpStatus.FORBIDDEN) { service.changeManager(ordinary.id!!, id, ordinary.id!!, true, false) }
        failure(HttpStatus.FORBIDDEN) { service.delete(ordinary.id!!, id, "삭제") }
        failure(HttpStatus.NOT_FOUND) { service.changeManager(owner.id!!, id, foreign.id!!, true, false) }
        failure(HttpStatus.NOT_FOUND) { service.leave(foreign.id!!, id) }
        failure(HttpStatus.BAD_REQUEST) { service.changeManager(owner.id!!, id, owner.id!!, true, true) }
        for (text in listOf("", "delete", "삭제 ", " 삭제")) failure(HttpStatus.BAD_REQUEST) { service.delete(owner.id!!, id, text) }
        assertTrue(clubService.getMyClub(owner.id!!, id).manager)
        assertEquals(2, memberships.count())
    }

    // Includes real foreign-key references to the departing player's profile.
    private fun history(clubId: Long, actor: User): Long {
        voting.create(clubId, actor.id!!, "History", "Gym", LocalDate.now(), Instant.now().plusSeconds(20000))
        val vote = voting.list(clubId, actor.id!!).single().id
        voting.respond(clubId, vote, actor.id!!, AttendanceChoice.ATTENDING)
        voting.addComment(clubId, vote, actor.id!!, "Keep comment")
        val member = members.findByClubIdAndUserId(clubId, actor.id!!)!!
        jdbc.update("INSERT INTO matches (club_id, request_id, sport_snapshot, played_at, attendance_vote_id) VALUES (?, ?, 'BASKETBALL', CURRENT_TIMESTAMP, ?)", clubId, UUID.randomUUID().toString(), vote)
        val match = jdbc.queryForObject("SELECT id FROM matches WHERE club_id = ?", Long::class.java, clubId)!!
        jdbc.update("INSERT INTO match_teams (match_id, team_number) VALUES (?, 1)", match)
        val team = jdbc.queryForObject("SELECT id FROM match_teams WHERE match_id = ?", Long::class.java, match)!!
        jdbc.update("INSERT INTO match_team_members (match_id, match_team_id, club_member_id, name_snapshot, position_snapshot, rating_snapshot) VALUES (?, ?, ?, 'Snapshot', 'PG', 70)", match, team, member.id)
        return member.id!!
    }

    @Test
    fun `leave preserves records revokes access and rejoin reuses the original player`() {
        val owner = user(); val returning = user(); val stranger = user(); val id = create(owner)
        val token = join(id, owner, returning)
        service.changeManager(owner.id!!, id, returning.id!!, true, false)
        val originalId = history(id, returning)
        service.leave(returning.id!!, id)
        assertTrue(members.findById(originalId).orElseThrow().departed)
        assertFalse(members.findById(originalId).orElseThrow().active)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM match_team_members WHERE club_member_id = ?", Int::class.java, originalId))
        assertEquals("Keep comment", voting.list(id, owner.id!!).single().comments.single().content)
        assertEquals(returning.id, voting.list(id, owner.id!!).single().responses.single().userId)
        assertTrue(clubService.getMyClubs(returning.id!!).isEmpty())
        failure(HttpStatus.NOT_FOUND) { clubService.getMyClub(returning.id!!, id) }
        failure(HttpStatus.NOT_FOUND) { profiles.saveMyProfile(returning.id!!, id, profile) }
        failure(HttpStatus.NOT_FOUND) { voting.addComment(id, voting.list(id, owner.id!!).single().id, returning.id!!, "Forbidden") }
        assertFalse(invites.preview(stranger.id!!, token).candidates.any { it.id == originalId })
        assertTrue(invites.preview(returning.id!!, token).candidates.any { it.id == originalId })
        invites.join(returning.id!!, token, originalId, null)
        assertEquals(originalId, members.findByClubIdAndUserId(id, returning.id!!)!!.id)
        assertTrue(members.findById(originalId).orElseThrow().active)
        assertFalse(clubService.getMyClub(returning.id!!, id).manager)
        service.leave(returning.id!!, id)
        invites.join(returning.id!!, token, null, profile.copy(name = "Updated"))
        assertEquals("Updated", members.findById(originalId).orElseThrow().name)
        assertEquals(2, members.count())
    }

    @Test
    fun `additional manager deletes all club data without touching accounts or another club`() {
        val owner = user(); val additional = user(); val id = create(owner); val otherId = create(owner)
        val token = join(id, owner, additional)
        service.changeManager(owner.id!!, id, additional.id!!, true, false)
        history(id, additional)
        service.delete(additional.id!!, id, "삭제")
        assertFalse(clubs.existsById(id)); assertTrue(clubs.existsById(otherId))
        for (table in listOf("match_team_members", "match_teams", "matches", "attendance_votes", "attendance_responses", "attendance_vote_comments", "club_invitations"))
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java))
        assertEquals(1, members.count()); assertEquals(1, memberships.count()); assertEquals(2, users.count())
        failure(HttpStatus.NOT_FOUND) { invites.preview(additional.id!!, token) }
        failure(HttpStatus.NOT_FOUND) { service.delete(additional.id!!, id, "삭제") }
    }

    @Test
    fun `deletion failure rolls back all dependent deletions`() {
        val owner = user(); val id = create(owner)
        val token = invites.issue(owner.id!!, id)
        history(id, owner)
        jdbc.execute("CREATE TABLE test_deletion_blocker (club_id BIGINT REFERENCES clubs(id))")
        try {
            jdbc.update("INSERT INTO test_deletion_blocker (club_id) VALUES (?)", id)
            assertFails { service.delete(owner.id!!, id, "삭제") }
            assertTrue(clubs.existsById(id))
            assertEquals(1, voting.list(id, owner.id!!).single().comments.size)
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM match_team_members", Int::class.java))
            assertTrue(invites.preview(owner.id!!, token).alreadyJoined)
        } finally { jdbc.execute("DROP TABLE test_deletion_blocker") }
    }

    @Test
    fun `concurrent departures leave exactly one manager`() {
        val owner = user(); val additional = user(); val id = create(owner)
        join(id, owner, additional); service.changeManager(owner.id!!, id, additional.id!!, true, false)
        val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2)
        try {
            val attempts = listOf(owner, additional).map { actor -> pool.submit<Boolean> {
                start.await()
                try { service.leave(actor.id!!, id); true } catch (failure: ResponseStatusException) {
                    assertEquals(HttpStatus.CONFLICT, failure.statusCode); false
                }
            } }
            start.countDown()
            assertEquals(1, attempts.count { it.get(15, TimeUnit.SECONDS) })
            assertEquals(1, memberships.findWithUsers(id).count { it.manager })
            assertEquals(1, memberships.count())
        } finally { pool.shutdownNow(); pool.awaitTermination(15, TimeUnit.SECONDS) }
    }
}
