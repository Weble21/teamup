package com.teamup.teamup.controller

import com.teamup.teamup.model.service.TeamAssignmentService
import com.teamup.teamup.model.service.TeamAnalysisService
import com.teamup.teamup.view.TeamAssignmentResponse
import com.teamup.teamup.view.TeamAnalysisResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/clubs/{clubId}/team-assignments")
class TeamAssignmentController(
    private val assignmentService: TeamAssignmentService,
    private val analysisService: TeamAnalysisService,
) {
    @PostMapping("/analyze")
    fun analyze(
        @AuthenticationPrincipal principal: OAuth2User?, @PathVariable clubId: Long,
        @Valid @RequestBody request: ValidateTeamAssignmentRequest,
    ): TeamAnalysisResponse = analysisService.analyze(userId(principal), clubId, request.memberIds, request.teams)

    @PostMapping("/validate")
    fun validate(
        @AuthenticationPrincipal principal: OAuth2User?, @PathVariable clubId: Long,
        @Valid @RequestBody request: ValidateTeamAssignmentRequest,
    ): TeamAssignmentResponse = assignmentService.validate(userId(principal), clubId, request.memberIds, request.teams)

    @PostMapping("/swap")
    fun swap(
        @AuthenticationPrincipal principal: OAuth2User?, @PathVariable clubId: Long,
        @Valid @RequestBody request: SwapTeamMembersRequest,
    ): TeamAssignmentResponse = assignmentService.swap(
        userId(principal), clubId, request.memberIds, request.teams, request.firstMemberId, request.secondMemberId, request.conditionText,
    )

    private fun userId(principal: OAuth2User?): Long = principal?.name?.toLongOrNull()?.takeIf { it > 0 }
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")

    @PostMapping
    fun generate(
        @AuthenticationPrincipal principal: OAuth2User?,
        @PathVariable clubId: Long,
        @Valid @RequestBody request: CreateTeamAssignmentRequest,
    ): TeamAssignmentResponse {
        val ownerId = principal?.name?.toLongOrNull()?.takeIf { it > 0 }
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")
        return assignmentService.generate(ownerId, clubId, request.memberIds, request.teamCount, request.conditionText)
    }
}
