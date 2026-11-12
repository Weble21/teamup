package com.teamup.teamup.view

data class TeamAnalysisResponse(val teams: List<TeamInsightResponse>)

data class TeamInsightResponse(
    val teamNumber: Int,
    val keyPlayerId: Long,
    val keyPlayerReason: String,
    val strength: String,
)
