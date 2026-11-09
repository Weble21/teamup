package com.teamup.teamup.model.repository

import com.teamup.teamup.model.entity.MatchRecord
import com.teamup.teamup.model.entity.MatchTeam
import com.teamup.teamup.model.entity.MatchTeamMember
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface MatchRepository : JpaRepository<MatchRecord, Long> {
    fun findByClubIdAndRequestId(clubId: Long, requestId: String): MatchRecord?
    fun findByIdAndClubId(id: Long, clubId: Long): MatchRecord?
    fun findAllByClubIdOrderByPlayedAtDescIdDesc(clubId: Long, pageable: Pageable): Slice<MatchRecord>
}

interface MatchTeamRepository : JpaRepository<MatchTeam, Long>

interface MatchTeamMemberRepository : JpaRepository<MatchTeamMember, Long> {
    @EntityGraph(attributePaths = ["team"])
    fun findAllByMatchIdOrderByTeamTeamNumberAscIdAsc(matchId: Long): List<MatchTeamMember>
}
