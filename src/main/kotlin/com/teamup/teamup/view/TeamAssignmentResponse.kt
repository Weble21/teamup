package com.teamup.teamup.view

import com.teamup.teamup.model.team.TeamParticipant
import java.math.BigDecimal

data class TeamAssignmentResponse(
    val clubId: Long,
    val teams: List<AssignedTeamResponse>,
)

data class AssignedTeamResponse(
    val teamNumber: Int,
    val members: List<TeamParticipant>,
    val totalRating: Long,
    val averageRating: BigDecimal,
)
