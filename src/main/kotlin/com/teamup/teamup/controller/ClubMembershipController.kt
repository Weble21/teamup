package com.teamup.teamup.controller

import com.teamup.teamup.model.service.ClubMembershipManagementService
import com.teamup.teamup.model.service.MembershipView
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

data class ManagerRequest(val manager: Boolean, val transfer: Boolean = false)
data class DeleteClubRequest(val confirmation: String)

@RestController
@RequestMapping("/api/clubs/{clubId}")
class ClubMembershipController(private val service: ClubMembershipManagementService) {
    @GetMapping("/memberships")
    fun list(@AuthenticationPrincipal principal: OAuth2User?, @PathVariable clubId: Long): List<MembershipView> =
        service.list(userId(principal), clubId)

    @PutMapping("/managers/{targetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun manager(@AuthenticationPrincipal principal: OAuth2User?, @PathVariable clubId: Long,
                @PathVariable targetId: Long, @RequestBody request: ManagerRequest) =
        service.changeManager(userId(principal), clubId, targetId, request.manager, request.transfer)

    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leave(@AuthenticationPrincipal principal: OAuth2User?, @PathVariable clubId: Long) = service.leave(userId(principal), clubId)

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal principal: OAuth2User?, @PathVariable clubId: Long, @RequestBody request: DeleteClubRequest) =
        service.delete(userId(principal), clubId, request.confirmation)

    private fun userId(principal: OAuth2User?): Long = principal?.name?.toLongOrNull()?.takeIf { it > 0 }
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")
}
