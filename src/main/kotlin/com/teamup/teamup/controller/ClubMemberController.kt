package com.teamup.teamup.controller

import com.teamup.teamup.model.service.ClubMemberService
import com.teamup.teamup.view.ClubMemberResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.net.URI

@RestController
@RequestMapping("/api/clubs/{clubId}/members")
class ClubMemberController(
    private val memberService: ClubMemberService,
) {
    @PostMapping("/validate-participants")
    fun validateParticipants(
        @AuthenticationPrincipal principal: OAuth2User?,
        @PathVariable clubId: Long,
        @Valid @RequestBody request: ValidateParticipantsRequest,
    ): List<ClubMemberResponse> = memberService.validateParticipants(currentUserId(principal), clubId, request.memberIds)

    @PatchMapping("/{memberId}/deactivate")
    fun deactivate(
        @AuthenticationPrincipal principal: OAuth2User?,
        @PathVariable clubId: Long,
        @PathVariable memberId: Long,
    ): ClubMemberResponse = memberService.deactivate(currentUserId(principal), clubId, memberId)

    @PutMapping("/{memberId}")
    fun update(
        @AuthenticationPrincipal principal: OAuth2User?,
        @PathVariable clubId: Long,
        @PathVariable memberId: Long,
        @Valid @RequestBody request: UpdateClubMemberRequest,
    ): ClubMemberResponse = memberService.update(
        currentUserId(principal), clubId, memberId,
        request.name, request.position, request.rating, request.age, request.notes,
    )

    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: OAuth2User?,
        @PathVariable clubId: Long,
        @RequestParam(required = false) active: Boolean?,
    ): List<ClubMemberResponse> = memberService.getMembers(currentUserId(principal), clubId, active)

    @PostMapping
    fun create(
        @AuthenticationPrincipal principal: OAuth2User?,
        @PathVariable clubId: Long,
        @Valid @RequestBody request: CreateClubMemberRequest,
    ): ResponseEntity<ClubMemberResponse> {
        val response = memberService.create(
            currentUserId(principal), clubId, request.name, request.position, request.rating, request.age, request.notes,
        )
        return ResponseEntity.created(URI.create("/api/clubs/$clubId/members/${response.id}")).body(response)
    }

    private fun currentUserId(principal: OAuth2User?): Long =
        principal?.name?.toLongOrNull()?.takeIf { it > 0 }
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")
}
