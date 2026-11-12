package com.teamup.teamup.model.repository

import com.teamup.teamup.model.entity.ClubInvitation
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface ClubInvitationRepository : JpaRepository<ClubInvitation, Long> {
    fun findByClubId(clubId: Long): ClubInvitation?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ClubInvitation i where i.tokenHash = :hash")
    fun findByTokenHashForUpdate(hash: String): ClubInvitation?
}
