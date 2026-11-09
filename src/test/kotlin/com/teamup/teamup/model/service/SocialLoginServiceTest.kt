package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.AuthProvider
import com.teamup.teamup.model.repository.SocialAccountRepository
import com.teamup.teamup.model.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DataJpaTest(properties = [
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:social-login-schema.sql",
])
@Import(SocialLoginService::class)
// Service calls must commit or roll back their own transactions, not join a test transaction.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SocialLoginServiceTest {
    @Autowired
    lateinit var service: SocialLoginService

    @Autowired
    lateinit var users: UserRepository

    @Autowired
    lateinit var accounts: SocialAccountRepository

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun clearDatabase() {
        accounts.deleteAllInBatch()
        users.deleteAllInBatch()
    }

    @Test
    fun `new login commits user and linked social account without email`() {
        val user = service.findOrCreateUser(AuthProvider.KAKAO, "kakao-1", "  Owner  ", null)

        assertNotNull(user.id)
        val stored = users.findById(user.id!!).orElseThrow()
        assertEquals("Owner", stored.name)
        assertNull(stored.email)
        assertNotNull(stored.createdAt)
        assertEquals(1, users.count())
        assertEquals(1, accounts.count())
        val linkedUserId = jdbc.queryForObject(
            "SELECT user_id FROM social_accounts WHERE provider = ? AND provider_user_id = ?",
            Long::class.java, "KAKAO", "kakao-1",
        )
        assertEquals(user.id, linkedUserId)
    }

    @Test
    fun `repeat login returns original user without creating or overwriting profile`() {
        val original = service.findOrCreateUser(AuthProvider.GOOGLE, "sub-1", "Original", "old@example.com")
        val repeated = service.findOrCreateUser(AuthProvider.GOOGLE, "sub-1", "Changed", "new@example.com")

        assertEquals(original.id, repeated.id)
        assertEquals(1, users.count())
        assertEquals(1, accounts.count())
        val stored = users.findById(original.id!!).orElseThrow()
        assertEquals("Original", stored.name)
        assertEquals("old@example.com", stored.email)
    }

    @Test
    fun `same email and external ID across different providers remain separate users`() {
        val google = service.findOrCreateUser(AuthProvider.GOOGLE, "same-id", "Google", "same@example.com")
        val naver = service.findOrCreateUser(AuthProvider.NAVER, "same-id", "Naver", "same@example.com")

        assertNotEquals(google.id, naver.id)
        assertEquals(2, users.count())
        assertEquals(2, accounts.count())
    }

    @Test
    fun `invalid provider IDs are rejected without persisting anything`() {
        for (id in listOf("", "  ", "a".repeat(256))) {
            assertFailsWith<IllegalArgumentException> {
                service.findOrCreateUser(AuthProvider.KAKAO, id, "Owner", null)
            }
        }
        assertEmptyDatabase()
    }

    @Test
    fun `invalid names are rejected without persisting anything`() {
        for (name in listOf("", "  ", "a".repeat(101))) {
            assertFailsWith<IllegalArgumentException> {
                service.findOrCreateUser(AuthProvider.KAKAO, "kakao-1", name, null)
            }
        }
        assertEmptyDatabase()
    }

    @Test
    fun `oversized email is rejected without persisting anything`() {
        assertFailsWith<IllegalArgumentException> {
            service.findOrCreateUser(AuthProvider.KAKAO, "kakao-1", "Owner", "a".repeat(256))
        }
        assertEmptyDatabase()
    }

    @Test
    fun `social account insert failure rolls back user insert and preserves existing data`() {
        val existing = service.findOrCreateUser(AuthProvider.GOOGLE, "existing", "Existing", null)
        // Force a real DB write failure after the new User INSERT has executed.
        jdbc.execute("ALTER TABLE social_accounts ADD CONSTRAINT test_reject_account CHECK (provider_user_id <> 'rejected')")
        try {
            assertFailsWith<DataIntegrityViolationException> {
                service.findOrCreateUser(AuthProvider.KAKAO, "rejected", "Must roll back", null)
            }
            assertEquals(1, users.count())
            assertEquals(1, accounts.count())
            assertEquals("Existing", users.findById(existing.id!!).orElseThrow().name)
            assertNull(accounts.findByProviderAndProviderUserId(AuthProvider.KAKAO, "rejected"))
        } finally {
            jdbc.execute("ALTER TABLE social_accounts DROP CONSTRAINT test_reject_account")
        }
    }

    private fun assertEmptyDatabase() {
        assertEquals(0, users.count())
        assertEquals(0, accounts.count())
    }

}
