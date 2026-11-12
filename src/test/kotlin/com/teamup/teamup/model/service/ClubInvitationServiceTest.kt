package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.Club
import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.entity.User
import com.teamup.teamup.model.repository.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

@DataJpaTest(properties = ["spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:social-login-schema.sql"])
@Import(ClubInvitationService::class, ClubMemberService::class, ClubService::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClubInvitationServiceTest {
    @Autowired lateinit var invites: ClubInvitationService
    @Autowired lateinit var profiles: ClubMemberService
    @Autowired lateinit var clubService: ClubService
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var clubs: ClubRepository
    @Autowired lateinit var members: ClubMemberRepository
    @Autowired lateinit var memberships: ClubMembershipRepository
    @Autowired lateinit var invitations: ClubInvitationRepository

    @AfterEach
    fun clean() {
        members.deleteAllInBatch(); memberships.deleteAllInBatch(); invitations.deleteAllInBatch()
        clubs.deleteAllInBatch(); users.deleteAllInBatch()
    }

    @Test
    fun `invitation can be reused and joining twice is idempotent`() {
        val owner = users.save(User("Owner")); val first = users.save(User("First")); val second = users.save(User("Second"))
        val club = clubs.save(Club(owner, "Club", SportType.FUTSAL)); val clubId = club.id!!
        val token = invites.issue(owner.id!!, clubId)
        assertEquals(43, token.length)
        assertNotEquals(token, invitations.findByClubId(clubId)!!.tokenHash)
        assertEquals(clubId, invites.join(first.id!!, token))
        invites.join(first.id!!, token); invites.join(second.id!!, token); invites.join(owner.id!!, token)
        assertEquals(2, memberships.count())
        assertEquals(listOf(clubId), clubService.getMyClubs(first.id!!).map { it.id })
        assertEquals(owner.id, clubService.getMyClub(first.id!!, clubId).ownerId)
    }

    @Test
    fun `rotation and revoke invalidate links without removing joined users`() {
        val owner = users.save(User("Owner")); val user = users.save(User("Member")); val club = clubs.save(Club(owner, "Club", SportType.FUTSAL))
        val first = invites.issue(owner.id!!, club.id!!); invites.join(user.id!!, first)
        val second = invites.issue(owner.id!!, club.id!!)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { invites.join(user.id!!, first) }.statusCode)
        invites.revoke(owner.id!!, club.id!!)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { invites.join(user.id!!, second) }.statusCode)
        assertEquals(1, memberships.count())
    }

    @Test
    fun `concurrent joins by same account create one membership`() {
        val owner = users.save(User("Owner")); val user = users.save(User("Member")); val club = clubs.save(Club(owner, "Club", SportType.FUTSAL))
        val token = invites.issue(owner.id!!, club.id!!)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val calls = (1..2).map { pool.submit<Long> { start.await(); invites.join(user.id!!, token) } }
            start.countDown()
            calls.forEach { assertEquals(club.id, it.get(10, TimeUnit.SECONDS)) }
            assertEquals(1, memberships.count())
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `invited users can read roster but only edit their own profile`() {
        val owner = users.save(User("Owner")); val a = users.save(User("A")); val b = users.save(User("B")); val outsider = users.save(User("Other"))
        val club = clubs.save(Club(owner, "Club", SportType.FUTSAL)); val clubId = club.id!!
        val token = invites.issue(owner.id!!, clubId); invites.join(a.id!!, token); invites.join(b.id!!, token)
        val input = NewClubMember("A", "PG", 60, 23, "private")
        val aProfile = profiles.saveMyProfile(a.id!!, clubId, input)
        val bProfile = profiles.saveMyProfile(b.id!!, clubId, input.copy(name = "B"))
        assertEquals(aProfile.id, profiles.saveMyProfile(a.id!!, clubId, input.copy(name = "New A")).id)
        val visible = profiles.getMembers(a.id!!, clubId)
        assertEquals(setOf("New A", "B"), visible.map { it.name }.toSet())
        assertTrue(visible.single { it.id == aProfile.id }.mine)
        assertEquals(23, visible.single { it.id == aProfile.id }.age)
        assertFalse(visible.single { it.id == bProfile.id }.mine)
        assertNull(visible.single { it.id == bProfile.id }.age)
        assertNull(visible.single { it.id == bProfile.id }.notes)
        assertEquals(2, profiles.getMembers(owner.id!!, clubId).size)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { profiles.update(a.id!!, clubId, bProfile.id, "Hack", "PG", 60, null, null) }.statusCode)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { invites.issue(a.id!!, clubId) }.statusCode)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { invites.revoke(a.id!!, clubId) }.statusCode)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { clubService.update(a.id!!, clubId, "Changed", SportType.OTHER) }.statusCode)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { profiles.validateParticipants(a.id!!, clubId, listOf(aProfile.id, bProfile.id)) }.statusCode)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { profiles.saveMyProfile(outsider.id!!, clubId, input) }.statusCode)
        profiles.deactivate(owner.id!!, clubId, aProfile.id)
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> { profiles.saveMyProfile(a.id!!, clubId, input) }.statusCode)
    }
}
