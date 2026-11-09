package com.teamup.teamup.view

import com.teamup.teamup.model.entity.SportType
import java.time.Instant

data class MatchResponse(
    val id: Long, val clubId: Long, val sportType: SportType,
    val playedAt: Instant, val memo: String?, val teams: List<AssignedTeamResponse>,
)

data class MatchSummaryResponse(val id: Long, val sportType: SportType, val playedAt: Instant, val memo: String?)
data class MatchPageResponse(val matches: List<MatchSummaryResponse>, val page: Int, val hasNext: Boolean)
