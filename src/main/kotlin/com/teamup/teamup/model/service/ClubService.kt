package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.Club
import com.teamup.teamup.model.entity.ClubMembership
import com.teamup.teamup.model.entity.ClubMember
import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.repository.ClubRepository
import com.teamup.teamup.model.repository.ClubMemberRepository
import com.teamup.teamup.model.repository.UserRepository
import com.teamup.teamup.model.repository.ClubMembershipRepository
import com.teamup.teamup.view.ClubResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ClubService(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val memberRepository: ClubMemberRepository,
    private val memberships: ClubMembershipRepository,
) {
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun update(ownerId: Long, clubId: Long, name: String, sportType: SportType): ClubResponse {
        val club = clubRepository.findManagedForUpdate(clubId, ownerId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        club.updateDetails(name, sportType)
        return ClubResponse(checkNotNull(club.id), club.name, club.sportType, checkNotNull(club.owner.id), memberRepository.countByClubIdAndActiveTrue(clubId), true)
    }

    @Transactional(readOnly = true)
    fun getMyClub(ownerId: Long, clubId: Long): ClubResponse {
        val club = clubRepository.findById(clubId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        if (club.owner.id != ownerId && !memberships.existsByClubIdAndUserId(clubId, ownerId)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        return ClubResponse(checkNotNull(club.id), club.name, club.sportType, checkNotNull(club.owner.id), memberRepository.countByClubIdAndActiveTrue(clubId), clubRepository.findManaged(clubId, ownerId) != null)
    }

    @Transactional(readOnly = true)
    fun getMyClubs(ownerId: Long): List<ClubResponse> {
        if (!userRepository.existsById(ownerId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        val clubs = clubRepository.findVisibleForUser(ownerId)
        if (clubs.isEmpty()) return emptyList()
        val managedIds = memberships.findAllByUserIdAndManagerTrue(ownerId).map { it.club.id }.toSet()
        val counts = memberRepository.countActiveByClubs(clubs.map { checkNotNull(it.id) }).associate { it.clubId to it.memberCount }
        return clubs.map { club ->
            ClubResponse(checkNotNull(club.id), club.name, club.sportType, checkNotNull(club.owner.id), counts[club.id] ?: 0, club.owner.id == ownerId || club.id in managedIds)
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun create(ownerId: Long, name: String, sportType: SportType, profile: NewClubMember): ClubResponse {
        val owner = userRepository.findById(ownerId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        val club = clubRepository.save(Club(owner, name, sportType))
        memberships.save(ClubMembership(club, owner, true))
        memberRepository.save(ClubMember(club, profile.name, profile.position, profile.rating, profile.age, profile.notes, owner))
        return ClubResponse(checkNotNull(club.id), club.name, club.sportType, checkNotNull(owner.id), 1, true)
    }
}
