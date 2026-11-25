package com.teamup.teamup.model.repository

import com.teamup.teamup.model.entity.ClubMembership
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ClubMembershipRepository : JpaRepository<ClubMembership, Long> {
    fun existsByClubIdAndUserId(clubId: Long, userId: Long): Boolean
    fun findByClubIdAndUserId(clubId: Long, userId: Long): ClubMembership?
    @Query("select m from ClubMembership m join fetch m.user where m.club.id = :clubId order by m.id")
    fun findWithUsers(clubId: Long): List<ClubMembership>
    fun findAllByUserIdAndManagerTrue(userId: Long): List<ClubMembership>
}
