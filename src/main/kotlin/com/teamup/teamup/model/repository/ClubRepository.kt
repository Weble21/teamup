package com.teamup.teamup.model.repository

import com.teamup.teamup.model.entity.Club
import org.springframework.data.jpa.repository.JpaRepository

interface ClubRepository : JpaRepository<Club, Long> {
    fun findAllByOwnerIdOrderByIdDesc(ownerId: Long): List<Club>
    fun findByIdAndOwnerId(id: Long, ownerId: Long): Club?
}
