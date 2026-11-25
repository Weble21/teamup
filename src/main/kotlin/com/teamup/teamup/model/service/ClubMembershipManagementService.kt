package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.*
import com.teamup.teamup.model.repository.*
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

data class MembershipView(val userId: Long, val name: String, val manager: Boolean)

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
class ClubMembershipManagementService(
    private val clubs: ClubRepository,
    private val memberships: ClubMembershipRepository,
    private val members: ClubMemberRepository,
    private val deletion: ClubDeletionRepository,
    private val em: EntityManager,
) {
    private fun lockedClub(userId: Long, clubId: Long): Club {
        val club = clubs.findForUpdate(clubId) ?: missing()
        if (club.owner.id != userId && !memberships.existsByClubIdAndUserId(clubId, userId)) missing()
        return club
    }

    private fun isManager(club: Club, userId: Long) = club.owner.id == userId ||
        memberships.findByClubIdAndUserId(club.id!!, userId)?.manager == true

    private fun requireManager(club: Club, userId: Long) {
        if (!isManager(club, userId)) throw ResponseStatusException(HttpStatus.FORBIDDEN, "모임장만 사용할 수 있습니다.")
    }

    @Transactional(readOnly = true)
    fun list(userId: Long, clubId: Long): List<MembershipView> {
        val club = clubs.findManaged(clubId, userId) ?: missing()
        val rows = memberships.findWithUsers(clubId).associateBy { it.user.id }.toMutableMap()
        val result = rows.values.map { MembershipView(it.user.id!!, it.user.name, it.manager || it.user.id == club.owner.id) }.toMutableList()
        if (!rows.containsKey(club.owner.id)) result.add(0, MembershipView(club.owner.id!!, club.owner.name, true))
        return result
    }

    fun changeManager(userId: Long, clubId: Long, targetId: Long, manager: Boolean, transfer: Boolean) {
        val club = lockedClub(userId, clubId)
        requireManager(club, userId)
        if (transfer && (!manager || targetId == userId))
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "다른 회원에게 권한을 위임해 주세요.")
        val target = membership(club, targetId)
        if (manager) target.manager = true else demote(club, target)
        if (transfer) demote(club, membership(club, userId))
    }

    private fun membership(club: Club, userId: Long): ClubMembership {
        return memberships.findByClubIdAndUserId(club.id!!, userId)
            ?: if (club.owner.id == userId) memberships.save(ClubMembership(club, club.owner, true)) else missing()
    }

    private fun demote(club: Club, target: ClubMembership) {
        if (!isManager(club, target.user.id!!)) return
        // Include in-memory changes so a transfer can promote and demote atomically.
        val successor = memberships.findWithUsers(club.id!!).firstOrNull {
            it.user.id != target.user.id && (it.manager || it.user.id == club.owner.id)
        } ?: throw ResponseStatusException(HttpStatus.CONFLICT, "마지막 모임장은 다른 회원에게 권한을 넘겨야 합니다.")
        if (club.owner.id == target.user.id) club.owner = successor.user
        target.manager = false
    }

    fun leave(userId: Long, clubId: Long) {
        val club = lockedClub(userId, clubId)
        val membership = membership(club, userId)
        if (isManager(club, userId)) demote(club, membership)
        members.findByClubIdAndUserId(clubId, userId)?.depart()
        memberships.delete(membership)
    }

    fun delete(userId: Long, clubId: Long, confirmation: String) {
        val club = lockedClub(userId, clubId)
        requireManager(club, userId)
        if (confirmation != "삭제") throw ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제를 정확히 입력해 주세요.")
        em.flush()
        deletion.deleteClub(clubId)
        em.clear()
    }

    private fun missing(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "모임 또는 회원을 찾을 수 없습니다.")
}
