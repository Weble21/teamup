package com.teamup.teamup.model.service

import com.teamup.teamup.model.repository.VotePushDevice
import com.teamup.teamup.model.repository.VotePushRepository
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import org.mockito.Mockito
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import kotlin.test.assertEquals

class VotePushTest {
    @Test
    fun `configured sender posts encrypted payload and expires a gone endpoint`() {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val point = (keys.public as ECPublicKey).w
        fun scalar(value: java.math.BigInteger) = (ByteArray(32) + value.toByteArray()).takeLast(32).toByteArray()
        val encode = Base64.getUrlEncoder().withoutPadding()
        val publicKey = encode.encodeToString(byteArrayOf(4) + scalar(point.affineX) + scalar(point.affineY))
        val privateKey = encode.encodeToString(scalar((keys.private as ECPrivateKey).s))
        val devices = Mockito.mock(VotePushRepository::class.java)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val received = AtomicBoolean()
        server.createContext("/push") { exchange ->
            received.set(exchange.requestMethod == "POST" && exchange.requestHeaders.getFirst("Content-Encoding") == "aes128gcm"
                && exchange.requestHeaders.getFirst("Authorization").startsWith("vapid ") && exchange.requestBody.readAllBytes().isNotEmpty())
            exchange.sendResponseHeaders(410, -1)
            exchange.close()
        }
        server.start()
        val service = VotePushService(devices, publicKey, privateKey, "mailto:test@example.org")
        try {
            val device = VotePushDevice("http://127.0.0.1:${server.address.port}/push", publicKey, encode.encodeToString(ByteArray(16)))
            Mockito.`when`(devices.recipients(7)).thenReturn(listOf(device))
            service.onCreated(VoteCreated(7, 9))
            assertEquals(true, received.get())
            Mockito.verify(devices).expire(device)
        } finally {
            service.close()
            server.stop(0)
        }
    }

    @Test
    fun `each subscribed device in the club receives once including the creator`() {
        val source = DriverManagerDataSource("jdbc:h2:mem:votePush;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
        val jdbc = JdbcTemplate(source)
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)")
        jdbc.execute("CREATE TABLE clubs(id BIGINT PRIMARY KEY, owner_id BIGINT)")
        jdbc.execute("CREATE TABLE club_memberships(club_id BIGINT, user_id BIGINT)")
        ResourceDatabasePopulator(ClassPathResource("db/migration/V14__vote_push_subscriptions.sql")).execute(source)
        jdbc.update("INSERT INTO users VALUES (1), (2), (3)")
        jdbc.update("INSERT INTO clubs VALUES (10, 1), (20, 3)")
        jdbc.update("INSERT INTO club_memberships VALUES (10, 1), (10, 2), (20, 3)")
        val devices = VotePushRepository(jdbc)
        val owner = VotePushDevice("owner-device", "key", "auth")
        val member = owner.copy(endpoint = "member-device")
        val outsider = owner.copy(endpoint = "other-device")
        devices.subscribe(1, owner)
        devices.subscribe(2, member)
        devices.subscribe(2, member)
        devices.subscribe(3, outsider)
        assertEquals(listOf(owner, member).toSet(), devices.recipients(10).toSet())
        assertEquals(2, devices.recipients(10).size)
        devices.unsubscribe(3, member.endpoint)
        assertEquals(true, devices.isSubscribed(2, member.endpoint))
        jdbc.update("DELETE FROM club_memberships WHERE user_id = 2")
        assertEquals(listOf(owner), devices.recipients(10))
        devices.expire(owner)
        assertEquals(emptyList(), devices.recipients(10))
    }
}
