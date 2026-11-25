package com.teamup.teamup.controller

import com.teamup.teamup.model.entity.AttendanceChoice
import com.teamup.teamup.model.service.AttendanceVoteService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate

data class CreateAttendanceVoteRequest(@field:NotBlank val title: String, @field:NotBlank val location: String,
    @field:NotNull val exerciseDate: LocalDate, @field:NotNull val closesAt: Instant)
data class AttendanceResponseRequest(@field:NotNull val choice: AttendanceChoice)
data class UpdateAttendanceDeadlineRequest(@field:NotNull val closesAt: Instant)

@RestController
@RequestMapping("/api/clubs/{clubId}/attendance-votes")
class AttendanceVoteController(private val service: AttendanceVoteService) {
    private fun userId(principal: OAuth2User?): Long = principal?.name?.toLongOrNull()?.takeIf { it > 0 }
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")

    @GetMapping
    fun list(@PathVariable clubId: Long, @AuthenticationPrincipal principal: OAuth2User?) = service.list(clubId, userId(principal))

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun create(@PathVariable clubId: Long, @AuthenticationPrincipal principal: OAuth2User?, @Valid @RequestBody request: CreateAttendanceVoteRequest) =
        service.create(clubId, userId(principal), request.title, request.location, request.exerciseDate, request.closesAt)

    @PutMapping("/{voteId}/response")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun respond(@PathVariable clubId: Long, @PathVariable voteId: Long, @AuthenticationPrincipal principal: OAuth2User?, @Valid @RequestBody request: AttendanceResponseRequest) =
        service.respond(clubId, voteId, userId(principal), request.choice)

    @PutMapping("/{voteId}/deadline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changeDeadline(@PathVariable clubId: Long, @PathVariable voteId: Long, @AuthenticationPrincipal principal: OAuth2User?, @Valid @RequestBody request: UpdateAttendanceDeadlineRequest) =
        service.changeDeadline(clubId, voteId, userId(principal), request.closesAt)
}
