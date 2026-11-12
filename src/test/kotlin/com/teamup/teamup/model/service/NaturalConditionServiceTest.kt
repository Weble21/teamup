package com.teamup.teamup.model.service

import com.teamup.teamup.model.team.TeamParticipant
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.anyList
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NaturalConditionServiceTest {
    private val embeddings = mock(EmbeddingConstraintClient::class.java)
    private val service = NaturalConditionService(embeddings)
    private val players = listOf(
        TeamParticipant(1, "민수", "FW", 80),
        TeamParticipant(2, "철수", "DF", 70),
        TeamParticipant(3, "영희", "FW", 60),
    )

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
