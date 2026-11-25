package com.teamup.teamup

import com.teamup.teamup.model.entity.Club
import com.teamup.teamup.model.entity.ClubMember
import com.teamup.teamup.model.entity.SportType
import com.teamup.teamup.model.entity.User
import com.teamup.teamup.model.repository.ClubRepository
import com.teamup.teamup.model.repository.ClubMemberRepository
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
    @Autowired lateinit var members: ClubMemberRepository
    private val mapper = ObjectMapper()

    private fun login(id: Long) = oauth2Login().oauth2User(
        DefaultOAuth2User(listOf(SimpleGrantedAuthority("ROLE_USER")), mapOf("userId" to id.toString()), "userId"),
    )

    @Test
    fun `club creation registers the authenticated owner as a player`() {
        val owner = users.save(User("Create owner"))
        val result = mvc.perform(post("/api/clubs").with(login(owner.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Created club","sportType":"BASKETBALL","ownerProfile":{"name":"Captain","position":"PG","rating":70,"age":25,"notes":"Captain"}}"""))
            .andExpect(status().isCreated).andExpect(jsonPath("$.activeMemberCount").value(1)).andReturn().response.contentAsString
        val clubId = mapper.readTree(result).path("id").asLong()
        mvc.perform(get("/api/clubs/$clubId/members").with(login(owner.id!!)))
            .andExpect(status().isOk).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Captain")).andExpect(jsonPath("$[0].mine").value(true))
            .andExpect(jsonPath("$[0].position").value("PG"))
    }

    @Test
    fun `invited account sees and claims existing player while other club members can read all details`() {
        val owner = users.save(User("Roster owner")); val first = users.save(User("First")); val second = users.save(User("Second"))
        val club = clubs.save(Club(owner, "Roster", SportType.FUTSAL))
        val player = members.save(ClubMember(club, "Existing", "FW", 70, 25, "roster note"))
        val tokenResponse = mvc.perform(post("/api/clubs/${club.id}/invitation").with(login(owner.id!!)).with(csrf()))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val token = mapper.readTree(tokenResponse).path("token").asString()
        val preview = """{"token":"$token"}"""
        mvc.perform(post("/api/invitations/preview").with(login(first.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(preview))
            .andExpect(status().isOk).andExpect(jsonPath("$.candidates[0].id").value(player.id))
        mvc.perform(post("/api/invitations/join").with(login(first.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"token":"$token","memberId":${player.id}}""")).andExpect(status().isOk)
        mvc.perform(post("/api/invitations/join").with(login(second.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"token":"$token","profile":{"name":"New","position":"GK","rating":50}}""")).andExpect(status().isOk)
        mvc.perform(get("/api/clubs/${club.id}/members").with(login(second.id!!)))
            .andExpect(status().isOk).andExpect(jsonPath("$[?(@.name == 'Existing')].age").value(25))
            .andExpect(jsonPath("$[?(@.name == 'Existing')].notes").value("roster note"))
        mvc.perform(put("/api/clubs/${club.id}/members/${player.id}").with(login(second.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Other","position":"FW","rating":70}""")).andExpect(status().isNotFound)
    }

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
        mvc.perform(post("/api/invitations/join").with(login(member.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"token":"$first","profile":{"name":"HTTP Player","position":"C","rating":70}}""")).andExpect(status().isOk)
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
            .andExpect(jsonPath("$.alreadyJoined").value(false)).andExpect(jsonPath("$.candidates.length()").value(0))
        mvc.perform(post("/api/invitations/join").with(login(member.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
        mvc.perform(post("/api/invitations/join").with(login(member.id!!)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body.dropLast(1) + ",\"profile\":{\"name\":\"Summoner\",\"position\":\"MID\",\"rating\":60,\"age\":23,\"notes\":\"Emerald\"}}"))
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
