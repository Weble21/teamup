package com.teamup.teamup.controller

import com.teamup.teamup.model.entity.SportType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateClubRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
    val sportType: SportType,
)
