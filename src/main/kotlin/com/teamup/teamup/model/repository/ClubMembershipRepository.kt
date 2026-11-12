package com.teamup.teamup.model.repository

import com.teamup.teamup.model.entity.ClubMembership
import org.springframework.data.jpa.repository.JpaRepository

interface ClubMembershipRepository : JpaRepository<ClubMembership, Long> {
    fun existsByClubIdAndUserId(clubId: Long, userId: Long): Boolean
}
