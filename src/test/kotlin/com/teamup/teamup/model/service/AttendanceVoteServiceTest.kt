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
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { service.list(clubId, outsider.id!!) }.statusCode)
        assertEquals(HttpStatus.FORBIDDEN, assertFailsWith<ResponseStatusException> {
            service.create(clubId, member.id!!, "title", "place", LocalDate.now(), Instant.now().plusSeconds(18000))
        }.statusCode)
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
    fun `owner can change deadline and reopen a closed vote without losing responses`() {
        val owner = users.save(User("owner"))
        val club = clubs.save(Club(owner, "club", SportType.BASKETBALL))
        val vote = votes.save(AttendanceVote(club, "title", "place", LocalDate.now(), Instant.now().plusSeconds(18000)))
        service.respond(club.id!!, vote.id!!, owner.id!!, AttendanceChoice.ATTENDING)
        val newDeadline = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        service.changeDeadline(club.id!!, vote.id!!, owner.id!!, newDeadline)
        assertEquals(newDeadline, service.list(club.id!!, owner.id!!).single().closesAt)
        assertEquals(AttendanceChoice.ATTENDING, service.list(club.id!!, owner.id!!).single().myChoice)
        val closed = votes.save(AttendanceVote(club, "closed", "place", LocalDate.now(), Instant.now().minusSeconds(1)))
        service.changeDeadline(club.id!!, closed.id!!, owner.id!!, newDeadline)
        service.respond(club.id!!, closed.id!!, owner.id!!, AttendanceChoice.UNDECIDED)
        assertEquals(AttendanceChoice.UNDECIDED, service.list(club.id!!, owner.id!!).first { it.id == closed.id }.myChoice)
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
}
