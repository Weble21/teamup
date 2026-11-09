package com.teamup.teamup.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "social_accounts",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_social_accounts_provider_id",
            columnNames = ["provider", "provider_user_id"],
        ),
    ],
)
class SocialAccount(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false, updatable = false)
    val user: User,

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20, updatable = false)
    val provider: AuthProvider,

    @field:Column(name = "provider_user_id", nullable = false, length = 255, updatable = false)
    val providerUserId: String,
) {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
