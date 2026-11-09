package com.teamup.teamup.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "club_members")
class ClubMember(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "club_id", nullable = false, updatable = false)
    val club: Club,
    name: String,
    position: String,
    rating: Int,
    age: Int? = null,
    notes: String? = null,
) {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @field:Column(nullable = false, length = 100)
    var name: String = name.trim()
        protected set

    @field:Column(nullable = false, length = 50)
    var position: String = position.trim()
        protected set

    @field:Column(nullable = false)
    var rating: Int = rating
        protected set

    var age: Int? = age
        protected set

    @field:Column(length = 1000)
    var notes: String? = notes?.trim()?.takeIf { it.isNotEmpty() }
        protected set

    @field:Column(nullable = false)
    var active: Boolean = true
        protected set

    init {
        validateDetails(this.name, this.position, rating, age, this.notes)
    }

    fun deactivate() {
        active = false
    }

    fun updateDetails(name: String, position: String, rating: Int, age: Int?, notes: String?) {
        val trimmedName = name.trim()
        val trimmedPosition = position.trim()
        val trimmedNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
        validateDetails(trimmedName, trimmedPosition, rating, age, trimmedNotes)
        this.name = trimmedName
        this.position = trimmedPosition
        this.rating = rating
        this.age = age
        this.notes = trimmedNotes
    }

    private fun validateDetails(name: String, position: String, rating: Int, age: Int?, notes: String?) {
        require(name.isNotEmpty() && name.length <= 100) { "Member name must contain 1 to 100 characters" }
        require(position.isNotEmpty() && position.length <= 50) { "Position must contain 1 to 50 characters" }
        require(rating in 1..100) { "Rating must be between 1 and 100" }
        require(age?.let { it >= 0 } ?: true) { "Age must not be negative" }
        require((notes?.length ?: 0) <= 1000) { "Notes must not exceed 1000 characters" }
    }
}
