package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.Club
import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.entity.ClubMember
import com.teamup.teamup.model.entity.ClubMembership
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

    private fun player(name: String): User = users.save(User(name))
    @AfterEach
    fun clean() {
        members.deleteAllInBatch(); memberships.deleteAllInBatch(); invitations.deleteAllInBatch()
        clubs.deleteAllInBatch(); users.deleteAllInBatch()
    }

    @Test
    fun `selecting an existing player preserves details and rejects reuse by another account`() {
        val owner = users.save(User("Owner")); val first = users.save(User("First")); val second = users.save(User("Second"))
        val club = clubs.save(Club(owner, "Club", SportType.FUTSAL))
        val original = members.saveAndFlush(ClubMember(club, "Player", "GK", 73, 27, "notes"))
        val token = invites.issue(owner.id!!, club.id!!)
        assertEquals(listOf(original.id), invites.preview(first.id!!, token).candidates.map { it.id })
        invites.join(first.id!!, token, original.id!!, null)
        val linked = members.findByClubIdAndUserId(club.id!!, first.id!!)!!
        assertEquals(original.id, linked.id); assertEquals(73, linked.rating); assertEquals("notes", linked.notes)
        assertTrue(invites.preview(second.id!!, token).candidates.isEmpty())
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> { invites.join(second.id!!, token, original.id!!, null) }.statusCode)
        assertFalse(memberships.existsByClubIdAndUserId(club.id!!, second.id!!))
    }

    @Test
    fun `concurrent claims of one player link exactly one account`() {
        val owner = users.save(User("Owner")); val first = users.save(User("First")); val second = users.save(User("Second"))
        val club = clubs.save(Club(owner, "Club", SportType.FUTSAL))
        val player = members.saveAndFlush(ClubMember(club, "Player", "FW", 60))
        val token = invites.issue(owner.id!!, club.id!!)
        val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2)
        try {
            val attempts = listOf(first, second).map { user -> pool.submit<Boolean> {
                start.await()
                try { invites.join(user.id!!, token, player.id!!, null); true }
                catch (failure: ResponseStatusException) { assertEquals(HttpStatus.CONFLICT, failure.statusCode); false }
            } }
            start.countDown()
            assertEquals(1, attempts.count { it.get(10, TimeUnit.SECONDS) })
            assertEquals(1, memberships.count()); assertEquals(1, members.count())
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `joining rejects invalid selection and has no partial membership`() {
        val owner = users.save(User("Owner")); val outsider = users.save(User("Outsider"))
        val club = clubs.save(Club(owner, "Club", SportType.FUTSAL))
        val other = clubs.save(Club(owner, "Other", SportType.FUTSAL))
        val foreign = members.saveAndFlush(ClubMember(other, "Other", "FW", 50))
        val inactive = members.saveAndFlush(ClubMember(club, "Inactive", "FW", 50).also { it.deactivate() })
        val token = invites.issue(owner.id!!, club.id!!)
        assertEquals(HttpStatus.BAD_REQUEST, assertFailsWith<ResponseStatusException> { invites.join(outsider.id!!, token, null, null) }.statusCode)
        for (id in listOf(foreign.id!!, inactive.id!!)) assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> { invites.join(outsider.id!!, token, id, null) }.statusCode)
        assertFalse(memberships.existsByClubIdAndUserId(club.id!!, outsider.id!!))
    }

    @Test
    fun `existing membership without sport profile bypasses registration without creating a player`() {
        val owner = users.save(User("Owner")); val member = users.save(User("Legacy member"))
        val club = clubs.save(Club(owner, "Legacy membership", SportType.FUTSAL))
        memberships.saveAndFlush(ClubMembership(club, member))
        val token = invites.issue(owner.id!!, club.id!!)
        val preview = invites.preview(member.id!!, token)
        assertTrue(preview.alreadyJoined); assertTrue(preview.candidates.isEmpty())
        assertTrue(invites.join(member.id!!, token, null, NewClubMember("Player", "FW", 60, 23, "profile note")).alreadyJoined)
        assertEquals(1, memberships.count()); assertEquals(0, members.count())
    }

    @Test
    fun `accept and revoke concurrently complete without partial roster or stale token acceptance`() {
        val owner = users.save(User("Owner")); val member = player("Concurrent")
        val clubId = clubs.save(Club(owner, "Race", SportType.FUTSAL)).id!!
        val token = invites.issue(owner.id!!, clubId)
        val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2)
        try {
            val accept = pool.submit<Boolean> {
                start.await()
                try { invites.join(member.id!!, token, null, NewClubMember("Player", "FW", 60, 23, "profile note")); true }
                catch (failure: ResponseStatusException) { assertEquals(HttpStatus.NOT_FOUND, failure.statusCode); false }
            }
            val revoke = pool.submit { start.await(); invites.revoke(owner.id!!, clubId) }
            start.countDown()
            val accepted = accept.get(10, TimeUnit.SECONDS); revoke.get(10, TimeUnit.SECONDS)
            assertEquals(if (accepted) 1L else 0L, memberships.count())
            assertEquals(memberships.count(), members.count())
            assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { invites.preview(member.id!!, token) }.statusCode)
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `preview checks membership before profile registration and does not join`() {
        val owner = users.save(User("Owner")); val newcomer = users.save(User("New"))
        val clubId = clubs.save(Club(owner, "Onboarding", SportType.FUTSAL)).id!!
        val token = invites.issue(owner.id!!, clubId)
        val preview = invites.preview(newcomer.id!!, token)
        assertEquals(clubId, preview.clubId)
        assertEquals("Onboarding", preview.clubName)
        assertEquals(SportType.FUTSAL, preview.sportType)
        assertFalse(preview.alreadyJoined); assertTrue(preview.candidates.isEmpty())
        assertEquals(0, memberships.count()); assertEquals(0, members.count())
        assertTrue(invites.preview(owner.id!!, token).alreadyJoined)
        assertTrue(invites.preview(owner.id!!, token).candidates.isEmpty())
        assertTrue(invites.join(owner.id!!, token, null, NewClubMember("Player", "FW", 60, 23, "profile note")).alreadyJoined)
        assertEquals(0, memberships.count()); assertEquals(0, members.count())
    }

    @Test
    fun `owner retrieves committed token across requests until rotation or revoke`() {
        val owner = users.save(User("Owner"))
        val clubId = clubs.save(Club(owner, "Persistent", SportType.FUTSAL)).id!!
        assertNull(invites.getCurrent(owner.id!!, clubId).token)
        val first = invites.issue(owner.id!!, clubId)
        // Each call uses its own committed transaction, as after login on another device.
        repeat(2) { assertEquals(first, invites.getCurrent(owner.id!!, clubId).token) }
        assertEquals(1, invitations.count())
        val second = invites.issue(owner.id!!, clubId)
        assertNotEquals(first, second)
        assertEquals(second, invites.getCurrent(owner.id!!, clubId).token)
        invites.revoke(owner.id!!, clubId)
        val revoked = invites.getCurrent(owner.id!!, clubId)
        assertNull(revoked.token)
        assertFalse(revoked.requiresReissue)
        assertEquals(0, invitations.count())
    }

    @Test
    fun `legacy invitation remains valid and retrieval never silently rotates it`() {
        val owner = users.save(User("Owner")); val member = player("Member")
        val clubId = clubs.save(Club(owner, "Legacy", SportType.FUTSAL)).id!!
        val token = invites.issue(owner.id!!, clubId)
        val stored = invitations.findByClubId(clubId)!!
        stored.token = null
        invitations.saveAndFlush(stored)
        val legacy = invites.getCurrent(owner.id!!, clubId)
        assertNull(legacy.token)
        assertTrue(legacy.requiresReissue)
        assertEquals(clubId, invites.join(member.id!!, token, null, NewClubMember("Player", "FW", 60, 23, "profile note")).clubId)
        invites.issue(owner.id!!, clubId)
        assertFalse(invites.getCurrent(owner.id!!, clubId).requiresReissue)
    }

    @Test
    fun `members outsiders and owners of other clubs cannot retrieve invitation secrets`() {
        val owner = users.save(User("Owner")); val member = player("Member"); val other = users.save(User("Other"))
        val clubId = clubs.save(Club(owner, "Private", SportType.FUTSAL)).id!!
        val token = invites.issue(owner.id!!, clubId)
        invites.join(member.id!!, token, null, NewClubMember("Player", "FW", 60, 23, "profile note"))
        for (id in listOf(member.id!!, other.id!!)) {
            assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { invites.getCurrent(id, clubId) }.statusCode)
        }
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { invites.getCurrent(owner.id!!, Long.MAX_VALUE) }.statusCode)
        assertEquals(token, invites.getCurrent(owner.id!!, clubId).token)
    }

    @Test
    fun `invitation can be reused and joining twice is idempotent`() {
        val owner = users.save(User("Owner")); val first = player("First"); val second = player("Second")
        val club = clubs.save(Club(owner, "Club", SportType.FUTSAL)); val clubId = club.id!!
        val token = invites.issue(owner.id!!, clubId)
        assertEquals(43, token.length)
        assertNotEquals(token, invitations.findByClubId(clubId)!!.tokenHash)
        assertEquals(clubId, invites.join(first.id!!, token, null, NewClubMember("Player", "FW", 60, 23, "profile note")).clubId)
        invites.join(first.id!!, token, null, NewClubMember("Player", "FW", 60, 23, "profile note")); invites.join(second.id!!, token, null, NewClubMember("Player", "FW", 60, 23, "profile note")); invites.join(owner.id!!, token, null, NewClubMember("Player", "FW", 60, 23, "profile note"))
        assertEquals(2, memberships.count())
        assertEquals(listOf(clubId), clubService.getMyClubs(first.id!!).map { it.id })
        assertEquals(owner.id, clubService.getMyClub(first.id!!, clubId).ownerId)
    }

    @Test
    fun `rotation and revoke invalidate links without removing joined users`() {
        val owner = users.save(User("Owner")); val user = player("Member"); val club = clubs.save(Club(owner, "Club", SportType.FUTSAL))
        val first = invites.issue(owner.id!!, club.id!!); invites.join(user.id!!, first, null, NewClubMember("Player", "FW", 60, 23, "profile note"))
        val second = invites.issue(owner.id!!, club.id!!)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { invites.join(user.id!!, first, null, NewClubMember("Player", "FW", 60, 23, "profile note")) }.statusCode)
        invites.revoke(owner.id!!, club.id!!)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { invites.join(user.id!!, second, null, NewClubMember("Player", "FW", 60, 23, "profile note")) }.statusCode)
        assertEquals(1, memberships.count())
    }

    @Test
    fun `concurrent joins by same account create one membership`() {
        val owner = users.save(User("Owner")); val user = player("Member"); val club = clubs.save(Club(owner, "Club", SportType.FUTSAL))
        val token = invites.issue(owner.id!!, club.id!!)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val calls = (1..2).map { pool.submit<Long> { start.await(); invites.join(user.id!!, token, null, NewClubMember("Player", "FW", 60, 23, "profile note")).clubId } }
            start.countDown()
            calls.forEach { assertEquals(club.id, it.get(10, TimeUnit.SECONDS)) }
            assertEquals(1, memberships.count())
            assertEquals(1, members.count())
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `invited users can read roster but only edit their own profile`() {
        val owner = users.save(User("Owner")); val a = player("A"); val b = player("B"); val outsider = users.save(User("Other"))
        val club = clubs.save(Club(owner, "Club", SportType.FUTSAL)); val clubId = club.id!!
        val token = invites.issue(owner.id!!, clubId); invites.join(a.id!!, token, null, NewClubMember("Player", "FW", 60, 23, "profile note")); invites.join(b.id!!, token, null, NewClubMember("Player", "FW", 60, 23, "profile note"))
        val input = NewClubMember("A", "PG", 60, 23, "private")
        val aProfile = profiles.saveMyProfile(a.id!!, clubId, input)
        val bProfile = profiles.saveMyProfile(b.id!!, clubId, input.copy(name = "B"))
        assertEquals(aProfile.id, profiles.saveMyProfile(a.id!!, clubId, input.copy(name = "New A")).id)
        val visible = profiles.getMembers(a.id!!, clubId)
        assertEquals(setOf("New A", "B"), visible.map { it.name }.toSet())
        assertTrue(visible.single { it.id == aProfile.id }.mine)
        assertEquals(23, visible.single { it.id == aProfile.id }.age)
        assertFalse(visible.single { it.id == bProfile.id }.mine)
        assertEquals(23, visible.single { it.id == bProfile.id }.age)
        assertEquals("private", visible.single { it.id == bProfile.id }.notes)
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
