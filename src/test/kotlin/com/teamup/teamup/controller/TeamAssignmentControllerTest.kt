package com.teamup.teamup.controller

import com.teamup.teamup.config.SecurityConfig
import com.teamup.teamup.model.auth.SocialOAuth2UserService
import com.teamup.teamup.model.auth.SocialOidcUserService
import com.teamup.teamup.model.service.TeamAssignmentService
import com.teamup.teamup.model.service.TeamAnalysisService
import com.teamup.teamup.model.team.TeamParticipant
import com.teamup.teamup.view.AssignedTeamResponse
import com.teamup.teamup.view.TeamAssignmentResponse
import com.teamup.teamup.view.TeamAnalysisResponse
import com.teamup.teamup.view.TeamInsightResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException

@WebMvcTest(TeamAssignmentController::class)
@Import(SecurityConfig::class)
@ActiveProfiles("oauth")
class TeamAssignmentControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: TeamAssignmentService
    @MockitoBean lateinit var analysis: TeamAnalysisService
    @MockitoBean lateinit var oauth2UserService: SocialOAuth2UserService
    @MockitoBean lateinit var oidcUserService: SocialOidcUserService
    @MockitoBean lateinit var registrations: ClientRegistrationRepository

    private fun login() = oauth2Login().oauth2User(DefaultOAuth2User(
        listOf(SimpleGrantedAuthority("ROLE_USER")), mapOf("userId" to "7"), "userId",
    ))

    @Test
    fun `analysis requires auth and CSRF and uses the session user`() {
        val body = """{"memberIds":[5,8],"teams":[[5],[8]]}"""
        mvc.perform(post("/api/clubs/3/team-assignments/analyze").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/clubs/3/team-assignments/analyze").with(login())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden)
        verifyNoInteractions(analysis)
        `when`(analysis.analyze(7, 3, listOf(5L, 8L), listOf(listOf(5L), listOf(8L))))
            .thenReturn(TeamAnalysisResponse(listOf(TeamInsightResponse(1, 5, "높은 능력치", "균형 잡힌 팀"))))
        mvc.perform(post("/api/clubs/3/team-assignments/analyze").with(login()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk)
        verify(analysis).analyze(7, 3, listOf(5L, 8L), listOf(listOf(5L), listOf(8L)))
    }

    @Test
    fun `generation returns candidate and uses authenticated owner rather than body owner`() {
        `when`(service.generate(7, 3, listOf(5L, 8L), 2)).thenReturn(TeamAssignmentResponse(3, listOf(
            AssignedTeamResponse(1, listOf(TeamParticipant(5, "First", "PG", 70)), 70, "70.00".toBigDecimal()),
            AssignedTeamResponse(2, listOf(TeamParticipant(8, "Second", "C", 80)), 80, "80.00".toBigDecimal()),
        )))
        mvc.perform(post("/api/clubs/3/team-assignments").with(login()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"memberIds":[5,8],"teamCount":2,"ownerId":99}"""))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"clubId":3,"teams":[{"teamNumber":1,"members":[{"memberId":5,"name":"First","position":"PG","rating":70}],"totalRating":70,"averageRating":70.00},{"teamNumber":2,"members":[{"memberId":8,"name":"Second","position":"C","rating":80}],"totalRating":80,"averageRating":80.00}]}"""))
        verify(service).generate(7, 3, listOf(5L, 8L), 2)
    }

    @Test
    fun `generation requires authentication`() {
        mvc.perform(post("/api/clubs/3/team-assignments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"memberIds":[5,8],"teamCount":2}"""))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(service)
    }

    @Test
    fun `generation requires CSRF token`() {
        mvc.perform(post("/api/clubs/3/team-assignments").with(login()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"memberIds":[5,8],"teamCount":2}"""))
            .andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `invalid team counts and empty or missing member lists are rejected`() {
        for (body in listOf(
            """{"memberIds":[5,8],"teamCount":1}""",
            """{"memberIds":[5,8]}""",
            """{"memberIds":[],"teamCount":2}""",
            """{"teamCount":2}""",
            """{"memberIds":null,"teamCount":2}""",
        )) {
            mvc.perform(post("/api/clubs/3/team-assignments").with(login()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest)
        }
        verifyNoInteractions(service)
    }

    @Test
    fun `generation of unavailable club returns 404`() {
        `when`(service.generate(7, 3, listOf(5L, 8L), 2))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found"))
        mvc.perform(post("/api/clubs/3/team-assignments").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"memberIds":[5,8],"teamCount":2}"""))
            .andExpect(status().isNotFound)
    }
}
