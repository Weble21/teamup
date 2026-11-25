package com.teamup.teamup.view

import com.teamup.teamup.model.entity.SportType
import java.time.Instant
import java.time.LocalDate

data class MatchResponse(
    val id: Long, val clubId: Long, val sportType: SportType,
    val playedAt: Instant, val memo: String?, val teams: List<AssignedTeamResponse>,
)

data class MatchScheduleResponse(val id: Long, val title: String, val exerciseDate: LocalDate, val location: String, val closesAt: Instant)

data class MatchSummaryResponse(val id: Long, val sportType: SportType, val playedAt: Instant, val memo: String?,
                               val attendanceVote: MatchScheduleResponse? = null)
data class MatchPageResponse(val matches: List<MatchSummaryResponse>, val page: Int, val hasNext: Boolean)
