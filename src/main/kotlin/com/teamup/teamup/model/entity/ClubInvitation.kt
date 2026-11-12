package com.teamup.teamup.model.entity

import jakarta.persistence.*

@Entity
@Table(name = "club_invitations")
class ClubInvitation(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "club_id", nullable = false)
    val club: Club,
    @field:Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    var tokenHash: String,
) {
    @field:Id @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
