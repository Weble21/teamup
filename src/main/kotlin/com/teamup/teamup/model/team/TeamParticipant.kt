package com.teamup.teamup.model.team

data class TeamParticipant(
    val memberId: Long,
    val name: String,
    val position: String,
    val rating: Int,
)
