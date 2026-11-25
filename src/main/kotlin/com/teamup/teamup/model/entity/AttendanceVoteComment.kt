package com.teamup.teamup.model.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "attendance_vote_comments")
class AttendanceVoteComment(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "vote_id", nullable = false) val vote: AttendanceVote,
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false) val author: User,
    @field:Column(nullable = false, length = 500) val content: String,
    @field:Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
) {
    @field:Id @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
