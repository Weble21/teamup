package com.teamup.teamup.model.entity

import jakarta.persistence.*

@Entity
@Table(name = "club_memberships")
class ClubMembership(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "club_id", nullable = false)
    val club: Club,
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false)
    val user: User,
) {
    @field:Id @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
