package com.teamup.teamup.auth

import com.teamup.teamup.user.AuthProvider
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error

data class SocialProfile(
    val provider: AuthProvider,
    val providerUserId: String,
    val name: String,
    val email: String?,
)

// Input must come from Spring Security's verified provider response, not a request body.
object SocialProfileMapper {
    fun fromOAuth2(registrationId: String, attributes: Map<String, Any>): SocialProfile =
        when (registrationId) {
            "kakao" -> {
                val account = attributes["kakao_account"] as? Map<*, *> ?: emptyMap<Any, Any>()
                val profile = account["profile"] as? Map<*, *> ?: emptyMap<Any, Any>()
                SocialProfile(
                    AuthProvider.KAKAO,
                    requiredId(attributes["id"]),
                    displayName(profile["nickname"]),
                    if (account["is_email_valid"] == true && account["is_email_verified"] == true) {
                        optionalEmail(account["email"])
                    } else null,
                )
            }
            "naver" -> {
                val response = attributes["response"] as? Map<*, *> ?: invalidProfile()
                SocialProfile(
                    AuthProvider.NAVER,
                    requiredId(response["id"]),
                    displayName(response["nickname"] ?: response["name"]),
                    optionalEmail(response["email"]),
                )
            }
            else -> invalidProfile()
        }

    fun fromGoogle(claims: Map<String, Any>): SocialProfile = SocialProfile(
        AuthProvider.GOOGLE,
        requiredId(claims["sub"]),
        displayName(claims["name"]),
        if (claims["email_verified"] == true) optionalEmail(claims["email"]) else null,
    )

    private fun requiredId(raw: Any?): String {
        val id = when (raw) {
            is String -> raw
            is Long -> raw.toString()
            is Int -> raw.toString()
            else -> invalidProfile()
        }
        if (id.isBlank() || id.length > 255) invalidProfile()
        return id
    }

    private fun displayName(raw: Any?): String =
        (raw as? String)?.trim()?.takeIf { it.isNotEmpty() }?.take(100) ?: "사용자"

    private fun optionalEmail(raw: Any?): String? =
        (raw as? String)?.trim()?.takeIf { it.isNotEmpty() && it.length <= 255 }

    private fun invalidProfile(): Nothing = throw OAuth2AuthenticationException(
        OAuth2Error("invalid_user_info"),
        "The social provider returned an unsupported or missing user ID",
    )
}
