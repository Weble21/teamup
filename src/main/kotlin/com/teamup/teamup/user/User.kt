package com.teamup.teamup.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class User(
    @field:Column(nullable = false, length = 100)
    var name: String,

    @field:Column(length = 255)
    var email: String? = null,
) {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @field:Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    var createdAt: LocalDateTime? = null
        protected set
}