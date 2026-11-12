package com.teamup.teamup.controller

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class CreateTeamAssignmentRequest(
    @field:NotEmpty
    val memberIds: List<Long?>,
    @field:Min(2)
    val teamCount: Int,
    @field:Size(max = 500)
    val conditionText: String? = null,
)
