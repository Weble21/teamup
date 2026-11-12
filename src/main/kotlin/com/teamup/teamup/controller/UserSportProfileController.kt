package com.teamup.teamup.controller

import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.service.NewClubMember
import com.teamup.teamup.model.service.UserSportProfileService
import com.teamup.teamup.view.UserSportProfileResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/users/me/sport-profiles")
class UserSportProfileController(private val profiles: UserSportProfileService) {
    @GetMapping
    fun list(@AuthenticationPrincipal principal: OAuth2User?): List<UserSportProfileResponse> =
        profiles.list(userId(principal))

    @PutMapping("/{sportType}")
    fun save(
        @AuthenticationPrincipal principal: OAuth2User?,
        @PathVariable sportType: SportType,
        @Valid @RequestBody request: SaveUserSportProfileRequest,
    ): UserSportProfileResponse = profiles.save(
        userId(principal), sportType,
        NewClubMember(request.name, request.position, request.rating, request.age, request.notes),
    )

    private fun userId(principal: OAuth2User?): Long =
        principal?.name?.toLongOrNull()?.takeIf { it > 0 }
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")
}
