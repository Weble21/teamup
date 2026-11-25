package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.ClubInvitation
import com.teamup.teamup.model.entity.ClubMembership
import com.teamup.teamup.model.entity.ClubMember
import com.teamup.teamup.model.repository.ClubInvitationRepository
import com.teamup.teamup.model.repository.ClubMembershipRepository
import com.teamup.teamup.model.repository.ClubRepository
import com.teamup.teamup.model.repository.UserRepository
import com.teamup.teamup.model.repository.ClubMemberRepository
import com.teamup.teamup.view.InvitationDetailsResponse
import com.teamup.teamup.view.InvitationPreviewResponse
import com.teamup.teamup.view.InvitationJoinResponse
import com.teamup.teamup.view.InvitationCandidate
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
        val candidates = if (joined) emptyList() else members.findAllByClubIdAndActiveTrueAndUserIsNullOrderByNameAscIdAsc(clubId)
            .map { InvitationCandidate(checkNotNull(it.id), it.name, it.position) }
        return InvitationPreviewResponse(clubId, club.name, club.sportType, joined, candidates)
    }

    // A waiter must see membership committed by the previous club-lock holder.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun join(userId: Long, token: String, memberId: Long?, profile: NewClubMember?): InvitationJoinResponse {
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
        if ((memberId == null) == (profile == null) || (memberId != null && memberId <= 0))
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a player or provide a profile")
        val user = users.findById(userId).orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found") }
        if (members.findByClubIdAndUserId(clubId, userId) != null)
            throw ResponseStatusException(HttpStatus.CONFLICT, "User already has a club profile")
        if (memberId != null) {
            val member = members.findForUpdate(memberId, clubId)
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Player is unavailable")
            if (!member.active || member.user != null) throw ResponseStatusException(HttpStatus.CONFLICT, "Player is unavailable")
            member.linkUser(user)
        } else {
            val input = checkNotNull(profile)
            members.save(ClubMember(club, input.name, input.position, input.rating, input.age, input.notes, user))
        }
        memberships.save(ClubMembership(club, user))
        return InvitationJoinResponse(clubId, false)
    }

    private fun hash(token: String): String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)),
    )
}
