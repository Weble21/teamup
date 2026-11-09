package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.Club
import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.repository.ClubRepository
import com.teamup.teamup.model.repository.UserRepository
import com.teamup.teamup.view.ClubResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ClubService(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun update(ownerId: Long, clubId: Long, name: String, sportType: SportType): ClubResponse {
        val club = clubRepository.findByIdAndOwnerId(clubId, ownerId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        club.updateDetails(name, sportType)
        return ClubResponse(checkNotNull(club.id), club.name, club.sportType, ownerId)
    }

    @Transactional(readOnly = true)
    fun getMyClub(ownerId: Long, clubId: Long): ClubResponse {
        val club = clubRepository.findByIdAndOwnerId(clubId, ownerId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        return ClubResponse(checkNotNull(club.id), club.name, club.sportType, ownerId)
    }

    @Transactional(readOnly = true)
    fun getMyClubs(ownerId: Long): List<ClubResponse> {
        if (!userRepository.existsById(ownerId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        return clubRepository.findAllByOwnerIdOrderByIdDesc(ownerId).map { club ->
            ClubResponse(checkNotNull(club.id), club.name, club.sportType, ownerId)
        }
    }

    @Transactional
    fun create(ownerId: Long, name: String, sportType: SportType): ClubResponse {
        val owner = userRepository.findById(ownerId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        val club = clubRepository.save(Club(owner, name, sportType))
        return ClubResponse(checkNotNull(club.id), club.name, club.sportType, checkNotNull(owner.id))
    }
}
