package com.teamup.teamup.view

import com.teamup.teamup.model.entity.SportType

data class UserSportProfileResponse(
    val sportType: SportType,
    val name: String,
    val position: String,
    val rating: Int,
    val age: Int?,
    val notes: String?,
)
