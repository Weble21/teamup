package com.teamup.teamup.controller

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SaveUserSportProfileRequest(
    @field:NotBlank @field:Size(max = 100) val name: String,
    @field:NotBlank @field:Size(max = 50) val position: String,
    @field:Min(1) @field:Max(100) val rating: Int,
    @field:Min(0) @field:Max(130) val age: Int? = null,
    @field:Size(max = 1000) val notes: String? = null,
)
