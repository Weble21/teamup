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
