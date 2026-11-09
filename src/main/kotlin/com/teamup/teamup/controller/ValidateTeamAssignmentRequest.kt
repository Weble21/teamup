package com.teamup.teamup.controller

import jakarta.validation.constraints.NotEmpty

data class ValidateTeamAssignmentRequest(
    @field:NotEmpty val memberIds: List<Long?>,
    @field:NotEmpty val teams: List<List<Long?>?>,
)

data class SwapTeamMembersRequest(
    @field:NotEmpty val memberIds: List<Long?>,
    @field:NotEmpty val teams: List<List<Long?>?>,
    val firstMemberId: Long,
    val secondMemberId: Long,
)
