package com.teamup.teamup

import com.teamup.teamup.model.entity.AttendanceVote
import com.teamup.teamup.model.entity.Club
import com.teamup.teamup.model.entity.ClubMember
import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.entity.User
import com.teamup.teamup.model.repository.AttendanceVoteRepository
import com.teamup.teamup.model.repository.ClubMemberRepository
import com.teamup.teamup.model.repository.ClubRepository
import com.teamup.teamup.model.repository.UserRepository
import com.teamup.teamup.model.service.MatchService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

// Explicit opt-in; deletes only records created for this test.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "TEAMUP_VERIFY_ATTENDANCE_MYSQL", matches = "true")
class AttendanceMatchMySqlConcurrencyTests {
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var clubs: ClubRepository
    @Autowired lateinit var members: ClubMemberRepository
    @Autowired lateinit var votes: AttendanceVoteRepository
    @Autowired lateinit var matches: MatchService
    @Autowired lateinit var jdbc: JdbcTemplate
    private var ownerId: Long? = null
    private var clubId: Long? = null

    @AfterEach
    fun cleanup() {
        clubId?.let { id ->
            jdbc.update("DELETE FROM match_team_members WHERE match_id IN (SELECT id FROM matches WHERE club_id = ?)", id)
            jdbc.update("DELETE FROM match_teams WHERE match_id IN (SELECT id FROM matches WHERE club_id = ?)", id)
            jdbc.update("DELETE FROM matches WHERE club_id = ?", id)
            jdbc.update("DELETE FROM attendance_votes WHERE club_id = ?", id)
            jdbc.update("DELETE FROM club_members WHERE club_id = ?", id)
            jdbc.update("DELETE FROM clubs WHERE id = ?", id)
        }
        ownerId?.let { jdbc.update("DELETE FROM users WHERE id = ?", it) }
    }

    @Test
    fun `mysql concurrent confirmations for one vote produce one match`() {
        assertEquals("MySQL", jdbc.dataSource!!.connection.use { it.metaData.databaseProductName })
        val owner = users.save(User("Attendance match concurrency test")); ownerId = owner.id!!
        val club = clubs.save(Club(owner, "Attendance match concurrency test", SportType.BASKETBALL)); clubId = club.id!!
        val ids = (1..4).map { members.save(ClubMember(club, "Player $it", "PG", it * 20)).id!! }
        val vote = votes.save(AttendanceVote(club, "Test vote", "Test place", LocalDate.now(), Instant.now().minusSeconds(10)))
        val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2)
        try {
            val results = (1..2).map { pool.submit<Long> {
                start.await(); matches.confirm(owner.id!!, club.id!!, UUID.randomUUID(), ids, listOf(ids.take(2), ids.drop(2)), null, null, vote.id!!).id
            } }
            start.countDown()
            assertEquals(1, results.map { it.get(30, TimeUnit.SECONDS) }.distinct().size)
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM matches WHERE attendance_vote_id = ?", Long::class.java, vote.id))
        } finally { pool.shutdownNow(); pool.awaitTermination(30, TimeUnit.SECONDS) }
    }
}
