package com.teamup.teamup.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.Size

data class CreateClubMembersRequest(
    @field:Size(min = 1, max = 100)
    @field:Valid
    val members: List<@Valid CreateClubMemberRequest>,
)
