package com.teamup.teamup.controller

import com.teamup.teamup.config.SecurityConfig
import com.teamup.teamup.model.auth.SocialOAuth2UserService
import com.teamup.teamup.model.auth.SocialOidcUserService
import com.teamup.teamup.model.service.ClubMemberService
import com.teamup.teamup.view.ClubMemberResponse
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException

@WebMvcTest(ClubMemberController::class)
@Import(SecurityConfig::class)
@ActiveProfiles("oauth")
class ClubMemberControllerTest {
    @Autowired
    lateinit var mvc: MockMvc

    @MockitoBean
    lateinit var service: ClubMemberService

    @MockitoBean
    lateinit var oauth2UserService: SocialOAuth2UserService

    @MockitoBean
    lateinit var oidcUserService: SocialOidcUserService

    @MockitoBean
    lateinit var registrations: ClientRegistrationRepository

    private fun login() = oauth2Login().oauth2User(DefaultOAuth2User(
        listOf(SimpleGrantedAuthority("ROLE_USER")), mapOf("userId" to "7"), "userId",
    ))

    @Test
    fun `member list accepts active and inactive filters`() {
        for (active in listOf(true, false)) {
            `when`(service.getMembers(7, 3, active)).thenReturn(emptyList())
            mvc.perform(get("/api/clubs/3/members").with(login()).param("active", active.toString()))
                .andExpect(status().isOk).andExpect(content().json("[]"))
            verify(service).getMembers(7, 3, active)
        }
    }

