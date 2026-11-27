package com.teamup.teamup.model.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.security.MessageDigest
import java.util.HexFormat

data class VotePushDevice(val endpoint: String, val publicKey: String, val auth: String)

@Repository
class VotePushRepository(private val jdbc: JdbcTemplate) {
    private fun hash(endpoint: String) =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(endpoint.toByteArray()))

    fun subscribe(userId: Long, device: VotePushDevice) {
        jdbc.update("""INSERT INTO vote_push_subscriptions (endpoint_hash, user_id, endpoint, public_key, auth_secret)
            VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE user_id = ?, public_key = ?, auth_secret = ?""",
            hash(device.endpoint), userId, device.endpoint, device.publicKey, device.auth, userId, device.publicKey, device.auth)
    }

    fun unsubscribe(userId: Long, endpoint: String) {
        jdbc.update("DELETE FROM vote_push_subscriptions WHERE user_id = ? AND endpoint_hash = ?", userId, hash(endpoint))
    }

    fun isSubscribed(userId: Long, endpoint: String): Boolean =
        jdbc.queryForObject("SELECT COUNT(*) FROM vote_push_subscriptions WHERE user_id = ? AND endpoint_hash = ?",
            Int::class.java, userId, hash(endpoint)) == 1

    fun recipients(clubId: Long): List<VotePushDevice> = jdbc.query("""
        SELECT endpoint, public_key, auth_secret FROM vote_push_subscriptions
        WHERE user_id IN (SELECT owner_id FROM clubs WHERE id = ?
                          UNION SELECT user_id FROM club_memberships WHERE club_id = ?)
        """, { row, _ -> VotePushDevice(row.getString(1), row.getString(2), row.getString(3)) }, clubId, clubId)

    fun expire(device: VotePushDevice) {
        jdbc.update("DELETE FROM vote_push_subscriptions WHERE endpoint_hash = ? AND auth_secret = ?",
            hash(device.endpoint), device.auth)
    }
}
