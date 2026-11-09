package com.teamup.teamup.model.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "matches", uniqueConstraints = [UniqueConstraint(columnNames = ["club_id", "request_id"])])
class MatchRecord(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "club_id", nullable = false, updatable = false)
    val club: Club,
    @field:Column(name = "request_id", nullable = false, length = 36)
    val requestId: String,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "sport_snapshot", nullable = false, length = 30)
    val sportSnapshot: SportType,
    @field:Column(name = "played_at", nullable = false)
    val playedAt: Instant,
    @field:Column(length = 1000)
    val memo: String?,
) {
    @field:Id @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}

@Entity
@Table(name = "match_teams", uniqueConstraints = [UniqueConstraint(columnNames = ["match_id", "team_number"])])
class MatchTeam(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "match_id", nullable = false, updatable = false)
    val match: MatchRecord,
    @field:Column(name = "team_number", nullable = false)
    val teamNumber: Int,
) {
    @field:Id @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}

@Entity
@Table(name = "match_team_members", uniqueConstraints = [UniqueConstraint(columnNames = ["match_id", "club_member_id"])])
class MatchTeamMember(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "match_id", nullable = false, updatable = false)
    val match: MatchRecord,
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "match_team_id", nullable = false, updatable = false)
    val team: MatchTeam,
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "club_member_id", nullable = false, updatable = false)
    val member: ClubMember,
    @field:Column(name = "name_snapshot", nullable = false, length = 100)
    val nameSnapshot: String,
    @field:Column(name = "position_snapshot", nullable = false, length = 50)
    val positionSnapshot: String,
    @field:Column(name = "rating_snapshot", nullable = false)
    val ratingSnapshot: Int,
)
{
    @field:Id @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
