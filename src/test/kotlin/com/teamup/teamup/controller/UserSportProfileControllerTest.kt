package com.teamup.teamup.controller

import com.teamup.teamup.config.SecurityConfig
import com.teamup.teamup.model.auth.SocialOAuth2UserService
import com.teamup.teamup.model.auth.SocialOidcUserService
import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.service.NewClubMember
import com.teamup.teamup.model.service.UserSportProfileService
import com.teamup.teamup.view.UserSportProfileResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(UserSportProfileController::class)
@Import(SecurityConfig::class)
@ActiveProfiles("oauth")
class UserSportProfileControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var profiles: UserSportProfileService
    @MockitoBean lateinit var oauth2UserService: SocialOAuth2UserService
    @MockitoBean lateinit var oidcUserService: SocialOidcUserService
    @MockitoBean lateinit var registrations: ClientRegistrationRepository

    private val principal = DefaultOAuth2User(
        listOf(SimpleGrantedAuthority("ROLE_USER")), mapOf("userId" to "7"), "userId",
    )

    @Test
    fun `only authenticated user can list profiles`() {
        mvc.perform(get("/api/users/me/sport-profiles")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/users/me/sport-profiles").with(oauth2Login().oauth2User(principal)))
            .andExpect(status().isOk)
        verify(profiles).list(7)
    }

    @Test
    fun `save requires CSRF and uses authenticated user ID`() {
        val input = NewClubMember("Alice", "Guard", 70, null, null)
        val body = """{"name":"Alice","position":"Guard","rating":70}"""
        `when`(profiles.save(7, SportType.BASKETBALL, input))
            .thenReturn(UserSportProfileResponse(SportType.BASKETBALL, "Alice", "Guard", 70, null, null))
        mvc.perform(put("/api/users/me/sport-profiles/BASKETBALL").with(oauth2Login().oauth2User(principal))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden)
        verifyNoInteractions(profiles)
        mvc.perform(put("/api/users/me/sport-profiles/BASKETBALL").with(oauth2Login().oauth2User(principal)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk)
        verify(profiles).save(7, SportType.BASKETBALL, input)
    }
}
