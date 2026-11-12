package com.teamup.teamup.model.repository

import com.teamup.teamup.model.entity.Club
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import jakarta.persistence.LockModeType

interface ClubRepository : JpaRepository<Club, Long> {
    fun findAllByOwnerIdOrderByIdDesc(ownerId: Long): List<Club>
    fun findByIdAndOwnerId(id: Long, ownerId: Long): Club?
    @Query("select c from Club c join fetch c.owner where c.owner.id = :userId or exists (select m.id from ClubMembership m where m.club = c and m.user.id = :userId) order by c.id desc")
    fun findVisibleForUser(userId: Long): List<Club>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Club c where c.id = :id and c.owner.id = :ownerId")
    fun findOwnedForUpdate(id: Long, ownerId: Long): Club?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Club c where c.id = :id")
    fun findForUpdate(id: Long): Club?
}
