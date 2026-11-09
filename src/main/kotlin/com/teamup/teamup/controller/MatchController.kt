package com.teamup.teamup.controller

import com.teamup.teamup.model.service.MatchService
import com.teamup.teamup.view.MatchPageResponse
import com.teamup.teamup.view.MatchResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.net.URI

@RestController
@RequestMapping("/api/clubs/{clubId}/matches")
class MatchController(private val service: MatchService) {
    private fun userId(principal: OAuth2User?): Long = principal?.name?.toLongOrNull()?.takeIf { it > 0 }
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")

    @PostMapping
    fun confirm(@AuthenticationPrincipal principal: OAuth2User?, @PathVariable clubId: Long, @Valid @RequestBody request: CreateMatchRequest): ResponseEntity<MatchResponse> {
        val result = service.confirm(userId(principal), clubId, request.requestId, request.memberIds, request.teams, request.playedAt, request.memo)
        return ResponseEntity.created(URI.create("/api/clubs/$clubId/matches/${result.id}")).body(result)
    }

    @GetMapping
    fun list(@AuthenticationPrincipal principal: OAuth2User?, @PathVariable clubId: Long,
             @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int): MatchPageResponse =
        service.list(userId(principal), clubId, page, size)

    @GetMapping("/{matchId}")
    fun detail(@AuthenticationPrincipal principal: OAuth2User?, @PathVariable clubId: Long, @PathVariable matchId: Long): MatchResponse =
        service.detail(userId(principal), clubId, matchId)
}
