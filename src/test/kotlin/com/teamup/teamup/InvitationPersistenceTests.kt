package com.teamup.teamup

import com.teamup.teamup.model.entity.Club
import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.entity.User
import com.teamup.teamup.model.repository.ClubRepository
import com.teamup.teamup.model.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertNotEquals

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:invitation-http;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
    "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:social-login-schema.sql",
    "GOOGLE_CLIENT_ID=test", "GOOGLE_CLIENT_SECRET=test",
    "KAKAO_CLIENT_ID=test", "KAKAO_CLIENT_SECRET=test",
    "NAVER_CLIENT_ID=test", "NAVER_CLIENT_SECRET=test",
])
@AutoConfigureMockMvc
@ActiveProfiles("oauth")
class InvitationPersistenceTests {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var clubs: ClubRepository
    private val mapper = ObjectMapper()

    private fun login(id: Long) = oauth2Login().oauth2User(
        DefaultOAuth2User(listOf(SimpleGrantedAuthority("ROLE_USER")), mapOf("userId" to id.toString()), "userId"),
    )

    @Test
    fun `two device sessions share persisted link and honor rotation revoke and owner access`() {
        val owner = users.save(User("HTTP Owner")); val member = users.save(User("HTTP Member"))
        val clubId = clubs.save(Club(owner, "HTTP Club", SportType.BASKETBALL)).id!!
        val path = "/api/clubs/$clubId/invitation"
        val deviceA = MockHttpSession(); val deviceB = MockHttpSession()

        fun issue(): String {
            val response = mvc.perform(post(path).session(deviceA).with(login(owner.id!!)).with(csrf()))
                .andExpect(status().isOk).andReturn().response.contentAsString
            return mapper.readTree(response).path("token").asString()
        }
        fun read(expected: String) {
            mvc.perform(get(path).session(deviceB).with(login(owner.id!!)))
                .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").value(expected)).andExpect(jsonPath("$.requiresReissue").value(false))
        }

        val first = issue(); read(first); read(first)
        mvc.perform(put("/api/users/me/sport-profiles/BASKETBALL").with(login(member.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"HTTP Player","position":"C","rating":70}""")).andExpect(status().isOk)
        mvc.perform(post("/api/invitations/join").with(login(member.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"token":"$first"}""")).andExpect(status().isOk)
        // Membership does not grant permission to retrieve the invitation secret.
        mvc.perform(get(path).with(login(member.id!!))).andExpect(status().isNotFound)
        mvc.perform(get(path)).andExpect(status().isUnauthorized)
        mvc.perform(post(path).with(login(owner.id!!))).andExpect(status().isForbidden)

        val second = issue(); assertNotEquals(first, second); read(second)
        mvc.perform(post("/api/invitations/join").with(login(member.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"token":"$first"}""")).andExpect(status().isNotFound)
        mvc.perform(delete(path).session(deviceA).with(login(owner.id!!)).with(csrf())).andExpect(status().isNoContent)
        mvc.perform(get(path).session(deviceB).with(login(owner.id!!)))
            .andExpect(status().isOk).andExpect(jsonPath("$.token").isEmpty).andExpect(jsonPath("$.requiresReissue").value(false))
        mvc.perform(post("/api/invitations/join").with(login(member.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"token":"$second"}""")).andExpect(status().isNotFound)
    }

    @Test
    fun `login preview registration acceptance and duplicate acceptance enforce onboarding order`() {
        val owner = users.save(User("Onboarding Owner")); val member = users.save(User("Onboarding Member"))
        val clubId = clubs.save(Club(owner, "Onboarding Club", SportType.LEAGUE_OF_LEGENDS)).id!!
        val tokenResponse = mvc.perform(post("/api/clubs/$clubId/invitation").with(login(owner.id!!)).with(csrf()))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val body = """{"token":"${mapper.readTree(tokenResponse).path("token").asString()}"}"""
        val previewPath = "/api/invitations/preview"
        mvc.perform(post(previewPath).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
        mvc.perform(post(previewPath).with(login(member.id!!)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden)
        mvc.perform(post(previewPath).with(login(member.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.clubId").value(clubId)).andExpect(jsonPath("$.sportType").value("LEAGUE_OF_LEGENDS"))
            .andExpect(jsonPath("$.alreadyJoined").value(false)).andExpect(jsonPath("$.profileRegistered").value(false))
        mvc.perform(post("/api/invitations/join").with(login(member.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict)
        mvc.perform(put("/api/users/me/sport-profiles/LEAGUE_OF_LEGENDS").with(login(member.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Summoner","position":"MID","rating":60,"age":23,"notes":"Emerald"}""")).andExpect(status().isOk)
        mvc.perform(post(previewPath).with(login(member.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andExpect(jsonPath("$.profileRegistered").value(true)).andExpect(jsonPath("$.alreadyJoined").value(false))
        mvc.perform(post("/api/invitations/join").with(login(member.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andExpect(jsonPath("$.alreadyJoined").value(false))
        mvc.perform(get("/api/clubs/$clubId/members").with(login(member.id!!)))
            .andExpect(status().isOk).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Summoner")).andExpect(jsonPath("$[0].rating").value(60))
            .andExpect(jsonPath("$[0].mine").value(true))
        mvc.perform(post(previewPath).with(login(member.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andExpect(jsonPath("$.alreadyJoined").value(true))
        mvc.perform(post("/api/invitations/join").with(login(member.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andExpect(jsonPath("$.alreadyJoined").value(true))
        mvc.perform(get("/api/clubs/$clubId/members").with(login(member.id!!)))
            .andExpect(status().isOk).andExpect(jsonPath("$.length()").value(1))
    }
}
