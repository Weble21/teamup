package com.teamup.teamup.controller

import com.teamup.teamup.model.service.UserService
import com.teamup.teamup.view.UserResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
) {
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: OAuth2User?): UserResponse {
        val userId = principal?.name?.toLongOrNull()?.takeIf { it > 0 }
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")
        return userService.getUser(userId)
    }
}
