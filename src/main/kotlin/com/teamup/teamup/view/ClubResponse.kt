package com.teamup.teamup.view

import com.teamup.teamup.model.entity.SportType

data class ClubResponse(
    val id: Long,
    val name: String,
    val sportType: SportType,
    val ownerId: Long,
)
