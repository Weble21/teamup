package com.teamup.teamup.model.service

import com.teamup.teamup.model.team.TeamParticipant
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.ArgumentMatchers.anyList
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NaturalConditionServiceTest {
    private val embeddings = mock(EmbeddingConstraintClient::class.java)
    private val service = NaturalConditionService(embeddings)
    private val players = listOf(
        TeamParticipant(1, "민수", "FW", 80),
        TeamParticipant(2, "철수", "DF", 70),
        TeamParticipant(3, "영희", "FW", 60),
    )

    private val koreanPlayers = listOf(
        TeamParticipant(11, "이정현", "PG", 80),
        TeamParticipant(12, "이현중", "SF", 70),
    )

    private val englishPlayers = listOf(
        TeamParticipant(21, "Jaden Hardy", "SG", 60),
        TeamParticipant(22, "Jarred Vanderbilt", "PF", 70),
    )

    @Test
    fun `English surname words in the reported sentence resolve and are masked`() {
        `when`(embeddings.classify(anyList())).thenReturn(listOf(ClassifiedClause("PAIR_RELATION", 0.9)))
        val result = service.interpret("Hardy와 Vanderbilt는 같은 팀", englishPlayers)
        assertEquals(listOf(21L to 22L), result.together)
        assertEquals(listOf("Jaden Hardy·Jarred Vanderbilt 같은 팀"), result.descriptions)
        verify(embeddings).classify(listOf("선수와 선수는 같은 팀"))
    }

    @Test
    fun `English given names surnames and full names ignore casing`() {
        `when`(embeddings.classify(anyList())).thenReturn(listOf(ClassifiedClause("PAIR_RELATION", 0.9)))
        for (clause in listOf("hardy와 VANDERBILT는 다른 팀", "Jaden과 Jarred는 다른 팀", "jaden hardy와 JARRED VANDERBILT는 다른 팀")) {
            assertEquals(listOf(21L to 22L), service.interpret(clause, englishPlayers).apart)
        }
    }

    @Test
    fun `shared English surname requires full names and only attending players participate`() {
        val attending = englishPlayers + TeamParticipant(23, "Other Hardy", "C", 60)
        val failure = assertFailsWith<ResponseStatusException> { service.interpret("Hardy와 Vanderbilt는 같은 팀", attending) }
        assertTrue(failure.reason!!.contains("전체 이름"))
        verifyNoInteractions(embeddings)
        `when`(embeddings.classify(anyList())).thenReturn(listOf(ClassifiedClause("PAIR_RELATION", 0.9)))
        assertEquals(listOf(21L to 22L), service.interpret("Jaden Hardy와 Vanderbilt는 같은 팀", attending).together)
        assertEquals(listOf(21L to 22L), service.interpret("Hardy와 Vanderbilt는 같은 팀", englishPlayers).together)
    }

    @Test
    fun `partial words and unselected English members are not guessed`() {
        `when`(embeddings.classify(anyList())).thenReturn(listOf(ClassifiedClause("PAIR_RELATION", 0.9)))
        for (clause in listOf("Hard와 Vanderbilt는 같은 팀", "Hardyman과 Vanderbilt는 같은 팀", "SomeHardy와 Vanderbilt는 같은 팀")) {
            assertFailsWith<ResponseStatusException> { service.interpret(clause, englishPlayers) }
        }
        assertFailsWith<ResponseStatusException> { service.interpret("Hardy와 Vanderbilt는 같은 팀", englishPlayers.take(1)) }
    }

    @Test
    fun `Latin names preserve apostrophes and accents in surname matching`() {
        `when`(embeddings.classify(anyList())).thenReturn(listOf(ClassifiedClause("PAIR_RELATION", 0.9)))
        val attending = listOf(TeamParticipant(31, "Shaquille O'Neal", "C", 100), TeamParticipant(32, "Luka Dončić", "PG", 100))
        assertEquals(listOf(31L to 32L), service.interpret("O'Neal과 Dončić는 같은 팀", attending).together)
        verify(embeddings).classify(listOf("선수과 선수는 같은 팀"))
    }

    @Test
    fun `unique Korean given names resolve to attending members and are masked`() {
        `when`(embeddings.classify(anyList())).thenReturn(listOf(ClassifiedClause("PAIR_RELATION", 0.9)))
        val result = service.interpret("현중과 정현은 다른 팀", koreanPlayers)
        assertEquals(listOf(11L to 12L), result.apart)
        assertEquals(listOf("이정현·이현중 다른 팀"), result.descriptions)
        verify(embeddings).classify(listOf("선수과 선수은 다른 팀"))
    }

    @Test
    fun `ambiguous given names require full names before classification`() {
        val attending = koreanPlayers + TeamParticipant(13, "김정현", "C", 60)
        val failure = assertFailsWith<ResponseStatusException> { service.interpret("현중과 정현은 다른 팀", attending) }
        assertTrue(failure.reason!!.contains("전체 이름"))
        verifyNoInteractions(embeddings)
    }

    @Test
    fun `full names disambiguate identical given names without matching embedded aliases`() {
        `when`(embeddings.classify(anyList())).thenReturn(listOf(ClassifiedClause("PAIR_RELATION", 0.9)))
        val attending = koreanPlayers + TeamParticipant(13, "김정현", "C", 60)
        val result = service.interpret("이정현과 이현중은 같은 팀", attending)
        assertEquals(listOf(11L to 12L), result.together)
        verify(embeddings).classify(listOf("선수과 선수은 같은 팀"))
        assertEquals(listOf(11L to 12L), service.interpret("이정현과이현중은 같은 팀", attending).together)
    }

    @Test
    fun `only selected participants determine whether a short name is unique`() {
        `when`(embeddings.classify(anyList())).thenReturn(listOf(ClassifiedClause("PAIR_RELATION", 0.9)))
        val result = service.interpret("이현중과 정현은 같은 팀", koreanPlayers)
        assertEquals(listOf(11L to 12L), result.together)
        assertFailsWith<ResponseStatusException> { service.interpret("현중과 김정현은 같은 팀", koreanPlayers) }
        assertFailsWith<ResponseStatusException> { service.interpret("현중과 정현수는 같은 팀", koreanPlayers) }
    }

    @Test
    fun `repeated names and particles resolve each member once`() {
        `when`(embeddings.classify(anyList())).thenReturn(listOf(ClassifiedClause("PAIR_RELATION", 0.9)))
        val result = service.interpret("정현이랑 이현중은 같은 팀, 이정현도 포함", koreanPlayers)
        assertEquals(listOf(11L to 12L), result.together)
        verify(embeddings).classify(listOf("선수이랑 선수은 같은 팀, 선수도 포함"))
    }

    @Test
    fun `registered full name has precedence and duplicate full names are rejected`() {
        `when`(embeddings.classify(anyList())).thenReturn(listOf(ClassifiedClause("PAIR_RELATION", 0.9)))
        val attending = koreanPlayers + TeamParticipant(13, "정현", "C", 60)
        assertEquals(listOf(12L to 13L), service.interpret("정현과 이현중은 같은 팀", attending).together)
        val duplicate = koreanPlayers + TeamParticipant(14, "이정현", "C", 60)
        assertFailsWith<ResponseStatusException> { service.interpret("이정현과 이현중은 같은 팀", duplicate) }
    }

    @Test
    fun `interprets pair polarity and position balance`() {
        `when`(embeddings.classify(anyList())).thenReturn(listOf(
            ClassifiedClause("PAIR_RELATION", 0.9),
            ClassifiedClause("PAIR_RELATION", 0.9),
            ClassifiedClause("POSITION_BALANCE", 0.9),
        ))
        val result = service.interpret("민수와 철수는 같은 팀\n철수와 영희는 다른 팀\n포지션을 골고루 나눠줘", players)
        assertEquals(listOf(1L to 2L), result.together)
        assertEquals(listOf(2L to 3L), result.apart)
        assertEquals(true, result.balancePositions)
    }

    @Test
    fun `rejects missing player and contradictory pair conditions`() {
        `when`(embeddings.classify(anyList())).thenReturn(listOf(ClassifiedClause("PAIR_RELATION", 0.9)))
        assertFailsWith<ResponseStatusException> { service.interpret("민수와 미등록 선수를 같은 팀", players) }
        `when`(embeddings.classify(anyList())).thenReturn(List(2) { ClassifiedClause("PAIR_RELATION", 0.9) })
        assertFailsWith<ResponseStatusException> { service.interpret("민수와 철수는 같은 팀\n민수와 철수는 다른 팀", players) }
    }

    @Test
    fun `negated same team means different team and ambiguous negation is rejected`() {
        `when`(embeddings.classify(anyList())).thenReturn(listOf(ClassifiedClause("PAIR_RELATION", 0.9)))
        assertEquals(listOf(1L to 2L), service.interpret("민수와 철수는 같은 팀에 넣지 마", players).apart)
        assertFailsWith<ResponseStatusException> { service.interpret("민수와 철수는 다른 팀 말고 같은 팀", players) }
    }

    @Test
    fun `recognizes detailed football and basketball position terms`() {
        `when`(embeddings.classify(anyList())).thenReturn(List(2) { ClassifiedClause("POSITION_BALANCE", 0.9) })
        val result = service.interpret("LWB, MF, LW, ST를 고르게 나눠줘\n포인트가드와 센터를 균형 있게 나눠줘", players)
        assertEquals(true, result.balancePositions)
    }
}
