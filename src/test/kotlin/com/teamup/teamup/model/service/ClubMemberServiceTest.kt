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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataJpaTest(properties = [
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:social-login-schema.sql",
])
@Import(ClubMemberService::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClubMemberServiceTest {
    @Autowired
    lateinit var service: ClubMemberService

    @Autowired
    lateinit var members: ClubMemberRepository

    @Autowired
    lateinit var clubs: ClubRepository

    @Autowired
    lateinit var users: UserRepository

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun clearDatabase() {
        members.deleteAllInBatch()
        clubs.deleteAllInBatch()
        users.deleteAllInBatch()
    }

    @Test
    fun `bulk registration rolls back earlier members when later member is invalid`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val entries = listOf(NewClubMember("First", "PG", 50, null, null), NewClubMember("Invalid", "C", 101, null, null))
        assertFailsWith<IllegalArgumentException> { service.createBulk(owner.id!!, club.id!!, entries) }
        assertEquals(0, members.count())
    }

    @Test
    fun `bulk registration rejects another owners club`() {
        val owner = users.save(User("Owner"))
        val outsider = users.save(User("Other"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val error = assertFailsWith<ResponseStatusException> {
            service.createBulk(outsider.id!!, club.id!!, listOf(NewClubMember("Player", "PG", 50, null, null)))
        }
        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        assertEquals(0, members.count())
    }

    @Test
    fun `active filter returns only matching state from requested club`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val otherClub = clubs.save(Club(owner, "Other", SportType.BASKETBALL))
        val active = service.create(owner.id!!, club.id!!, "Active", "PG", 70, null, null)
        val inactive = service.create(owner.id!!, club.id!!, "Inactive", "C", 80, null, null)
        service.deactivate(owner.id!!, club.id!!, inactive.id)
        service.create(owner.id!!, otherClub.id!!, "Other", "PG", 90, null, null)

        assertEquals(listOf(active), service.getMembers(owner.id!!, club.id!!, true))
        assertEquals(listOf(inactive.copy(active = false)), service.getMembers(owner.id!!, club.id!!, false))
        assertEquals(2, service.getMembers(owner.id!!, club.id!!).size)
    }

    @Test
    fun `participant validation preserves requested order and returns current DB profile`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val first = service.create(owner.id!!, club.id!!, "Same name", "PG", 70, null, null)
        val last = service.create(owner.id!!, club.id!!, "Same name", "C", 80, null, null)
        val updated = service.update(owner.id!!, club.id!!, first.id, "Updated", "SF", 95, null, null)

        assertEquals(listOf(last, updated), service.validateParticipants(owner.id!!, club.id!!, listOf(last.id, first.id)))
    }

    @Test
    fun `invalid participant selections are rejected as a whole without writes`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val otherClub = clubs.save(Club(owner, "Other", SportType.BASKETBALL))
        val active = service.create(owner.id!!, club.id!!, "Active", "PG", 70, null, null)
        val inactive = service.create(owner.id!!, club.id!!, "Inactive", "C", 80, null, null)
        val foreign = service.create(owner.id!!, otherClub.id!!, "Other", "PG", 90, null, null)
        service.deactivate(owner.id!!, club.id!!, inactive.id)
        val invalidSelections = listOf(
            emptyList(), listOf(active.id, active.id), listOf(active.id, inactive.id),
            listOf(active.id, foreign.id), listOf(active.id, Long.MAX_VALUE),
            listOf(active.id, 0L), listOf(active.id, -1L), listOf(active.id, null),
        )
        for (selection in invalidSelections) {
            val error = assertFailsWith<ResponseStatusException> {
                service.validateParticipants(owner.id!!, club.id!!, selection)
            }
            assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        }
        assertEquals(3, members.count())
        assertEquals(listOf(active), service.getMembers(owner.id!!, club.id!!, true))
    }

    @Test
    fun `active list and participant validation reject another owner and missing club`() {
        val owner = users.save(User("Owner"))
        val other = users.save(User("Other"))
        val club = clubs.save(Club(owner, "Private", SportType.BASKETBALL))
        val member = service.create(owner.id!!, club.id!!, "Player", "PG", 70, null, null)
        for (clubId in listOf(club.id!!, Long.MAX_VALUE)) {
            val listError = assertFailsWith<ResponseStatusException> { service.getMembers(other.id!!, clubId, true) }
            val selectionError = assertFailsWith<ResponseStatusException> {
                service.validateParticipants(other.id!!, clubId, listOf(member.id))
            }
            assertEquals(HttpStatus.NOT_FOUND, listError.statusCode)
            assertEquals(HttpStatus.NOT_FOUND, selectionError.statusCode)
        }
    }

    @Test
    fun `owner deactivates member while preserving row and profile`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val member = service.create(owner.id!!, club.id!!, "Player", "PG", 70, 25, "Notes")
        val result = service.deactivate(owner.id!!, club.id!!, member.id)

        assertEquals(member.copy(active = false), result)
        assertEquals(listOf(result), service.getMembers(owner.id!!, club.id!!))
        assertEquals(1, members.count())
    }

    @Test
    fun `repeated deactivation succeeds without changing profile or row count`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val member = service.create(owner.id!!, club.id!!, "Player", "PG", 70, null, null)
        val first = service.deactivate(owner.id!!, club.id!!, member.id)
        val second = service.deactivate(owner.id!!, club.id!!, member.id)
        assertEquals(first, second)
        assertEquals(listOf(second), service.getMembers(owner.id!!, club.id!!))
        assertEquals(1, members.count())
    }

    @Test
    fun `another owner cannot deactivate member`() {
        val owner = users.save(User("Owner"))
        val other = users.save(User("Other"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val member = service.create(owner.id!!, club.id!!, "Player", "PG", 70, null, null)
        val error = assertFailsWith<ResponseStatusException> {
            service.deactivate(other.id!!, club.id!!, member.id)
        }
        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        assertEquals(listOf(member), service.getMembers(owner.id!!, club.id!!))
    }

    @Test
    fun `deactivation rejects wrong club missing club and missing member`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val otherClub = clubs.save(Club(owner, "Other", SportType.BASKETBALL))
        val member = service.create(owner.id!!, club.id!!, "Player", "PG", 70, null, null)
        val invalidTargets = listOf(
            otherClub.id!! to member.id,
            Long.MAX_VALUE to member.id,
            club.id!! to Long.MAX_VALUE,
        )
        for ((clubId, memberId) in invalidTargets) {
            val error = assertFailsWith<ResponseStatusException> {
                service.deactivate(owner.id!!, clubId, memberId)
            }
            assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        }
        assertEquals(listOf(member), service.getMembers(owner.id!!, club.id!!))
    }

    @Test
    fun `owner updates member fields and commits through dirty checking`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val member = service.create(owner.id!!, club.id!!, "Original", "PG", 70, null, null)
        val updated = service.update(owner.id!!, club.id!!, member.id, " Updated ", " C ", 90, 25, " Strong ")

        assertEquals(member.id, updated.id)
        assertEquals(club.id, updated.clubId)
        assertEquals("Updated", updated.name)
        assertEquals("C", updated.position)
        assertEquals(90, updated.rating)
        assertEquals(25, updated.age)
        assertEquals("Strong", updated.notes)
        assertEquals(listOf(updated), service.getMembers(owner.id!!, club.id!!))
        assertEquals(1, members.count())
    }

    @Test
    fun `optional fields can be cleared without reactivating member`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val member = service.create(owner.id!!, club.id!!, "Player", "PG", 70, 25, "Old notes")
        jdbc.update("UPDATE club_members SET active = FALSE WHERE id = ?", member.id)
        val updated = service.update(owner.id!!, club.id!!, member.id, "Player", "PG", 70, null, " ")
        assertEquals(member.copy(age = null, notes = null, active = false), updated)
        assertEquals(listOf(updated), service.getMembers(owner.id!!, club.id!!))
    }

    @Test
    fun `another owner cannot update member`() {
        val owner = users.save(User("Owner"))
        val other = users.save(User("Other"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val member = service.create(owner.id!!, club.id!!, "Player", "PG", 70, null, null)
        val error = assertFailsWith<ResponseStatusException> {
            service.update(other.id!!, club.id!!, member.id, "Changed", "C", 90, null, null)
        }
        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        assertEquals(listOf(member), service.getMembers(owner.id!!, club.id!!))
    }

    @Test
    fun `member from another owned club or missing member cannot be updated`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val otherClub = clubs.save(Club(owner, "Other", SportType.BASKETBALL))
        val member = service.create(owner.id!!, otherClub.id!!, "Player", "PG", 70, null, null)
        for (memberId in listOf(member.id, Long.MAX_VALUE)) {
            val error = assertFailsWith<ResponseStatusException> {
                service.update(owner.id!!, club.id!!, memberId, "Changed", "C", 90, null, null)
            }
            assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        }
        assertEquals(listOf(member), service.getMembers(owner.id!!, otherClub.id!!))
        assertEquals(emptyList(), service.getMembers(owner.id!!, club.id!!))
    }

    @Test
    fun `invalid updates preserve all existing fields`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val member = service.create(owner.id!!, club.id!!, "Player", "PG", 70, 25, "Original notes")
        val invalidAttempts = listOf<() -> Unit>(
            { service.update(owner.id!!, club.id!!, member.id, " ", "C", 90, null, null) },
            { service.update(owner.id!!, club.id!!, member.id, "Changed", " ", 90, null, null) },
            { service.update(owner.id!!, club.id!!, member.id, "Changed", "C", 101, null, null) },
            { service.update(owner.id!!, club.id!!, member.id, "Changed", "C", 90, -1, null) },
            { service.update(owner.id!!, club.id!!, member.id, "Changed", "C", 90, null, "a".repeat(1001)) },
        )
        for (attempt in invalidAttempts) {
            assertFailsWith<IllegalArgumentException> { attempt() }
            assertEquals(listOf(member), service.getMembers(owner.id!!, club.id!!))
        }
    }

    @Test
    fun `list returns only requested clubs members newest first including inactive members`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val otherClub = clubs.save(Club(owner, "Other", SportType.BASKETBALL))
        val first = service.create(owner.id!!, club.id!!, "First", "PG", 70, null, null)
        service.create(owner.id!!, otherClub.id!!, "Other player", "C", 80, null, null)
        val last = service.create(owner.id!!, club.id!!, "Last", "SF", 90, 25, "Fast")
        jdbc.update("UPDATE club_members SET active = FALSE WHERE id = ?", first.id)

        assertEquals(listOf(last, first.copy(active = false)), service.getMembers(owner.id!!, club.id!!))
    }

    @Test
    fun `empty club returns empty list even if another club has members`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Empty", SportType.BASKETBALL))
        val otherClub = clubs.save(Club(owner, "Other", SportType.BASKETBALL))
        service.create(owner.id!!, otherClub.id!!, "Other player", "PG", 70, null, null)

        assertEquals(emptyList(), service.getMembers(owner.id!!, club.id!!))
    }

    @Test
    fun `list of another owners club or missing club returns identical 404 errors`() {
        val owner = users.save(User("Owner"))
        val other = users.save(User("Other"))
        val club = clubs.save(Club(owner, "Private", SportType.BASKETBALL))
        service.create(owner.id!!, club.id!!, "Private player", "PG", 70, null, null)

        val forbidden = assertFailsWith<ResponseStatusException> {
            service.getMembers(other.id!!, club.id!!)
        }
        val missing = assertFailsWith<ResponseStatusException> {
            service.getMembers(other.id!!, Long.MAX_VALUE)
        }
        assertEquals(HttpStatus.NOT_FOUND, forbidden.statusCode)
        assertEquals(forbidden.statusCode, missing.statusCode)
        assertEquals(forbidden.reason, missing.reason)
    }

    @Test
    fun `owner registers active member with trimmed text and correct club reference`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val result = service.create(owner.id!!, club.id!!, "  Player  ", " PG ", 70, 25, "  Fast  ")
        val stored = members.findById(result.id).orElseThrow()
        assertEquals("Player", stored.name)
        assertEquals("PG", stored.position)
        assertEquals(70, stored.rating)
        assertEquals(25, stored.age)
        assertEquals("Fast", stored.notes)
        assertTrue(stored.active)
        assertEquals(club.id, jdbc.queryForObject("SELECT club_id FROM club_members WHERE id = ?", Long::class.java, result.id))
    }

    @Test
    fun `same name members are allowed and optional values remain absent`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val first = service.create(owner.id!!, club.id!!, "Player", "PG", 1, null, null)
        val second = service.create(owner.id!!, club.id!!, "Player", "C", 100, null, "  ")
        assertNotEquals(first.id, second.id)
        assertNull(first.age)
        assertNull(first.notes)
        assertNull(second.notes)
        assertEquals(2, members.count())
    }

    @Test
    fun `another owner and missing club are rejected without inserting member`() {
        val owner = users.save(User("Owner"))
        val other = users.save(User("Other"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        for (clubId in listOf(club.id!!, Long.MAX_VALUE)) {
            val error = assertFailsWith<ResponseStatusException> {
                service.create(other.id!!, clubId, "Player", "PG", 70, null, null)
            }
            assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        }
        assertEquals(0, members.count())
    }

    @Test
    fun `domain validation prevents invalid members from being committed`() {
        val owner = users.save(User("Owner"))
        val club = clubs.save(Club(owner, "Weekend", SportType.BASKETBALL))
        val invalidAttempts = listOf<() -> Unit>(
            { service.create(owner.id!!, club.id!!, " ", "PG", 70, null, null) },
            { service.create(owner.id!!, club.id!!, "Player", " ", 70, null, null) },
            { service.create(owner.id!!, club.id!!, "Player", "PG", 0, null, null) },
            { service.create(owner.id!!, club.id!!, "Player", "PG", 101, null, null) },
            { service.create(owner.id!!, club.id!!, "Player", "PG", 70, -1, null) },
            { service.create(owner.id!!, club.id!!, "Player", "PG", 70, null, "a".repeat(1001)) },
        )
        for (attempt in invalidAttempts) {
            assertFailsWith<IllegalArgumentException> { attempt() }
        }
        assertEquals(0, members.count())
    }
}
