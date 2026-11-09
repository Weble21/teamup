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

@Entity
@Table(name = "clubs")
class Club(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "owner_id", nullable = false, updatable = false)
    val owner: User,

    name: String,

    sportType: SportType,
) {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @field:Column(nullable = false, length = 100)
    var name: String = validatedName(name)
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "sport_type", nullable = false, length = 30)
    var sportType: SportType = sportType
        protected set

    fun updateDetails(name: String, sportType: SportType) {
        this.name = validatedName(name)
        this.sportType = sportType
    }

    private fun validatedName(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 100) {
            "Club name must contain 1 to 100 characters"
        }
        return trimmed
    }
}
