package com.teamup.teamup.controller

import com.teamup.teamup.config.SecurityConfig
import com.teamup.teamup.model.auth.SocialOAuth2UserService
import com.teamup.teamup.model.auth.SocialOidcUserService
import com.teamup.teamup.model.service.AttendanceVoteService
import com.teamup.teamup.model.service.VoteCommentView
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import java.time.Instant

@WebMvcTest(AttendanceVoteController::class)
@Import(SecurityConfig::class)
@ActiveProfiles("oauth")
class AttendanceVoteControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: AttendanceVoteService
    @MockitoBean lateinit var oauth2UserService: SocialOAuth2UserService
    @MockitoBean lateinit var oidcUserService: SocialOidcUserService
    @MockitoBean lateinit var registrations: ClientRegistrationRepository

    private fun login() = oauth2Login().oauth2User(DefaultOAuth2User(
        listOf(SimpleGrantedAuthority("ROLE_USER")), mapOf("userId" to "7"), "userId",
    ))

    @Test
    fun `closing uses authenticated user and requires csrf`() {
        mvc.perform(post("/api/clubs/3/attendance-votes/4/close").with(login()).with(csrf()))
            .andExpect(status().isNoContent)
        verify(service).close(3, 4, 7)
    }

    @Test
    fun `closing without login or csrf is rejected`() {
        mvc.perform(post("/api/clubs/3/attendance-votes/4/close").with(csrf())).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/clubs/3/attendance-votes/4/close").with(login())).andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `comment uses logged in identity and ignores a supplied author name`() {
        `when`(service.addComment(3, 4, 7, "hello")).thenReturn(VoteCommentView(5, "Real name", "hello", Instant.parse("2026-01-01T00:00:00Z")))
        mvc.perform(post("/api/clubs/3/attendance-votes/4/comments").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"content":"hello","authorName":"Spoof"}"""))
            .andExpect(status().isCreated).andExpect(jsonPath("$.authorName").value("Real name"))
        verify(service).addComment(3, 4, 7, "hello")
    }

    @Test
    fun `comment requires login csrf and valid content`() {
        val path = "/api/clubs/3/attendance-votes/4/comments"
        val valid = """{"content":"hello"}"""
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isUnauthorized)
        mvc.perform(post(path).with(login()).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isForbidden)
        mvc.perform(post(path).with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""{"content":" "}""")).andExpect(status().isBadRequest)
        verifyNoInteractions(service)
    }

    @Test
    fun `team setup requires login and passes the principal id`() {
        val path = "/api/clubs/3/attendance-votes/4/team-setup"
        mvc.perform(get(path)).andExpect(status().isUnauthorized)
        `when`(service.teamSetup(3, 4, 7)).thenReturn(com.teamup.teamup.model.service.VoteTeamSetup(listOf(9), emptyList(), null))
        mvc.perform(get(path).with(login())).andExpect(status().isOk).andExpect(jsonPath("$.memberIds[0]").value(9))
        verify(service).teamSetup(3, 4, 7)
    }
}
