package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.ClubInvitation
import com.teamup.teamup.model.entity.ClubMembership
import com.teamup.teamup.model.entity.ClubMember
import com.teamup.teamup.model.repository.ClubInvitationRepository
import com.teamup.teamup.model.repository.ClubMembershipRepository
import com.teamup.teamup.model.repository.ClubRepository
import com.teamup.teamup.model.repository.UserRepository
import com.teamup.teamup.model.repository.UserSportProfileRepository
import com.teamup.teamup.model.repository.ClubMemberRepository
import com.teamup.teamup.view.InvitationDetailsResponse
import com.teamup.teamup.view.InvitationPreviewResponse
import com.teamup.teamup.view.InvitationJoinResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Isolation
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

@Service
class ClubInvitationService(
    private val clubs: ClubRepository,
    private val invitations: ClubInvitationRepository,
    private val memberships: ClubMembershipRepository,
    private val users: UserRepository,
    private val sportProfiles: UserSportProfileRepository,
    private val members: ClubMemberRepository,
) {
    private val random = SecureRandom()

    @Transactional(readOnly = true)
    fun getCurrent(ownerId: Long, clubId: Long): InvitationDetailsResponse {
        clubs.findByIdAndOwnerId(clubId, ownerId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        val invitation = invitations.findByClubId(clubId)
        return InvitationDetailsResponse(invitation?.token, invitation != null && invitation.token == null)
    }

    @Transactional
    fun issue(ownerId: Long, clubId: Long): String {
        val club = clubs.findOwnedForUpdate(clubId, ownerId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        val token = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val previous = invitations.findByClubId(clubId)
        if (previous == null) invitations.save(ClubInvitation(club, hash(token), token)) else {
            previous.tokenHash = hash(token)
            previous.token = token
        }
        return token
    }

    @Transactional
    fun revoke(ownerId: Long, clubId: Long) {
        clubs.findOwnedForUpdate(clubId, ownerId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        invitations.findByClubId(clubId)?.let { invitations.delete(it) }
    }

    @Transactional(readOnly = true)
    fun preview(userId: Long, token: String): InvitationPreviewResponse {
        val invitation = invitations.findByTokenHash(hash(token))
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found")
        val club = invitation.club
        val clubId = checkNotNull(club.id)
        val joined = club.owner.id == userId || memberships.existsByClubIdAndUserId(clubId, userId)
        return InvitationPreviewResponse(clubId, club.name, club.sportType, joined,
            sportProfiles.findByUserIdAndSportType(userId, club.sportType) != null)
    }

    // A waiter must see membership committed by the previous club-lock holder.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun join(userId: Long, token: String): InvitationJoinResponse {
        val tokenHash = hash(token)
        val candidate = invitations.findByTokenHash(tokenHash)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found")
        // Match issue/revoke and roster edits: lock the club before its invitation.
        val club = clubs.findForUpdate(checkNotNull(candidate.club.id))
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        invitations.findByTokenHashForUpdate(tokenHash)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found")
        val clubId = checkNotNull(club.id)
        if (club.owner.id == userId || memberships.existsByClubIdAndUserId(clubId, userId)) {
            return InvitationJoinResponse(clubId, true)
        }
        val profile = sportProfiles.findByUserIdAndSportType(userId, club.sportType)
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "해당 종목의 내 정보를 먼저 등록해 주세요.")
        val user = users.findById(userId).orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found") }
        memberships.save(ClubMembership(club, user))
        if (members.findByClubIdAndUserId(clubId, userId) == null) {
            members.save(ClubMember(club, profile.name, profile.position, profile.rating, profile.age, profile.notes, user))
        }
        return InvitationJoinResponse(clubId, false)
    }

    private fun hash(token: String): String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)),
    )
}
