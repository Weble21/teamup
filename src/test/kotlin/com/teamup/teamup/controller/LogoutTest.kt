package com.teamup.teamup.controller

import com.teamup.teamup.config.SecurityConfig
import com.teamup.teamup.model.auth.SocialOAuth2UserService
import com.teamup.teamup.model.auth.SocialOidcUserService
import com.teamup.teamup.model.service.UserService
import com.teamup.teamup.view.CsrfResponse
import com.teamup.teamup.view.UserResponse
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated
import org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@WebMvcTest(AuthController::class, UserController::class)
@Import(SecurityConfig::class)
@ActiveProfiles("oauth")
class LogoutTest {
    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockitoBean
    lateinit var userService: UserService

    @MockitoBean
    lateinit var oauth2UserService: SocialOAuth2UserService

    @MockitoBean
    lateinit var oidcUserService: SocialOidcUserService

    @MockitoBean
    lateinit var registrations: ClientRegistrationRepository

    @Test
    fun `logout using issued CSRF token invalidates session and expires cookie`() {
        val session = loginSession()
        val oldSessionId = session.id
        `when`(userService.getUser(7)).thenReturn(UserResponse(7, "Owner", null))
        mvc.perform(get("/api/users/me").session(session)).andExpect(status().isOk)

        val response = mvc.perform(get("/api/auth/csrf").session(session))
            .andExpect(status().isOk).andReturn().response
        val csrf = objectMapper.readValue(response.contentAsString, CsrfResponse::class.java)

        mvc.perform(post("/api/auth/logout").session(session)
            .header(csrf.headerName, csrf.token))
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))
            .andExpect(cookie().maxAge("JSESSIONID", 0))
            .andExpect(unauthenticated())
        assertTrue(session.isInvalid)

        mvc.perform(get("/api/users/me").cookie(Cookie("JSESSIONID", oldSessionId)))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `logout without CSRF token is forbidden and preserves login`() {
        val session = loginSession()
        mvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isForbidden)
        assertStillLoggedIn(session)
    }

    @Test
    fun `logout with invalid CSRF token is forbidden and preserves login`() {
        val session = loginSession()
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", "invalid"))
            .andExpect(status().isForbidden)
        assertStillLoggedIn(session)
    }

    @Test
    fun `GET does not log the user out`() {
        val session = loginSession()
        mvc.perform(get("/api/auth/logout").session(session))
            .andExpect(status().isNotFound)
        assertStillLoggedIn(session)
    }

    @Test
    fun `unauthenticated user cannot fetch CSRF endpoint`() {
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isUnauthorized)
    }

    private fun loginSession(): MockHttpSession {
        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
        val principal = DefaultOAuth2User(authorities, mapOf("userId" to "7"), "userId")
        val authentication = OAuth2AuthenticationToken(principal, authorities, "kakao")
        return MockHttpSession().apply {
            setAttribute(SPRING_SECURITY_CONTEXT_KEY, SecurityContextImpl(authentication))
        }
    }

    private fun assertStillLoggedIn(session: MockHttpSession) {
        assertFalse(session.isInvalid)
        `when`(userService.getUser(7)).thenReturn(UserResponse(7, "Owner", null))
        mvc.perform(get("/api/users/me").session(session)).andExpect(status().isOk)
    }
}
