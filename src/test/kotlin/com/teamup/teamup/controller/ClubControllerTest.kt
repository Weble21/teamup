package com.teamup.teamup.controller

import com.teamup.teamup.config.SecurityConfig
import com.teamup.teamup.model.auth.SocialOAuth2UserService
import com.teamup.teamup.model.auth.SocialOidcUserService
import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.service.ClubService
import com.teamup.teamup.view.ClubResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.http.HttpStatus
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException

@WebMvcTest(ClubController::class)
@Import(SecurityConfig::class)
@ActiveProfiles("oauth")
class ClubControllerTest {
    @Autowired
    lateinit var mvc: MockMvc

    @MockitoBean
    lateinit var clubService: ClubService

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
    fun `update returns changed club and uses logged in owner`() {
        `when`(clubService.update(7, 3, "Updated", SportType.FUTSAL))
            .thenReturn(ClubResponse(3, "Updated", SportType.FUTSAL, 7))
        mvc.perform(put("/api/clubs/3").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Updated","sportType":"FUTSAL","ownerId":99}"""))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"id":3,"name":"Updated","sportType":"FUTSAL","ownerId":7}"""))
        verify(clubService).update(7, 3, "Updated", SportType.FUTSAL)
    }

    @Test
    fun `update rejects unauthenticated request with valid CSRF`() {
        mvc.perform(put("/api/clubs/3").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Updated","sportType":"FUTSAL"}"""))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(clubService)
    }

    @Test
    fun `update rejects missing CSRF`() {
        mvc.perform(put("/api/clubs/3").with(login()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Updated","sportType":"FUTSAL"}"""))
            .andExpect(status().isForbidden)
        verifyNoInteractions(clubService)
    }

    @Test
    fun `update rejects invalid or incomplete input`() {
        val bodies = listOf(
            """{"name":"   ","sportType":"FUTSAL"}""",
            """{"name":"${"a".repeat(101)}","sportType":"FUTSAL"}""",
            """{"name":"Updated","sportType":"UNKNOWN"}""",
            """{"name":"Updated"}""",
            """{"sportType":"FUTSAL"}""",
            """{"name":null,"sportType":"FUTSAL"}""",
            """{"name":"Updated","sportType":null}""",
        )
        for (body in bodies) {
            mvc.perform(put("/api/clubs/3").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isBadRequest)
        }
        verifyNoInteractions(clubService)
    }

    @Test
    fun `update of unavailable club returns 404`() {
        `when`(clubService.update(7, 3, "Updated", SportType.FUTSAL))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found"))
        mvc.perform(put("/api/clubs/3").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Updated","sportType":"FUTSAL"}"""))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `detail uses authenticated owner and ignores requested owner ID`() {
        `when`(clubService.getMyClub(7, 3)).thenReturn(ClubResponse(3, "Weekend", SportType.BASKETBALL, 7))
        mvc.perform(get("/api/clubs/3").with(login()).param("ownerId", "99"))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"id":3,"name":"Weekend","sportType":"BASKETBALL","ownerId":7}"""))
        verify(clubService).getMyClub(7, 3)
    }

    @Test
    fun `detail rejects unauthenticated requests`() {
        mvc.perform(get("/api/clubs/3")).andExpect(status().isUnauthorized)
        verifyNoInteractions(clubService)
    }

    @Test
    fun `unavailable club returns 404`() {
        `when`(clubService.getMyClub(7, 3))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found"))
        mvc.perform(get("/api/clubs/3").with(login())).andExpect(status().isNotFound)
    }

    @Test
    fun `non numeric club ID returns 400 without calling service`() {
        mvc.perform(get("/api/clubs/invalid").with(login())).andExpect(status().isBadRequest)
        verifyNoInteractions(clubService)
    }

    @Test
    fun `lists authenticated owners clubs and ignores requested owner ID`() {
        `when`(clubService.getMyClubs(7)).thenReturn(listOf(
            ClubResponse(3, "Weekend", SportType.BASKETBALL, 7),
        ))
        mvc.perform(get("/api/clubs").with(login()).param("ownerId", "99"))
            .andExpect(status().isOk)
            .andExpect(content().json("""[{"id":3,"name":"Weekend","sportType":"BASKETBALL","ownerId":7}]"""))
        verify(clubService).getMyClubs(7)
    }

    @Test
    fun `list returns empty JSON array when user has no clubs`() {
        `when`(clubService.getMyClubs(7)).thenReturn(emptyList())
        mvc.perform(get("/api/clubs").with(login()))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
    }

    @Test
    fun `list rejects unauthenticated requests`() {
        mvc.perform(get("/api/clubs")).andExpect(status().isUnauthorized)
        verifyNoInteractions(clubService)
    }

    @Test
    fun `creates club using authenticated owner and returns 201 and location`() {
        `when`(clubService.create(7, "Weekend", SportType.BASKETBALL))
            .thenReturn(ClubResponse(3, "Weekend", SportType.BASKETBALL, 7))
        mvc.perform(post("/api/clubs").with(login()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Weekend","sportType":"BASKETBALL","ownerId":99}"""))
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/clubs/3"))
            .andExpect(content().json("""{"id":3,"name":"Weekend","sportType":"BASKETBALL","ownerId":7}"""))
        verify(clubService).create(7, "Weekend", SportType.BASKETBALL)
    }

    @Test
    fun `unauthenticated request with valid CSRF is rejected`() {
        mvc.perform(post("/api/clubs").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Weekend","sportType":"BASKETBALL"}"""))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(clubService)
    }

    @Test
    fun `authenticated request without CSRF is rejected`() {
        mvc.perform(post("/api/clubs").with(login()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Weekend","sportType":"BASKETBALL"}"""))
            .andExpect(status().isForbidden)
        verifyNoInteractions(clubService)
    }

    @Test
    fun `blank and oversized names are rejected`() {
        for (name in listOf("", "   ", "a".repeat(101))) {
            mvc.perform(post("/api/clubs").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","sportType":"BASKETBALL"}"""))
                .andExpect(status().isBadRequest)
        }
        verifyNoInteractions(clubService)
    }

    @Test
    fun `unknown missing or null sport and missing or null name are rejected`() {
        val bodies = listOf(
            """{"name":"Weekend","sportType":"UNKNOWN"}""",
            """{"name":"Weekend"}""",
            """{"name":"Weekend","sportType":null}""",
            """{"sportType":"BASKETBALL"}""",
            """{"name":null,"sportType":"BASKETBALL"}""",
        )
        for (body in bodies) {
            mvc.perform(post("/api/clubs").with(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isBadRequest)
        }
        verifyNoInteractions(clubService)
    }
}
