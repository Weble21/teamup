package com.teamup.teamup.model.repository

import com.teamup.teamup.model.entity.ClubMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.Lock
import jakarta.persistence.LockModeType

interface ClubMemberRepository : JpaRepository<ClubMember, Long> {
    fun countByClubIdAndActiveTrue(clubId: Long): Long
    @Query("select m.club.id as clubId, count(m) as memberCount from ClubMember m where m.club.id in :clubIds and m.active = true group by m.club.id")
    fun countActiveByClubs(clubIds: Collection<Long>): List<ActiveClubCount>
    fun findAllByClubIdOrderByIdDesc(clubId: Long): List<ClubMember>
    fun findAllByClubIdAndActiveOrderByIdDesc(clubId: Long, active: Boolean): List<ClubMember>
    fun findAllByClubIdAndActiveTrueAndIdIn(clubId: Long, ids: Collection<Long>): List<ClubMember>
    fun findByIdAndClubId(id: Long, clubId: Long): ClubMember?
    fun findByClubIdAndUserId(clubId: Long, userId: Long): ClubMember?
    fun findAllByClubIdAndUserIdIn(clubId: Long, userIds: Collection<Long>): List<ClubMember>
    fun findAllByClubIdAndActiveTrueAndUserIsNullOrderByNameAscIdAsc(clubId: Long): List<ClubMember>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ClubMember m where m.id = :id and m.club.id = :clubId")
    fun findForUpdate(id: Long, clubId: Long): ClubMember?
}

interface ActiveClubCount {
    val clubId: Long
    val memberCount: Long
}
