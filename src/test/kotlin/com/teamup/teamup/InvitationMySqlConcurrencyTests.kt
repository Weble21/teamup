package com.teamup.teamup

import com.teamup.teamup.model.entity.*
import com.teamup.teamup.model.repository.*
import com.teamup.teamup.model.service.ClubInvitationService
import com.teamup.teamup.model.service.NewClubMember
import com.teamup.teamup.view.InvitationJoinResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

// Explicit Docker/local opt-in. Cleanup is restricted to IDs created by this test.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "TEAMUP_VERIFY_INVITATION_MYSQL", matches = "true")
class InvitationMySqlConcurrencyTests {
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var clubs: ClubRepository
    @Autowired lateinit var invitations: ClubInvitationService
    @Autowired lateinit var jdbc: JdbcTemplate
    private val userIds = mutableListOf<Long>()
    private var clubId: Long? = null

    @AfterEach
    fun cleanup() {
        clubId?.let { id ->
            for (table in listOf("club_members", "club_memberships", "club_invitations")) {
                jdbc.update("DELETE FROM $table WHERE club_id = ?", id)
            }
            jdbc.update("DELETE FROM clubs WHERE id = ?", id)
        }
        for (id in userIds) {
            jdbc.update("DELETE FROM users WHERE id = ?", id)
        }
    }

    @Test
    fun `MySQL concurrent accept creates exactly one membership and one linked player`() {
        assertEquals("MySQL", jdbc.dataSource!!.connection.use { it.metaData.databaseProductName })
        val owner = users.save(User("Invitation concurrency test owner")).also { userIds.add(it.id!!) }
        val member = users.save(User("Invitation concurrency test player")).also { userIds.add(it.id!!) }
        val club = clubs.save(Club(owner, "Invitation concurrency test", SportType.FUTSAL))
        clubId = club.id!!
        val token = invitations.issue(owner.id!!, club.id!!)
        val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = (1..4).map {
                pool.submit<InvitationJoinResponse> { start.await(); invitations.join(member.id!!, token, null, NewClubMember("Test player", "FW", 70, null, null)) }
            }
            start.countDown()
            val results = futures.map { it.get(15, TimeUnit.SECONDS) }
            assertTrue(results.all { it.clubId == club.id })
            assertEquals(1, results.count { !it.alreadyJoined })
            assertEquals(3, results.count { it.alreadyJoined })
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM club_memberships WHERE club_id = ?", Long::class.java, club.id))
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM club_members WHERE club_id = ? AND user_id = ?", Long::class.java, club.id, member.id))
        } finally { pool.shutdownNow(); pool.awaitTermination(15, TimeUnit.SECONDS) }
    }
}
