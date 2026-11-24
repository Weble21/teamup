package com.teamup.teamup.controller

import com.teamup.teamup.model.service.ClubInvitationService
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

data class JoinInvitationRequest(@field:Pattern(regexp = "[A-Za-z0-9_-]{43}") val token: String)
data class InvitationResponse(val token: String)
@RestController
@RequestMapping("/api")
class ClubInvitationController(private val service: ClubInvitationService) {
    @GetMapping("/clubs/{clubId}/invitation")
    fun current(@AuthenticationPrincipal principal: OAuth2User?, @PathVariable clubId: Long) =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getCurrent(userId(principal), clubId))

    @PostMapping("/clubs/{clubId}/invitation")
    fun issue(@AuthenticationPrincipal principal: OAuth2User?, @PathVariable clubId: Long) =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(InvitationResponse(service.issue(userId(principal), clubId)))

    @DeleteMapping("/clubs/{clubId}/invitation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(@AuthenticationPrincipal principal: OAuth2User?, @PathVariable clubId: Long) =
        service.revoke(userId(principal), clubId)

    @PostMapping("/invitations/preview")
    fun preview(@AuthenticationPrincipal principal: OAuth2User?, @Valid @RequestBody request: JoinInvitationRequest) =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preview(userId(principal), request.token))

    @PostMapping("/invitations/join")
    fun join(@AuthenticationPrincipal principal: OAuth2User?, @Valid @RequestBody request: JoinInvitationRequest) =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.join(userId(principal), request.token))

    private fun userId(principal: OAuth2User?): Long = principal?.name?.toLongOrNull()?.takeIf { it > 0 }
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")
}
