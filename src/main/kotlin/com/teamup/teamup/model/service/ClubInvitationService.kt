package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.ClubInvitation
import com.teamup.teamup.model.entity.ClubMembership
import com.teamup.teamup.model.repository.ClubInvitationRepository
import com.teamup.teamup.model.repository.ClubMembershipRepository
import com.teamup.teamup.model.repository.ClubRepository
import com.teamup.teamup.model.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
) {
    private val random = SecureRandom()

    @Transactional
    fun issue(ownerId: Long, clubId: Long): String {
        val club = clubs.findOwnedForUpdate(clubId, ownerId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        val token = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val previous = invitations.findByClubId(clubId)
        if (previous == null) invitations.save(ClubInvitation(club, hash(token))) else previous.tokenHash = hash(token)
        return token
    }

    @Transactional
    fun revoke(ownerId: Long, clubId: Long) {
        clubs.findOwnedForUpdate(clubId, ownerId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
        invitations.findByClubId(clubId)?.let { invitations.delete(it) }
    }

    @Transactional
    fun join(userId: Long, token: String): Long {
        val invitation = invitations.findByTokenHashForUpdate(hash(token))
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found")
        val club = invitation.club
        val clubId = checkNotNull(club.id)
        if (club.owner.id != userId && !memberships.existsByClubIdAndUserId(clubId, userId)) {
            val user = users.findById(userId).orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found") }
            memberships.save(ClubMembership(club, user))
        }
        return clubId
    }

    private fun hash(token: String): String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)),
    )
}
