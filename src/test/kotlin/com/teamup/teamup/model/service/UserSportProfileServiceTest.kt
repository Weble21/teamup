package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.entity.User
import com.teamup.teamup.model.repository.UserRepository
import com.teamup.teamup.model.repository.UserSportProfileRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DataJpaTest(properties = ["spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:social-login-schema.sql"])
@Import(UserSportProfileService::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserSportProfileServiceTest {
    @Autowired lateinit var service: UserSportProfileService
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var profiles: UserSportProfileRepository

    @AfterEach
    fun clean() {
        profiles.deleteAllInBatch()
        users.deleteAllInBatch()
    }

    @Test
    fun `profiles are isolated by user and sport and an update keeps one row`() {
        val alice = users.save(User("Alice")).id!!
        val bob = users.save(User("Bob")).id!!
        val basketball = NewClubMember("Basket Alice", "Guard", 70, 24, "fast")
        service.save(alice, SportType.BASKETBALL, basketball)
        service.save(alice, SportType.FUTSAL, NewClubMember("Futsal Alice", "Keeper", 40, null, null))
        service.save(bob, SportType.BASKETBALL, NewClubMember("Basket Bob", "Center", 90, 30, null))

        assertEquals(2, service.list(alice).size)
        assertEquals("Basket Bob", service.list(bob).single().name)
        val updated = service.save(alice, SportType.BASKETBALL, basketball.copy(position = "Forward", age = null))
        assertEquals("Forward", updated.position)
        assertNull(updated.age)
        assertEquals(3, profiles.count())
        assertEquals("Keeper", service.list(alice).single { it.sportType == SportType.FUTSAL }.position)
    }
}
