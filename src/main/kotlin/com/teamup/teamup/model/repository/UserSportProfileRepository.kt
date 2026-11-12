package com.teamup.teamup.model.repository

import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.entity.UserSportProfile
import org.springframework.data.jpa.repository.JpaRepository

interface UserSportProfileRepository : JpaRepository<UserSportProfile, Long> {
    fun findAllByUserIdOrderBySportType(userId: Long): List<UserSportProfile>
    fun findByUserIdAndSportType(userId: Long, sportType: SportType): UserSportProfile?
}
