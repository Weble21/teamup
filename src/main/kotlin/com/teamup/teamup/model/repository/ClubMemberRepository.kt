package com.teamup.teamup.model.repository

import com.teamup.teamup.model.entity.ClubMember
import org.springframework.data.jpa.repository.JpaRepository

interface ClubMemberRepository : JpaRepository<ClubMember, Long> {
    fun findAllByClubIdOrderByIdDesc(clubId: Long): List<ClubMember>
    fun findAllByClubIdAndActiveOrderByIdDesc(clubId: Long, active: Boolean): List<ClubMember>
    fun findAllByClubIdAndActiveTrueAndIdIn(clubId: Long, ids: Collection<Long>): List<ClubMember>
    fun findByIdAndClubId(id: Long, clubId: Long): ClubMember?
}
