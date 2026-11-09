package com.teamup.teamup.controller

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateMatchRequest(
    val requestId: UUID,
    @field:NotEmpty val memberIds: List<Long?>,
    @field:NotEmpty val teams: List<List<Long?>?>,
    val playedAt: Instant? = null,
    @field:Size(max = 1000) val memo: String? = null,
)
