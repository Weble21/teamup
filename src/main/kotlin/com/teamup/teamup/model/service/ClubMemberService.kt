package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.ClubMember
import com.teamup.teamup.model.repository.ClubMemberRepository
import com.teamup.teamup.model.repository.ClubRepository
import com.teamup.teamup.view.ClubMemberResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

data class NewClubMember(val name: String, val position: String, val rating: Int, val age: Int?, val notes: String?)

@Service
class ClubMemberService(
    private val clubRepository: ClubRepository,
    private val memberRepository: ClubMemberRepository,
) {
    @Transactional
    fun createBulk(ownerId: Long, clubId: Long, requests: List<NewClubMember>): List<ClubMemberResponse> {
        val club = clubRepository.findByIdAndOwnerId(clubId, ownerId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        return requests.map { request ->
            val member = memberRepository.save(ClubMember(club, request.name, request.position, request.rating, request.age, request.notes))
            toResponse(member, clubId)
        }
    }
    @Transactional(readOnly = true)
    fun validateParticipants(ownerId: Long, clubId: Long, memberIds: List<Long?>): List<ClubMemberResponse> {
        if (clubRepository.findByIdAndOwnerId(clubId, ownerId) == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        }
        if (memberIds.isEmpty() || memberIds.any { it == null || it <= 0 } || memberIds.distinct().size != memberIds.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Select unique, positive member IDs")
        }
        val ids = memberIds.filterNotNull()
        val members = memberRepository.findAllByClubIdAndActiveTrueAndIdIn(clubId, ids)
        if (members.size != ids.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "All participants must be active members of this club")
        }
        val membersById = members.associateBy { checkNotNull(it.id) }
        return ids.map { toResponse(membersById.getValue(it), clubId) }
    }

    @Transactional
    fun deactivate(ownerId: Long, clubId: Long, memberId: Long): ClubMemberResponse {
        if (clubRepository.findByIdAndOwnerId(clubId, ownerId) == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        }
        val member = memberRepository.findByIdAndClubId(memberId, clubId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found")
        member.deactivate()
        return toResponse(member, clubId)
    }

    @Transactional
    fun update(
        ownerId: Long,
        clubId: Long,
        memberId: Long,
        name: String,
        position: String,
        rating: Int,
        age: Int?,
        notes: String?,
    ): ClubMemberResponse {
        if (clubRepository.findByIdAndOwnerId(clubId, ownerId) == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        }
        val member = memberRepository.findByIdAndClubId(memberId, clubId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found")
        member.updateDetails(name, position, rating, age, notes)
        return toResponse(member, clubId)
    }

    @Transactional(readOnly = true)
    fun getMembers(ownerId: Long, clubId: Long, active: Boolean? = null): List<ClubMemberResponse> {
        if (clubRepository.findByIdAndOwnerId(clubId, ownerId) == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        }
        val members = if (active == null) {
            memberRepository.findAllByClubIdOrderByIdDesc(clubId)
        } else {
            memberRepository.findAllByClubIdAndActiveOrderByIdDesc(clubId, active)
        }
        return members.map { member ->
            toResponse(member, clubId)
        }
    }

    @Transactional
    fun create(ownerId: Long, clubId: Long, name: String, position: String, rating: Int, age: Int?, notes: String?): ClubMemberResponse {
        val club = clubRepository.findByIdAndOwnerId(clubId, ownerId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        val member = memberRepository.save(ClubMember(club, name, position, rating, age, notes))
        return toResponse(member, clubId)
    }

    private fun toResponse(member: ClubMember, clubId: Long): ClubMemberResponse =
        ClubMemberResponse(
            checkNotNull(member.id), clubId, member.name, member.position,
            member.rating, member.age, member.notes, member.active,
        )
}
