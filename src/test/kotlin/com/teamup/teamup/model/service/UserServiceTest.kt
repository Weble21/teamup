package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.User
import com.teamup.teamup.model.repository.UserRepository
import com.teamup.teamup.view.UserResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UserServiceTest {
    private val repository = mock(UserRepository::class.java)
    private val service = UserService(repository)

    @Test
    fun `returns stored user as DTO including absent email`() {
        val user = User("운영자")
        ReflectionTestUtils.setField(user, "id", 7L)
        `when`(repository.findById(7)).thenReturn(Optional.of(user))
        assertEquals(UserResponse(7, "운영자", null), service.getUser(7))
    }

    @Test
    fun `missing user returns not found`() {
        `when`(repository.findById(7)).thenReturn(Optional.empty())
        val error = assertFailsWith<ResponseStatusException> { service.getUser(7) }
        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
    }
}
