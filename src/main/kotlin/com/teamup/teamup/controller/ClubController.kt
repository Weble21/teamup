package com.teamup.teamup.controller

import com.teamup.teamup.model.service.ClubService
import com.teamup.teamup.view.ClubResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.net.URI

@RestController
@RequestMapping("/api/clubs")
class ClubController(
    private val clubService: ClubService,
) {
    @GetMapping
    fun list(@AuthenticationPrincipal principal: OAuth2User?): List<ClubResponse> =
        clubService.getMyClubs(currentUserId(principal))

    @GetMapping("/{clubId}")
    fun detail(
        @AuthenticationPrincipal principal: OAuth2User?,
        @PathVariable clubId: Long,
    ): ClubResponse = clubService.getMyClub(currentUserId(principal), clubId)

    @PutMapping("/{clubId}")
    fun update(
        @AuthenticationPrincipal principal: OAuth2User?,
        @PathVariable clubId: Long,
        @Valid @RequestBody request: UpdateClubRequest,
    ): ClubResponse = clubService.update(currentUserId(principal), clubId, request.name, request.sportType)

    @PostMapping
    fun create(
        @AuthenticationPrincipal principal: OAuth2User?,
        @Valid @RequestBody request: CreateClubRequest,
    ): ResponseEntity<ClubResponse> {
        val response = clubService.create(currentUserId(principal), request.name, request.sportType)
        return ResponseEntity.created(URI.create("/api/clubs/${response.id}")).body(response)
    }

    private fun currentUserId(principal: OAuth2User?): Long =
        principal?.name?.toLongOrNull()?.takeIf { it > 0 }
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")
}
