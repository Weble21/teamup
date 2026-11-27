package com.teamup.teamup.model.service

import com.teamup.teamup.model.repository.VotePushDevice
import com.teamup.teamup.model.repository.VotePushRepository
import jakarta.annotation.PreDestroy
import nl.martijndwars.webpush.Encoding
import nl.martijndwars.webpush.Notification
import nl.martijndwars.webpush.PushService
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.HttpClients
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.security.Security
import java.util.Base64

data class VoteCreated(val clubId: Long, val voteId: Long)

@Service
@EnableAsync
class VotePushService(
    private val devices: VotePushRepository,
    @Value("\${WEB_PUSH_PUBLIC_KEY:}") val publicKey: String,
    @Value("\${WEB_PUSH_PRIVATE_KEY:}") privateKey: String,
    @Value("\${WEB_PUSH_SUBJECT:}") subject: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sender = if (listOf(publicKey, privateKey, subject).all { it.isNotBlank() }) {
        Security.addProvider(BouncyCastleProvider())
        PushService(publicKey, privateKey, subject)
    } else null
    val enabled get() = sender != null
    private val http = HttpClients.custom().disableRedirectHandling().disableAutomaticRetries()
        .setDefaultRequestConfig(RequestConfig.custom().setConnectionRequestTimeout(5000)
            .setConnectTimeout(5000).setSocketTimeout(10000).build()).build()

    @PreDestroy
    fun close() = http.close()

    fun subscribe(userId: Long, device: VotePushDevice) {
        if (!enabled) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)
        val uri = runCatching { URI(device.endpoint) }.getOrNull()
        val host = uri?.host.orEmpty().lowercase()
        val trusted = host == "fcm.googleapis.com" || host == "updates.push.services.mozilla.com" ||
            host == "web.push.apple.com" || host.endsWith(".push.apple.com") || host.endsWith(".notify.windows.com")
        fun decode(value: String) = runCatching { Base64.getUrlDecoder().decode(value) }.getOrNull()
        val key = decode(device.publicKey)
        if (!trusted || uri?.scheme != "https" || uri.port !in listOf(-1, 443) || uri.userInfo != null ||
            uri.fragment != null || device.endpoint.length > 2048 || key?.size != 65 || key[0].toInt() != 4 ||
            decode(device.auth)?.size != 16) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
        devices.subscribe(userId, device)
    }

    fun unsubscribe(userId: Long, endpoint: String) = devices.unsubscribe(userId, endpoint)
    fun isSubscribed(userId: Long, endpoint: String) = devices.isSubscribed(userId, endpoint)

    @Async
    @TransactionalEventListener
    fun onCreated(event: VoteCreated) {
        val push = sender ?: return
        val payload = """{"clubId":${event.clubId},"voteId":${event.voteId}}"""
        for (device in devices.recipients(event.clubId)) {
            try {
                val message = Notification(device.endpoint, device.publicKey, device.auth, payload)
                val status = http.execute(push.preparePost(message, Encoding.AES128GCM)).use { it.statusLine.statusCode }
                when (status) {
                    404, 410 -> devices.expire(device)
                    !in 200..299 -> log.warn("Vote {} push returned {}", event.voteId, status)
                }
            } catch (failure: Exception) {
                log.warn("Vote {} push failed ({})", event.voteId, failure.javaClass.simpleName)
            }
        }
    }
}
