package com.teamup.teamup.controller

import com.teamup.teamup.model.entity.SportType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull

data class CreateClubRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
    val sportType: SportType,
    @field:Valid
    @field:NotNull
    val ownerProfile: CreateClubMemberRequest,
)
