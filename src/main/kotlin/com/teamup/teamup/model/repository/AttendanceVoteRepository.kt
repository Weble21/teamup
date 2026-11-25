package com.teamup.teamup.model.repository

import com.teamup.teamup.model.entity.AttendanceVote
import com.teamup.teamup.model.entity.AttendanceResponse
import com.teamup.teamup.model.entity.AttendanceVoteComment
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AttendanceVoteRepository : JpaRepository<AttendanceVote, Long> {
    fun findAllByClubIdOrderByClosesAtDesc(clubId: Long): List<AttendanceVote>
    fun findByIdAndClubId(id: Long, clubId: Long): AttendanceVote?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from AttendanceVote v where v.id = :id and v.club.id = :clubId")
    fun findForUpdate(@Param("id") id: Long, @Param("clubId") clubId: Long): AttendanceVote?
}

interface AttendanceVoteCommentRepository : JpaRepository<AttendanceVoteComment, Long> {
    @Query("select c from AttendanceVoteComment c join fetch c.author where c.vote.id in :voteIds order by c.id asc")
    fun findWithAuthors(@Param("voteIds") voteIds: Collection<Long>): List<AttendanceVoteComment>
}

interface AttendanceResponseRepository : JpaRepository<AttendanceResponse, Long> {
    @Query("select r from AttendanceResponse r join fetch r.user where r.vote.id in :voteIds")
    fun findWithUsers(@Param("voteIds") voteIds: Collection<Long>): List<AttendanceResponse>
    fun findByVoteIdAndUserId(voteId: Long, userId: Long): AttendanceResponse?
    @Query("select r from AttendanceResponse r join fetch r.user where r.vote.id = :voteId and r.choice = :choice order by r.id asc")
    fun findByVoteAndChoice(@Param("voteId") voteId: Long, @Param("choice") choice: com.teamup.teamup.model.entity.AttendanceChoice): List<AttendanceResponse>
}
