package com.teamup.teamup.controller

import com.teamup.teamup.config.SecurityConfig
import com.teamup.teamup.model.auth.SocialOAuth2UserService
import com.teamup.teamup.model.auth.SocialOidcUserService
import com.teamup.teamup.model.service.ClubInvitationService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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

    @Test fun `malformed invitation is rejected`() {
        mvc.perform(post("/api/invitations/join").with(oauth2Login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"token":"wrong"}""")).andExpect(status().isBadRequest)
        verifyNoInteractions(service)
    }
}
