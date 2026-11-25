package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.entity.User
import com.teamup.teamup.model.repository.ClubRepository
import com.teamup.teamup.model.repository.ClubMemberRepository
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

@DataJpaTest(properties = [
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:social-login-schema.sql",
])
@Import(ClubService::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClubServiceTest {
    @Autowired
    lateinit var service: ClubService

    @Autowired
    lateinit var clubs: ClubRepository

    @Autowired
    lateinit var members: ClubMemberRepository

    private val ownerProfile = NewClubMember("Captain", "PG", 70, 25, "Captain note")

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
    fun `creating a club creates one active owner-linked player with the entered profile`() {
        val owner = users.save(User("Account"))
        val created = service.create(owner.id!!, "Club", SportType.BASKETBALL, ownerProfile)
        val player = members.findByClubIdAndUserId(created.id, owner.id!!)!!
        assertEquals("Captain", player.name)
        assertEquals("PG", player.position)
        assertEquals(70, player.rating)
        assertEquals(25, player.age)
        assertEquals("Captain note", player.notes)
        assertEquals(true, player.active)
        assertEquals(1, created.activeMemberCount)
    }

    @Test
    fun `invalid owner profile rolls back club creation`() {
        val owner = users.save(User("Account"))
        assertFailsWith<IllegalArgumentException> {
            service.create(owner.id!!, "Club", SportType.BASKETBALL, ownerProfile.copy(position = " "))
        }
        assertEquals(0, clubs.count())
        assertEquals(0, members.count())
    }

    @Test
    fun `owner updates name and sport using dirty checking and keeps owner`() {
        val owner = users.save(User("Owner"))
        val created = service.create(owner.id!!, "Original", SportType.BASKETBALL, ownerProfile)
        val updated = service.update(owner.id!!, created.id, "  Updated  ", SportType.FUTSAL)

        assertEquals(created.id, updated.id)
        assertEquals(owner.id, updated.ownerId)
        assertEquals("Updated", updated.name)
        assertEquals(SportType.FUTSAL, updated.sportType)
        assertEquals(updated, service.getMyClub(owner.id!!, created.id))
        assertEquals(1, clubs.count())
    }

    @Test
    fun `another owner cannot update club and original data is preserved`() {
        val owner = users.save(User("Owner"))
        val other = users.save(User("Other"))
        val created = service.create(owner.id!!, "Original", SportType.BASKETBALL, ownerProfile)
        val error = assertFailsWith<ResponseStatusException> {
            service.update(other.id!!, created.id, "Changed", SportType.FUTSAL)
        }
        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        assertEquals(created, service.getMyClub(owner.id!!, created.id))
    }

    @Test
    fun `updating missing club returns 404`() {
        val owner = users.save(User("Owner"))
        val error = assertFailsWith<ResponseStatusException> {
            service.update(owner.id!!, Long.MAX_VALUE, "Updated", SportType.FUTSAL)
        }
        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        assertEquals(0, clubs.count())
    }

    @Test
    fun `invalid update leaves both name and sport unchanged`() {
        val owner = users.save(User("Owner"))
        val created = service.create(owner.id!!, "Original", SportType.BASKETBALL, ownerProfile)
        for (name in listOf("  ", "a".repeat(101))) {
            assertFailsWith<IllegalArgumentException> {
                service.update(owner.id!!, created.id, name, SportType.FUTSAL)
            }
            assertEquals(created, service.getMyClub(owner.id!!, created.id))
        }
    }

    @Test
    fun `owner can retrieve club details`() {
        val owner = users.save(User("Owner"))
        val created = service.create(owner.id!!, "Weekend", SportType.BASKETBALL, ownerProfile)
        assertEquals(created, service.getMyClub(owner.id!!, created.id))
    }

    @Test
    fun `other users club and missing club return identical not found errors`() {
        val owner = users.save(User("Owner"))
        val other = users.save(User("Other"))
        val created = service.create(owner.id!!, "Private club", SportType.BASKETBALL, ownerProfile)

        val forbidden = assertFailsWith<ResponseStatusException> {
            service.getMyClub(other.id!!, created.id)
        }
        val missing = assertFailsWith<ResponseStatusException> {
            service.getMyClub(other.id!!, Long.MAX_VALUE)
        }
        assertEquals(HttpStatus.NOT_FOUND, forbidden.statusCode)
        assertEquals(forbidden.statusCode, missing.statusCode)
        assertEquals(forbidden.reason, missing.reason)
    }

    @Test
    fun `missing user cannot retrieve existing club`() {
        val owner = users.save(User("Owner"))
        val created = service.create(owner.id!!, "Weekend", SportType.BASKETBALL, ownerProfile)
        val error = assertFailsWith<ResponseStatusException> {
            service.getMyClub(Long.MAX_VALUE, created.id)
        }
        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
    }

    @Test
    fun `list includes only owned clubs in descending creation ID order`() {
        val owner = users.save(User("Owner"))
        val other = users.save(User("Other"))
        val first = service.create(owner.id!!, "First", SportType.BASKETBALL, ownerProfile)
        service.create(other.id!!, "Other club", SportType.FUTSAL, ownerProfile)
        val last = service.create(owner.id!!, "Last", SportType.VOLLEYBALL, ownerProfile)

        assertEquals(listOf(last, first), service.getMyClubs(owner.id!!))
        assertEquals(listOf("Other club"), service.getMyClubs(other.id!!).map { it.name })
    }

    @Test
    fun `existing user with no clubs receives empty list even when others have clubs`() {
        val owner = users.save(User("Owner"))
        val other = users.save(User("Other"))
        service.create(other.id!!, "Other club", SportType.BASKETBALL, ownerProfile)

        assertEquals(emptyList(), service.getMyClubs(owner.id!!))
    }

    @Test
    fun `list for missing user returns 404`() {
        val error = assertFailsWith<ResponseStatusException> { service.getMyClubs(Long.MAX_VALUE) }
        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
    }

    @Test
    fun `commits club with trimmed name and correct owner foreign key`() {
        val owner = users.save(User("Owner"))
        val response = service.create(owner.id!!, "  주말 농구  ", SportType.BASKETBALL, ownerProfile)

        assertEquals("주말 농구", response.name)
        assertEquals(owner.id, response.ownerId)
        assertEquals(SportType.BASKETBALL, response.sportType)
        val stored = clubs.findById(response.id).orElseThrow()
        assertEquals(response.name, stored.name)
        assertEquals(response.sportType, stored.sportType)
        assertEquals(owner.id, jdbc.queryForObject("SELECT owner_id FROM clubs WHERE id = ?", Long::class.java, response.id))
        assertEquals(1, clubs.count())
    }

    @Test
    fun `missing owner returns 404 and creates no club`() {
        val error = assertFailsWith<ResponseStatusException> {
            service.create(Long.MAX_VALUE, "Weekend", SportType.BASKETBALL, ownerProfile)
        }
        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        assertEquals(0, clubs.count())
    }

    @Test
    fun `domain rejects invalid names without creating club`() {
        val owner = users.save(User("Owner"))
        for (name in listOf("  ", "a".repeat(101))) {
            assertFailsWith<IllegalArgumentException> {
                service.create(owner.id!!, name, SportType.BASKETBALL, ownerProfile)
            }
        }
        assertEquals(0, clubs.count())
    }

    @Test
    fun `name at maximum length is accepted`() {
        val owner = users.save(User("Owner"))
        val response = service.create(owner.id!!, "a".repeat(100), SportType.OTHER, ownerProfile)
        assertEquals(100, clubs.findById(response.id).orElseThrow().name.length)
    }
}
