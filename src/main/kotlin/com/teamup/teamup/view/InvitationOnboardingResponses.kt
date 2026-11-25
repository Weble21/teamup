package com.teamup.teamup.view

import com.teamup.teamup.model.entity.SportType

data class InvitationPreviewResponse(
    val clubId: Long,
    val clubName: String,
    val sportType: SportType,
    val alreadyJoined: Boolean,
    val candidates: List<InvitationCandidate>,
)

data class InvitationCandidate(val id: Long, val name: String, val position: String)
data class InvitationJoinResponse(val clubId: Long, val alreadyJoined: Boolean)
