package com.teamup.teamup.controller

import com.teamup.teamup.config.SecurityConfig
import com.teamup.teamup.model.auth.SocialOAuth2UserService
import com.teamup.teamup.model.auth.SocialOidcUserService
import com.teamup.teamup.model.service.ClubMembershipManagementService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ClubMembershipController::class)
@Import(SecurityConfig::class)
@ActiveProfiles("oauth")
class ClubMembershipControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: ClubMembershipManagementService
    @MockitoBean lateinit var oauth2UserService: SocialOAuth2UserService
    @MockitoBean lateinit var oidcUserService: SocialOidcUserService
    @MockitoBean lateinit var registrations: ClientRegistrationRepository
    private fun login() = oauth2Login().oauth2User(DefaultOAuth2User(
        listOf(SimpleGrantedAuthority("ROLE_USER")), mapOf("userId" to "7"), "userId",
    ))

    @Test
    fun `all management endpoints require login and mutations require csrf`() {
        mvc.perform(get("/api/clubs/3/memberships")).andExpect(status().isUnauthorized)
        for (request in listOf(post("/api/clubs/3/leave"), put("/api/clubs/3/managers/8"), delete("/api/clubs/3"))) {
            mvc.perform(request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""{"manager":true,"confirmation":"삭제"}""")).andExpect(status().isUnauthorized)
        }
        for (request in listOf(post("/api/clubs/3/leave"), put("/api/clubs/3/managers/8"), delete("/api/clubs/3"))) {
            mvc.perform(request.with(login()).contentType(MediaType.APPLICATION_JSON).content("""{"manager":true,"confirmation":"삭제"}""")).andExpect(status().isForbidden)
        }
        verifyNoInteractions(service)
    }

    @Test
    fun `mutations use authenticated actor and pass explicit confirmation and transfer`() {
        mvc.perform(post("/api/clubs/3/leave").with(login()).with(csrf())).andExpect(status().isNoContent)
        verify(service).leave(7, 3)
        mvc.perform(put("/api/clubs/3/managers/8").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"manager":true,"transfer":true,"actorId":99}""")).andExpect(status().isNoContent)
        verify(service).changeManager(7, 3, 8, true, true)
        mvc.perform(delete("/api/clubs/3").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"confirmation":"삭제"}""")).andExpect(status().isNoContent)
        verify(service).delete(7, 3, "삭제")
    }
}
