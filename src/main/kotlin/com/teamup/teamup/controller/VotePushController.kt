package com.teamup.teamup.controller

import com.teamup.teamup.model.repository.VotePushDevice
import com.teamup.teamup.model.service.VotePushService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

data class PushKeys(val p256dh: String, val auth: String)
data class PushSubscriptionRequest(val endpoint: String, val keys: PushKeys)
data class PushEndpointRequest(val endpoint: String)

@RestController
@RequestMapping("/api/vote-push")
class VotePushController(private val service: VotePushService) {
    private fun userId(user: OAuth2User?) = user?.name?.toLongOrNull()
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

    @GetMapping("/config")
    fun config() = mapOf("publicKey" to service.publicKey.takeIf { service.enabled })

    @PutMapping("/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun subscribe(@AuthenticationPrincipal user: OAuth2User?, @RequestBody request: PushSubscriptionRequest) =
        service.subscribe(userId(user), VotePushDevice(request.endpoint, request.keys.p256dh, request.keys.auth))

    @PostMapping("/subscription/status")
    fun status(@AuthenticationPrincipal user: OAuth2User?, @RequestBody request: PushEndpointRequest) =
        mapOf("active" to service.isSubscribed(userId(user), request.endpoint))

    @DeleteMapping("/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unsubscribe(@AuthenticationPrincipal user: OAuth2User?, @RequestBody request: PushEndpointRequest) =
        service.unsubscribe(userId(user), request.endpoint)
}