    @Test
    fun `participant selection uses authenticated owner and returns verified profiles`() {
        `when`(service.validateParticipants(7, 3, listOf(5L)))
            .thenReturn(listOf(ClubMemberResponse(5, 3, "Player", "PG", 70, null, null, true)))
        mvc.perform(post("/api/clubs/3/members/validate-participants").with(login()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("""{"memberIds":[5],"ownerId":99}"""))
            .andExpect(status().isOk)
            .andExpect(content().json("""[{"id":5,"clubId":3,"name":"Player","position":"PG","rating":70,"age":null,"notes":null,"active":true}]"""))
        verify(service).validateParticipants(7, 3, listOf(5L))
    }

    @Test
    fun `empty missing or null participant list is rejected`() {
        for (body in listOf("""{"memberIds":[]}""", "{}", """{"memberIds":null}""")) {
            mvc.perform(post("/api/clubs/3/members/validate-participants").with(login()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest)
        }
        verifyNoInteractions(service)
    }

    @Test
    fun `participant validation requires authentication`() {
        mvc.perform(post("/api/clubs/3/members/validate-participants").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("""{"memberIds":[5]}"""))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(service)
    }

    @Test
    fun `participant validation requires CSRF token`() {
        mvc.perform(post("/api/clubs/3/members/validate-participants").with(login())
            .contentType(MediaType.APPLICATION_JSON).content("""{"memberIds":[5]}"""))
            .andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `invalid participant selection returns 400 from service`() {
        `when`(service.validateParticipants(7, 3, listOf(5L, 5L)))
            .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST))
        mvc.perform(post("/api/clubs/3/members/validate-participants").with(login()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("""{"memberIds":[5,5]}"""))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `deactivation returns inactive member using authenticated owner and URL IDs`() {
        `when`(service.deactivate(7, 3, 5))
            .thenReturn(ClubMemberResponse(5, 3, "Player", "PG", 70, null, null, false))
        mvc.perform(patch("/api/clubs/3/members/5/deactivate").with(login()).with(csrf())
            .param("ownerId", "99").param("clubId", "99").param("memberId", "99"))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"id":5,"clubId":3,"name":"Player","position":"PG","rating":70,"age":null,"notes":null,"active":false}"""))
        verify(service).deactivate(7, 3, 5)
    }

    @Test
    fun `deactivation requires authentication`() {
        mvc.perform(patch("/api/clubs/3/members/5/deactivate").with(csrf()))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(service)
    }

    @Test
    fun `deactivation requires CSRF token`() {
        mvc.perform(patch("/api/clubs/3/members/5/deactivate").with(login()))
            .andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `deactivation of unavailable member returns 404`() {
        `when`(service.deactivate(7, 3, 5))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"))
        mvc.perform(patch("/api/clubs/3/members/5/deactivate").with(login()).with(csrf()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `update uses authenticated owner and URL IDs and clears omitted optional fields`() {
        `when`(service.update(7, 3, 5, "Updated", "C", 90, null, null))
            .thenReturn(ClubMemberResponse(5, 3, "Updated", "C", 90, null, null, true))
        mvc.perform(put("/api/clubs/3/members/5").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Updated","position":"C","rating":90,"ownerId":99,"clubId":99,"active":false}"""))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"id":5,"clubId":3,"name":"Updated","position":"C","rating":90,"age":null,"notes":null,"active":true}"""))
        verify(service).update(7, 3, 5, "Updated", "C", 90, null, null)
    }

    @Test
    fun `member update requires authentication`() {
        mvc.perform(put("/api/clubs/3/members/5").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Updated","position":"C","rating":90}"""))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(service)
    }

    @Test
    fun `member update requires CSRF token`() {
        mvc.perform(put("/api/clubs/3/members/5").with(login()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Updated","position":"C","rating":90}"""))
            .andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `invalid member update input is rejected`() {
        val bodies = listOf(
            """{"name":" ","position":"C","rating":90}""",
            """{"name":"${"a".repeat(101)}","position":"C","rating":90}""",
            """{"name":"Updated","position":" ","rating":90}""",
            """{"name":"Updated","position":"${"a".repeat(51)}","rating":90}""",
            """{"name":"Updated","position":"C","rating":0}""",
            """{"name":"Updated","position":"C","rating":101}""",
            """{"name":"Updated","position":"C","rating":90,"age":-1}""",
            """{"name":"Updated","position":"C","rating":90,"notes":"${"a".repeat(1001)}"}""",
            """{"name":"Updated","position":"C"}""",
            """{"name":"Updated","rating":90}""",
            """{"position":"C","rating":90}""",
        )
        for (body in bodies) {
            mvc.perform(put("/api/clubs/3/members/5").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isBadRequest)
        }
        verifyNoInteractions(service)
    }

    @Test
    fun `unavailable member update returns 404`() {
        `when`(service.update(7, 3, 5, "Updated", "C", 90, null, null))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"))
        mvc.perform(put("/api/clubs/3/members/5").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Updated","position":"C","rating":90}"""))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `list uses authenticated owner and club from URL`() {
        `when`(service.getMembers(7, 3)).thenReturn(listOf(
            ClubMemberResponse(5, 3, "Player", "PG", 70, null, null, true),
        ))
        mvc.perform(get("/api/clubs/3/members").with(login()).param("ownerId", "99").param("clubId", "99"))
            .andExpect(status().isOk)
            .andExpect(content().json("""[{"id":5,"clubId":3,"name":"Player","position":"PG","rating":70,"age":null,"notes":null,"active":true}]"""))
        verify(service).getMembers(7, 3)
    }

    @Test
    fun `list returns empty array when club has no members`() {
        `when`(service.getMembers(7, 3)).thenReturn(emptyList())
        mvc.perform(get("/api/clubs/3/members").with(login()))
            .andExpect(status().isOk).andExpect(content().json("[]"))
    }

    @Test
    fun `list requires authentication`() {
        mvc.perform(get("/api/clubs/3/members")).andExpect(status().isUnauthorized)
        verifyNoInteractions(service)
    }

    @Test
    fun `list for unavailable club returns 404`() {
        `when`(service.getMembers(7, 3))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found"))
        mvc.perform(get("/api/clubs/3/members").with(login())).andExpect(status().isNotFound)
    }

    @Test
    fun `creates member with nullable optional fields and ignores ownership injection`() {
        `when`(service.create(7, 3, "Player", "PG", 70, null, null))
            .thenReturn(ClubMemberResponse(5, 3, "Player", "PG", 70, null, null, true))
        mvc.perform(post("/api/clubs/3/members").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Player","position":"PG","rating":70,"ownerId":99,"clubId":99,"active":false}"""))
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/clubs/3/members/5"))
            .andExpect(content().json("""{"id":5,"clubId":3,"name":"Player","position":"PG","rating":70,"age":null,"notes":null,"active":true}"""))
        verify(service).create(7, 3, "Player", "PG", 70, null, null)
    }

    @Test
    fun `registration requires authentication`() {
        mvc.perform(post("/api/clubs/3/members").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Player","position":"PG","rating":70}"""))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(service)
    }

    @Test
    fun `registration requires CSRF token`() {
        mvc.perform(post("/api/clubs/3/members").with(login()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Player","position":"PG","rating":70}"""))
            .andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `invalid member fields are rejected before service call`() {
        val bodies = listOf(
            """{"name":" ","position":"PG","rating":70}""",
            """{"name":"${"a".repeat(101)}","position":"PG","rating":70}""",
            """{"name":"Player","position":" ","rating":70}""",
            """{"name":"Player","position":"${"a".repeat(51)}","rating":70}""",
            """{"name":"Player","position":"PG","rating":0}""",
            """{"name":"Player","position":"PG","rating":101}""",
            """{"name":"Player","position":"PG","rating":70,"age":-1}""",
            """{"name":"Player","position":"PG","rating":70,"notes":"${"a".repeat(1001)}"}""",
            """{"name":"Player","position":"PG"}""",
            """{"name":"Player","rating":70}""",
        )
        for (body in bodies) {
            mvc.perform(post("/api/clubs/3/members").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isBadRequest)
        }
        verifyNoInteractions(service)
    }

    @Test
    fun `unavailable club returns 404`() {
        `when`(service.create(7, 3, "Player", "PG", 70, null, null))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found"))
        mvc.perform(post("/api/clubs/3/members").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Player","position":"PG","rating":70}"""))
            .andExpect(status().isNotFound)
    }
}
