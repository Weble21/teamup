package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.entity.UserSportProfile
import com.teamup.teamup.model.repository.UserRepository
import com.teamup.teamup.model.repository.UserSportProfileRepository
import com.teamup.teamup.view.UserSportProfileResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserSportProfileService(
    private val profiles: UserSportProfileRepository,
    private val users: UserRepository,
) {
    @Transactional(readOnly = true)
    fun list(userId: Long): List<UserSportProfileResponse> =
        profiles.findAllByUserIdOrderBySportType(userId).map(::toResponse)

    @Transactional
    fun save(userId: Long, sportType: SportType, input: NewClubMember): UserSportProfileResponse {
        val existing = profiles.findByUserIdAndSportType(userId, sportType)
        if (existing != null) {
            existing.update(input.name, input.position, input.rating, input.age, input.notes)
            return toResponse(existing)
        }
        val user = users.findById(userId).orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found") }
        return toResponse(profiles.save(UserSportProfile(user, sportType, input.name, input.position, input.rating, input.age, input.notes)))
    }

    private fun toResponse(profile: UserSportProfile) = UserSportProfileResponse(
        profile.sportType, profile.name, profile.position, profile.rating, profile.age, profile.notes,
    )
}
