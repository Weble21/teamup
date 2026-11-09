package com.teamup.teamup.controller

import com.teamup.teamup.config.SecurityConfig
import com.teamup.teamup.model.auth.AppOidcUser
import com.teamup.teamup.model.auth.SocialOAuth2UserService
import com.teamup.teamup.model.auth.SocialOidcUserService
import com.teamup.teamup.model.service.UserService
import com.teamup.teamup.view.UserResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException

@WebMvcTest(UserController::class)
@Import(SecurityConfig::class)
@ActiveProfiles("oauth")
class UserControllerTest {
    @Autowired
    lateinit var mvc: MockMvc

    @MockitoBean
    lateinit var userService: UserService

    @MockitoBean
    lateinit var oauth2UserService: SocialOAuth2UserService

    @MockitoBean
    lateinit var oidcUserService: SocialOidcUserService

    @MockitoBean
    lateinit var registrations: ClientRegistrationRepository

    @Test
    fun `unauthenticated API request returns 401 instead of login redirect`() {
        mvc.perform(get("/api/users/me").header("Accept", "text/html"))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(userService)
    }

    @Test
    fun `OAuth2 login returns current DB profile and ignores requested user ID`() {
        `when`(userService.getUser(7)).thenReturn(UserResponse(7, "운영자", null))
        val principal = DefaultOAuth2User(
            listOf(SimpleGrantedAuthority("ROLE_USER")),
            mapOf("userId" to "7"),
            "userId",
        )
        mvc.perform(get("/api/users/me").param("userId", "99").with(oauth2Login().oauth2User(principal)))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"id":7,"name":"운영자","email":null}"""))
    }

    @Test
    fun `OIDC login uses internal user ID instead of provider subject`() {
        `when`(userService.getUser(8)).thenReturn(UserResponse(8, "회원", "member@example.com"))
        val token = OidcIdToken.withTokenValue("test-token").claim("sub", "external-subject").build()
        val principal = AppOidcUser(DefaultOidcUser(listOf(SimpleGrantedAuthority("ROLE_USER")), token), 8)
        mvc.perform(get("/api/users/me").with(oidcLogin().oidcUser(principal)))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"id":8,"name":"회원","email":"member@example.com"}"""))
    }

    @Test
    fun `stale login for missing user returns 404`() {
        `when`(userService.getUser(7)).thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND))
        val principal = DefaultOAuth2User(
            listOf(SimpleGrantedAuthority("ROLE_USER")), mapOf("userId" to "7"), "userId",
        )
        mvc.perform(get("/api/users/me").with(oauth2Login().oauth2User(principal)))
            .andExpect(status().isNotFound)
    }
}
