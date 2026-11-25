package com.teamup.teamup

import com.teamup.teamup.model.entity.*
import com.teamup.teamup.model.repository.*
import com.teamup.teamup.model.service.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

// Opt-in only. All mutations and cleanup are scoped to records created here.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "TEAMUP_VERIFY_MEMBERSHIP_MYSQL", matches = "true")
class ClubMembershipMySqlTests {
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var members: ClubMemberRepository
    @Autowired lateinit var memberships: ClubMembershipRepository
    @Autowired lateinit var clubService: ClubService
    @Autowired lateinit var management: ClubMembershipManagementService
    @Autowired lateinit var invites: ClubInvitationService
    @Autowired lateinit var voting: AttendanceVoteService
    @Autowired lateinit var matches: MatchService
    @Autowired lateinit var deletion: ClubDeletionRepository
    @Autowired lateinit var jdbc: JdbcTemplate
    private val userIds = mutableListOf<Long>()
    private var clubId: Long? = null

    @AfterEach
    fun cleanup() {
        clubId?.let { deletion.deleteClub(it) }
        userIds.forEach { jdbc.update("DELETE FROM users WHERE id = ?", it) }
    }

    @Test
    fun `mysql managers concurrently leave then rejoin and delete all linked records`() {
        assertEquals("MySQL", jdbc.dataSource!!.connection.use { it.metaData.databaseProductName })
        val actors = (1..2).map { users.save(User("Membership test ${UUID.randomUUID()}")).also { userIds.add(it.id!!) } }
        val profile = NewClubMember("Test player", "PG", 70, null, null)
        val id = clubService.create(actors[0].id!!, "Membership verification", SportType.BASKETBALL, profile).id
        clubId = id
        val token = invites.issue(actors[0].id!!, id)
        invites.join(actors[1].id!!, token, null, profile)
        management.changeManager(actors[0].id!!, id, actors[1].id!!, true, false)
        voting.create(id, actors[1].id!!, "Test", "Gym", LocalDate.now(), Instant.now().plusSeconds(20000))
        val voteId = voting.list(id, actors[1].id!!).single().id
        voting.respond(id, voteId, actors[1].id!!, AttendanceChoice.ATTENDING)
        voting.addComment(id, voteId, actors[1].id!!, "Kept history")
        voting.close(id, voteId, actors[1].id!!)
        val playerIds = actors.map { members.findByClubIdAndUserId(id, it.id!!)!!.id!! }
        val record = matches.confirm(actors[1].id!!, id, UUID.randomUUID(), playerIds, playerIds.map { listOf(it) }, null, null, voteId)
        val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2)
        val departed = try {
            val attempts = actors.map { actor -> pool.submit<Long?> {
                start.await()
                try { management.leave(actor.id!!, id); actor.id } catch (failure: ResponseStatusException) {
                    assertEquals(HttpStatus.CONFLICT, failure.statusCode); null
                }
            } }
            start.countDown()
            attempts.mapNotNull { it.get(30, TimeUnit.SECONDS) }.single()
        } finally { pool.shutdownNow(); pool.awaitTermination(30, TimeUnit.SECONDS) }
        val remaining = actors.single { it.id != departed }.id!!
        assertEquals(1, memberships.findWithUsers(id).count { it.manager })
        assertEquals(2, matches.detail(remaining, id, record.id).teams.sumOf { it.members.size })
        assertEquals("Kept history", voting.list(id, remaining).single().comments.single().content)
        assertTrue(clubService.getMyClubs(departed).isEmpty())
        val original = members.findByClubIdAndUserId(id, departed)!!.id!!
        invites.join(departed, token, original, null)
        assertEquals(original, members.findByClubIdAndUserId(id, departed)!!.id)
        assertFalse(clubService.getMyClub(departed, id).manager)
        management.delete(remaining, id, "삭제")
        for (table in listOf("clubs", "club_members", "club_memberships", "club_invitations", "matches", "attendance_votes")) {
            val column = if (table == "clubs") "id" else "club_id"
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM $table WHERE $column = ?", Int::class.java, id))
        }
        assertTrue(actors.all { users.existsById(it.id!!) })
    }
}
