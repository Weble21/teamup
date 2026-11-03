package com.teamup.teamup.auth

import com.teamup.teamup.user.AuthProvider
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SocialProfileMapperTest {
    @Test
    fun `kakao numeric ID is preserved without requiring email`() {
        val result = SocialProfileMapper.fromOAuth2(
            "kakao",
            mapOf("id" to 123456789012345L, "kakao_account" to mapOf("profile" to mapOf("nickname" to "선수"))),
        )
        assertEquals(AuthProvider.KAKAO, result.provider)
        assertEquals("123456789012345", result.providerUserId)
        assertEquals("선수", result.name)
        assertNull(result.email)
    }

    @Test
    fun `naver uses nested response ID rather than email`() {
        val result = SocialProfileMapper.fromOAuth2(
            "naver",
            mapOf("response" to mapOf("id" to "Naver-AbC_12", "nickname" to "팀원", "email" to "player@example.com")),
        )
        assertEquals(AuthProvider.NAVER, result.provider)
        assertEquals("Naver-AbC_12", result.providerUserId)
        assertEquals("player@example.com", result.email)
    }

    @Test
    fun `google uses subject and ignores unverified email`() {
        val result = SocialProfileMapper.fromGoogle(
            mapOf("sub" to "google-subject", "email" to "player@example.com", "email_verified" to false),
        )
        assertEquals(AuthProvider.GOOGLE, result.provider)
        assertEquals("google-subject", result.providerUserId)
        assertEquals("사용자", result.name)
        assertNull(result.email)
    }

    @Test
    fun `missing provider ID fails authentication`() {
        assertFailsWith<OAuth2AuthenticationException> {
            SocialProfileMapper.fromOAuth2("naver", mapOf("response" to mapOf("email" to "player@example.com")))
        }
        assertFailsWith<OAuth2AuthenticationException> {
            SocialProfileMapper.fromOAuth2("unsupported", emptyMap())
        }
    }

    @Test
    fun `kakao unverified email is ignored`() {
        val result = SocialProfileMapper.fromOAuth2(
            "kakao", mapOf("id" to 7L, "kakao_account" to mapOf(
                "email" to "player@example.com", "is_email_valid" to true, "is_email_verified" to false,
            )),
        )
        assertNull(result.email)
    }
}
