package com.teamup.teamup.model.team

data class TeamConstraints(
    val together: List<Pair<Long, Long>> = emptyList(),
    val apart: List<Pair<Long, Long>> = emptyList(),
    val balancePositions: Boolean = false,
    val descriptions: List<String> = emptyList(),
)
