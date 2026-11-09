package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.Club
import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.entity.User
import com.teamup.teamup.model.repository.ClubMemberRepository
import com.teamup.teamup.model.repository.ClubRepository
import com.teamup.teamup.model.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.RoundingMode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DataJpaTest(properties = [
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:social-login-schema.sql",
])
@Import(TeamAssignmentService::class, ClubMemberService::class, TeamBalancer::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TeamAssignmentServiceTest {
    @Autowired lateinit var service: TeamAssignmentService
    @Autowired lateinit var memberService: ClubMemberService
    @Autowired lateinit var members: ClubMemberRepository
    @Autowired lateinit var clubs: ClubRepository
    @Autowired lateinit var users: UserRepository

    @BeforeEach
    fun clearDatabase() {
        members.deleteAllInBatch()
        clubs.deleteAllInBatch()
        users.deleteAllInBatch()
    }

    @Test
    fun `generates uneven teams with accurate summaries and no member mutations`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val players = (1..5).map {
            memberService.create(owner.id!!, club.id!!, "Player $it", "PG", it * 15, null, null)
        }
        val before = memberService.getMembers(owner.id!!, club.id!!)
        val result = service.generate(owner.id!!, club.id!!, players.map { it.id }, 2)
        assertEquals(club.id, result.clubId)
        assertEquals(listOf(1, 2), result.teams.map { it.teamNumber })
        assertEquals(listOf(2, 3), result.teams.map { it.members.size }.sorted())
        assertEquals(players.map { it.id }.sorted(), result.teams.flatMap { it.members }.map { it.memberId }.sorted())
        for (team in result.teams) {
            val total = team.members.sumOf { it.rating.toLong() }
            assertEquals(total, team.totalRating)
            assertEquals(total.toBigDecimal().divide(team.members.size.toBigDecimal(), 2, RoundingMode.HALF_UP), team.averageRating)
        }
        assertEquals(before, memberService.getMembers(owner.id!!, club.id!!))
    }

    @Test
    fun `generation revalidates participants and ownership instead of trusting previous selection`() {
        val owner = users.save(User("Owner"))
        val other = users.save(User("Other"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val foreignClub = clubs.save(Club(owner, "Other", SportType.BASKETBALL))
        val first = memberService.create(owner.id!!, club.id!!, "First", "PG", 70, null, null)
        val second = memberService.create(owner.id!!, club.id!!, "Second", "C", 80, null, null)
        val foreign = memberService.create(owner.id!!, foreignClub.id!!, "Foreign", "PG", 90, null, null)
        memberService.validateParticipants(owner.id!!, club.id!!, listOf(first.id, second.id))
        memberService.deactivate(owner.id!!, club.id!!, second.id)
        for (ids in listOf(listOf(first.id, second.id), listOf(first.id, foreign.id), listOf(first.id, first.id), listOf(first.id, Long.MAX_VALUE))) {
            val error = assertFailsWith<ResponseStatusException> { service.generate(owner.id!!, club.id!!, ids, 2) }
            assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        }
        val error = assertFailsWith<ResponseStatusException> { service.generate(other.id!!, club.id!!, listOf(first.id), 2) }
        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
    }

    @Test
    fun `invalid team count is rejected without writes`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val player = memberService.create(owner.id!!, club.id!!, "Player", "PG", 70, null, null)
        for (count in listOf(0, 1, 2, Int.MAX_VALUE)) {
            val error = assertFailsWith<ResponseStatusException> { service.generate(owner.id!!, club.id!!, listOf(player.id), count) }
            assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        }
        assertEquals(1, members.count())
    }

    @Test
    fun `generation uses current ratings from database`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val first = memberService.create(owner.id!!, club.id!!, "First", "PG", 70, null, null)
        val second = memberService.create(owner.id!!, club.id!!, "Second", "C", 80, null, null)
        memberService.update(owner.id!!, club.id!!, first.id, "Changed", "SF", 95, null, null)
        val result = service.generate(owner.id!!, club.id!!, listOf(first.id, second.id), 2)
        assertTrue(result.teams.flatMap { it.members }.any { it.memberId == first.id && it.rating == 95 && it.name == "Changed" })
    }
}
