package com.teamup.teamup.model.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "attendance_votes")
class AttendanceVote(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "club_id", nullable = false)
    val club: Club,
    @field:Column(nullable = false, length = 150) val title: String,
    @field:Column(nullable = false, length = 100) val location: String,
    @field:Column(name = "exercise_date", nullable = false) val exerciseDate: LocalDate,
    closesAt: Instant,
) {
    @field:Id @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @field:Column(name = "closes_at", nullable = false)
    var closesAt: Instant = closesAt
        protected set

    fun changeDeadline(closesAt: Instant) { this.closesAt = closesAt }
}

enum class AttendanceChoice { ATTENDING, ABSENT, UNDECIDED }

@Entity
@Table(name = "attendance_responses")
class AttendanceResponse(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "vote_id", nullable = false) val vote: AttendanceVote,
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false) val user: User,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20) var choice: AttendanceChoice,
) {
    @field:Id @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
