package com.teamup.teamup.controller

import com.teamup.teamup.config.SecurityConfig
import com.teamup.teamup.model.auth.SocialOAuth2UserService
import com.teamup.teamup.model.auth.SocialOidcUserService
import com.teamup.teamup.model.service.ClubInvitationService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.mock.web.MockHttpSession
import com.teamup.teamup.view.InvitationDetailsResponse
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ClubInvitationController::class)
@Import(SecurityConfig::class)
@ActiveProfiles("oauth")
class ClubInvitationControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: ClubInvitationService
    @MockitoBean lateinit var oauth2UserService: SocialOAuth2UserService
    @MockitoBean lateinit var oidcUserService: SocialOidcUserService
    @MockitoBean lateinit var registrations: ClientRegistrationRepository

    @Test fun `join requires authentication and csrf`() {
        val body = """{"token":"${"a".repeat(43)}"}"""
        mvc.perform(post("/api/invitations/join").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/invitations/join").with(oauth2Login()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test fun `retrieval requires login`() {
        mvc.perform(get("/api/clubs/10/invitation")).andExpect(status().isUnauthorized)
        verifyNoInteractions(service)
    }

    @Test fun `different sessions of same account retrieve token without caching or reissuing`() {
        val token = "a".repeat(43)
        `when`(service.getCurrent(7, 10)).thenReturn(InvitationDetailsResponse(token, false))
        repeat(2) {
            val principal = DefaultOAuth2User(listOf(SimpleGrantedAuthority("ROLE_USER")), mapOf("userId" to "7"), "userId")
            mvc.perform(get("/api/clubs/10/invitation").session(MockHttpSession()).with(oauth2Login().oauth2User(principal)))
                .andExpect(status().isOk)
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.requiresReissue").value(false))
        }
        verify(service, org.mockito.Mockito.times(2)).getCurrent(7, 10)
    }

    @Test fun `malformed invitation is rejected`() {
        mvc.perform(post("/api/invitations/join").with(oauth2Login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"token":"wrong"}""")).andExpect(status().isBadRequest)
        verifyNoInteractions(service)
    }
}
