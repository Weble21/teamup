package com.teamup.teamup.controller

import jakarta.validation.constraints.NotEmpty

data class ValidateParticipantsRequest(
    @field:NotEmpty
    val memberIds: List<Long?>,
)
