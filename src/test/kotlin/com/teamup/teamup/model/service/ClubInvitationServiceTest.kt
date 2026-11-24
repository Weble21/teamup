package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.Club
import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.entity.UserSportProfile
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
    @Autowired lateinit var sportProfiles: UserSportProfileRepository

    private fun player(name: String): User = users.save(User(name)).also {
        sportProfiles.save(UserSportProfile(it, SportType.FUTSAL, name, "FW", 60, 23, "profile note"))
    }
    @AfterEach
    fun clean() {
        members.deleteAllInBatch(); memberships.deleteAllInBatch(); invitations.deleteAllInBatch()
        clubs.deleteAllInBatch(); sportProfiles.deleteAllInBatch(); users.deleteAllInBatch()
    }

    @Test
    fun `existing membership without sport profile bypasses registration without creating a player`() {
        val owner = users.save(User("Owner")); val member = users.save(User("Legacy member"))
        val club = clubs.save(Club(owner, "Legacy membership", SportType.FUTSAL))
        memberships.saveAndFlush(ClubMembership(club, member))
        val token = invites.issue(owner.id!!, club.id!!)
        val preview = invites.preview(member.id!!, token)
        assertTrue(preview.alreadyJoined); assertFalse(preview.profileRegistered)
        assertTrue(invites.join(member.id!!, token).alreadyJoined)
        assertEquals(1, memberships.count()); assertEquals(0, members.count())
    }

    @Test
    fun `same name unlinked player is not claimed during automatic registration`() {
        val owner = users.save(User("Owner")); val member = player("Same Name")
        val club = clubs.save(Club(owner, "Names", SportType.FUTSAL))
        val original = members.saveAndFlush(ClubMember(club, "Same Name", "GK", 10))
        val token = invites.issue(owner.id!!, club.id!!)
        invites.join(member.id!!, token)
        assertEquals(2, members.count())
        val roster = members.findAllByClubIdOrderByIdDesc(club.id!!)
        assertNull(roster.single { it.id == original.id }.user)
        assertEquals(10, roster.single { it.id == original.id }.rating)
        assertEquals(60, members.findByClubIdAndUserId(club.id!!, member.id!!)!!.rating)
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
                try { invites.join(member.id!!, token); true }
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
        assertFalse(preview.alreadyJoined); assertFalse(preview.profileRegistered)
        assertEquals(0, memberships.count()); assertEquals(0, members.count())
        assertTrue(invites.preview(owner.id!!, token).alreadyJoined)
        assertFalse(invites.preview(owner.id!!, token).profileRegistered)
        assertTrue(invites.join(owner.id!!, token).alreadyJoined)
        assertEquals(0, memberships.count()); assertEquals(0, members.count())
    }

    @Test
    fun `missing or different sport profile prevents any partial membership or roster write`() {
        val owner = users.save(User("Owner")); val newcomer = users.save(User("New"))
        val clubId = clubs.save(Club(owner, "Onboarding", SportType.FUTSAL)).id!!
        val token = invites.issue(owner.id!!, clubId)
        sportProfiles.save(UserSportProfile(newcomer, SportType.BASKETBALL, "Other sport", "C", 50, null, null))
        assertFalse(invites.preview(newcomer.id!!, token).profileRegistered)
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> { invites.join(newcomer.id!!, token) }.statusCode)
        assertEquals(0, memberships.count()); assertEquals(0, members.count())
    }

    @Test
    fun `acceptance copies matching profile once and repeat acceptance never overwrites it`() {
        val owner = users.save(User("Owner")); val newcomer = player("New")
        val clubId = clubs.save(Club(owner, "Onboarding", SportType.FUTSAL)).id!!
        val token = invites.issue(owner.id!!, clubId)
        assertTrue(invites.preview(newcomer.id!!, token).profileRegistered)
        assertEquals(0, memberships.count()); assertEquals(0, members.count())
        assertFalse(invites.join(newcomer.id!!, token).alreadyJoined)
        val member = members.findByClubIdAndUserId(clubId, newcomer.id!!)!!
        assertEquals("New", member.name); assertEquals("FW", member.position); assertEquals(60, member.rating)
        assertEquals(23, member.age); assertEquals("profile note", member.notes)
        val profile = sportProfiles.findByUserIdAndSportType(newcomer.id!!, SportType.FUTSAL)!!
        profile.update("Changed", "GK", 90, null, null); sportProfiles.saveAndFlush(profile)
        assertTrue(invites.preview(newcomer.id!!, token).alreadyJoined)
        assertTrue(invites.join(newcomer.id!!, token).alreadyJoined)
        assertEquals("New", members.findByClubIdAndUserId(clubId, newcomer.id!!)!!.name)
        assertEquals(1, memberships.count()); assertEquals(1, members.count())
    }

    @Test
    fun `existing linked inactive player and unlinked same name player remain untouched`() {
        val owner = users.save(User("Owner")); val newcomer = player("New")
        val club = clubs.save(Club(owner, "Onboarding", SportType.FUTSAL)); val clubId = club.id!!
        val linked = ClubMember(club, "Old", "GK", 10, null, null, newcomer).also { it.deactivate() }
        members.saveAndFlush(linked)
        members.saveAndFlush(ClubMember(club, "New", "FW", 50))
        val token = invites.issue(owner.id!!, clubId)
        invites.join(newcomer.id!!, token)
        val saved = members.findByClubIdAndUserId(clubId, newcomer.id!!)!!
        assertEquals("Old", saved.name); assertFalse(saved.active)
        assertEquals(2, members.count())
        assertNull(members.findAllByClubIdOrderByIdDesc(clubId).single { it.name == "New" }.user)
    }

    @Test
    fun `club sport changed after preview is checked again at acceptance`() {
        val owner = users.save(User("Owner")); val newcomer = player("New")
        val clubId = clubs.save(Club(owner, "Change sport", SportType.FUTSAL)).id!!
        val token = invites.issue(owner.id!!, clubId)
        assertTrue(invites.preview(newcomer.id!!, token).profileRegistered)
        clubService.update(owner.id!!, clubId, "Change sport", SportType.BASKETBALL)
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> { invites.join(newcomer.id!!, token) }.statusCode)
        assertFalse(invites.preview(newcomer.id!!, token).profileRegistered)
        assertEquals(SportType.BASKETBALL, invites.preview(newcomer.id!!, token).sportType)
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
        assertEquals(clubId, invites.join(member.id!!, token).clubId)
        invites.issue(owner.id!!, clubId)
        assertFalse(invites.getCurrent(owner.id!!, clubId).requiresReissue)
    }

    @Test
    fun `members outsiders and owners of other clubs cannot retrieve invitation secrets`() {
        val owner = users.save(User("Owner")); val member = player("Member"); val other = users.save(User("Other"))
        val clubId = clubs.save(Club(owner, "Private", SportType.FUTSAL)).id!!
        val token = invites.issue(owner.id!!, clubId)
        invites.join(member.id!!, token)
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
        assertEquals(clubId, invites.join(first.id!!, token).clubId)
        invites.join(first.id!!, token); invites.join(second.id!!, token); invites.join(owner.id!!, token)
        assertEquals(2, memberships.count())
        assertEquals(listOf(clubId), clubService.getMyClubs(first.id!!).map { it.id })
        assertEquals(owner.id, clubService.getMyClub(first.id!!, clubId).ownerId)
    }

    @Test
    fun `rotation and revoke invalidate links without removing joined users`() {
        val owner = users.save(User("Owner")); val user = player("Member"); val club = clubs.save(Club(owner, "Club", SportType.FUTSAL))
        val first = invites.issue(owner.id!!, club.id!!); invites.join(user.id!!, first)
        val second = invites.issue(owner.id!!, club.id!!)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { invites.join(user.id!!, first) }.statusCode)
        invites.revoke(owner.id!!, club.id!!)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { invites.join(user.id!!, second) }.statusCode)
        assertEquals(1, memberships.count())
    }

    @Test
    fun `concurrent joins by same account create one membership`() {
        val owner = users.save(User("Owner")); val user = player("Member"); val club = clubs.save(Club(owner, "Club", SportType.FUTSAL))
        val token = invites.issue(owner.id!!, club.id!!)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val calls = (1..2).map { pool.submit<Long> { start.await(); invites.join(user.id!!, token).clubId } }
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
