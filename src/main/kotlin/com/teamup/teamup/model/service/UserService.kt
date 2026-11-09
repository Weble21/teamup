package com.teamup.teamup.model.service

import com.teamup.teamup.model.repository.UserRepository
import com.teamup.teamup.view.UserResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserService(
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun getUser(userId: Long): UserResponse {
        val user = userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        return UserResponse(checkNotNull(user.id), user.name, user.email)
    }
}
