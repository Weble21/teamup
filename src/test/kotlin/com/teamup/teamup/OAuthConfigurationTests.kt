package com.teamup.teamup

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.core.env.Environment
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Checks the real local OAuth configuration and DB schema without logging into a provider.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("oauth")
class OAuthConfigurationTests {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var environment: Environment

    @Test
    fun `all providers start authorization with frontend callback and state`() {
        val frontend = environment.getProperty("app.frontend-url")!!
        for (provider in listOf("google", "kakao", "naver")) {
            val response = mvc.perform(get("/oauth2/authorization/$provider"))
                .andExpect(status().is3xxRedirection).andReturn().response
            val location = response.redirectedUrl!!
            val query = URI.create(location).rawQuery.split("&").associate {
                val pair = it.split("=", limit = 2)
                pair[0] to URLDecoder.decode(pair.getOrElse(1) { "" }, StandardCharsets.UTF_8)
            }
            assertEquals("$frontend/login/oauth2/code/$provider", query["redirect_uri"])
            assertTrue(location.contains("state="))
            assertFalse(location.contains("client_secret="))
        }
    }

    @Test
    fun `real oauth configuration protects API without login redirect`() {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/clubs")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `invalid callback returns to frontend error page`() {
        val response = mvc.perform(get("/login/oauth2/code/google").param("code", "invalid").param("state", "unknown"))
            .andExpect(status().is3xxRedirection).andReturn().response
        assertTrue(response.redirectedUrl!!.endsWith("/?loginError=1"))
    }
}
