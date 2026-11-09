package com.teamup.teamup.view

data class ClubMemberResponse(
    val id: Long,
    val clubId: Long,
    val name: String,
    val position: String,
    val rating: Int,
    val age: Int?,
    val notes: String?,
    val active: Boolean,
)
