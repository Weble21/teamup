package com.teamup.teamup.model.entity

import jakarta.persistence.*

@Entity
@Table(name = "user_sport_profiles", uniqueConstraints = [UniqueConstraint(name = "uk_user_sport_profiles_user_sport", columnNames = ["user_id", "sport_type"])])
class UserSportProfile(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false, updatable = false)
    val user: User,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "sport_type", nullable = false, length = 30, updatable = false)
    val sportType: SportType,
    name: String,
    position: String,
    rating: Int,
    age: Int?,
    notes: String?,
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

    init { validateDetails() }

    fun update(name: String, position: String, rating: Int, age: Int?, notes: String?) {
        this.name = name.trim()
        this.position = position.trim()
        this.rating = rating
        this.age = age
        this.notes = notes?.trim()?.takeIf { it.isNotEmpty() }
        validateDetails()
    }

    private fun validateDetails() {
        require(name.isNotEmpty() && name.length <= 100)
        require(position.isNotEmpty() && position.length <= 50)
        require(rating in 1..100)
        require(age?.let { it in 0..130 } ?: true)
        require((notes?.length ?: 0) <= 1000)
    }
}
