package com.teamup.teamup.controller

import com.teamup.teamup.config.SecurityConfig
import com.teamup.teamup.model.auth.SocialOAuth2UserService
import com.teamup.teamup.model.auth.SocialOidcUserService
import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.service.MatchService
import com.teamup.teamup.view.MatchResponse
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant
import java.util.UUID

@WebMvcTest(MatchController::class)
@Import(SecurityConfig::class)
@ActiveProfiles("oauth")
class MatchControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: MatchService
    @MockitoBean lateinit var oauth2UserService: SocialOAuth2UserService
    @MockitoBean lateinit var oidcUserService: SocialOidcUserService
    @MockitoBean lateinit var registrations: ClientRegistrationRepository
    private val key = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val body = """{"requestId":"$key","memberIds":[5,8],"teams":[[5],[8]]}"""
    private fun login() = oauth2Login().oauth2User(DefaultOAuth2User(
        listOf(SimpleGrantedAuthority("ROLE_USER")), mapOf("userId" to "7"), "userId",
    ))

    @Test
    fun `confirmation returns 201 with resource location`() {
        `when`(service.confirm(7, 3, key, listOf(5L, 8L), listOf(listOf(5L), listOf(8L)), null, null))
            .thenReturn(MatchResponse(9, 3, SportType.BASKETBALL, Instant.parse("2026-11-09T00:00:00Z"), null, emptyList()))
        mvc.perform(post("/api/clubs/3/matches").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated).andExpect(header().string("Location", "/api/clubs/3/matches/9"))
            .andExpect(jsonPath("$.id").value(9))
    }

    @Test
    fun `record endpoints require authentication`() {
        mvc.perform(get("/api/clubs/3/matches")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/clubs/3/matches/9")).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/clubs/3/matches").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(service)
    }

    @Test
    fun `confirmation without CSRF is rejected`() {
        mvc.perform(post("/api/clubs/3/matches").with(login()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `invalid confirmation request is rejected before service call`() {
        for (invalid in listOf("{}", body.replace(key.toString(), "invalid"), body.replace("[5,8]", "[]"))) {
            mvc.perform(post("/api/clubs/3/matches").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest)
        }
        verifyNoInteractions(service)
    }
}
