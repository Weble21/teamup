package com.teamup.teamup.model.service

import com.teamup.teamup.model.team.TeamParticipant
import com.teamup.teamup.model.team.TeamConstraints
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TeamBalancerTest {
    private val balancer = TeamBalancer()

    @Test
    fun `candidate preference respects strict 0 8 boundary and position priority`() {
        val even = TeamBalancer.CandidateScore(0.79, 0.0)
        val perfectScore = TeamBalancer.CandidateScore(0.0, 1.0)
        assertTrue(even.betterThan(perfectScore))
        assertTrue(perfectScore.betterThan(TeamBalancer.CandidateScore(0.8, 0.0)))
        assertTrue(TeamBalancer.CandidateScore(0.8, 1.0).betterThan(TeamBalancer.CandidateScore(0.81, 0.0)))
        assertTrue(TeamBalancer.CandidateScore(0.8, 0.0).betterThan(TeamBalancer.CandidateScore(0.8, 1.0)))
    }

    @Test
    fun `candidate uses unrounded team averages and normalized positions`() {
        val a = (1..11).map { TeamParticipant(it.toLong(), "A", if (it == 1) "PG" else "C", 50) }
        val b = (12..21).map { TeamParticipant(it.toLong(), "B", if (it == 12) "포인트가드" else "센터", 50) }
        val score = balancer.scoreCandidate(listOf(a, b))
        assertEquals(0.0, score.difference, 1e-9)
        assertTrue(score.positionPenalty < 0.01)
    }

    @Test
    fun `search improves first feasible result and can finish before deadline`() {
        val players = listOf(100, 90, 80, 70, 20, 10).mapIndexed { i, rating -> TeamParticipant(i + 1L, "P", "C", rating) }
        balancer.clock = { 0L }
        val teams = balancer.balance(players, 2, TeamConstraints(together = listOf(1L to 6L)))
        val all = players.indices.flatMap { a -> (a + 1 until players.size).flatMap { b -> (b + 1 until players.size).map { c -> listOf(players[a], players[b], players[c]) } } }
            .filter { team -> team.any { it.memberId == 1L } == team.any { it.memberId == 6L } }
        val best = all.map { team -> balancer.scoreCandidate(listOf(team, players - team.toSet())) }.reduce { a, b -> if (a.betterThan(b)) a else b }
        val actual = balancer.scoreCandidate(teams)
        assertEquals(best.difference, actual.difference, 1e-9)
        assertEquals(best.positionPenalty, actual.positionPenalty, 1e-9)
    }

    @Test
    fun `timeout without candidate differs from conflicting conditions`() {
        val players = (1..4).map { TeamParticipant(it.toLong(), "P", "C", 50) }
        var time = 0L
        balancer.budgetNanos = 1
        balancer.clock = { time++ }
        assertFailsWith<TeamSearchTimeoutException> { balancer.balance(players, 2, TeamConstraints()) }
        assertFailsWith<IllegalArgumentException> { balancer.balance(players, 2, TeamConstraints(together = listOf(1L to 2L), apart = listOf(1L to 2L))) }
    }

    @Test
    fun `deadline returns the best valid candidate found so far`() {
        val players = (1..6).map { TeamParticipant(it.toLong(), "P", "C", it * 10) }
        var time = 0L
        balancer.budgetNanos = 20
        balancer.clock = { time++ }
        val teams = balancer.balance(players, 2, TeamConstraints(apart = listOf(1L to 2L)))
        assertEquals(players.map { it.memberId }.sorted(), teams.flatten().map { it.memberId }.sorted())
        assertTrue(teams.none { team -> team.any { it.memberId == 1L } && team.any { it.memberId == 2L } })
        assertTrue(time >= balancer.budgetNanos)
    }

    @Test
    fun `position balance spreads basketball aliases across teams`() {
        val positions = listOf("포인트가드", "센터", "PG", "C")
        val players = positions.mapIndexed { index, position -> TeamParticipant(index + 1L, "Player", position, 50) }
        val teams = balancer.balance(players, 2, TeamConstraints(balancePositions = true))
        assertTrue(teams.all { team -> team.map { it.position }.any { it == "PG" || it == "포인트가드" } })
        assertTrue(teams.all { team -> team.map { it.position }.any { it == "C" || it == "센터" } })
    }

    @Test
    fun `same and different team conditions preserve every player and team sizes`() {
        val players = (1..6).map { TeamParticipant(it.toLong(), "Player $it", if (it % 2 == 0) "DF" else "FW", 50) }
        val teams = balancer.balance(players, 2, TeamConstraints(together = listOf(1L to 2L), apart = listOf(1L to 3L)))
        assertEquals((1L..6L).toList(), teams.flatten().map { it.memberId }.sorted())
        assertEquals(listOf(3, 3), teams.map { it.size })
        assertEquals(teams.indexOfFirst { team -> team.any { it.memberId == 1L } }, teams.indexOfFirst { team -> team.any { it.memberId == 2L } })
        assertTrue(teams.none { team -> team.any { it.memberId == 1L } && team.any { it.memberId == 3L } })
    }

    @Test
    fun `conflicting or oversized same team groups are rejected`() {
        val players = (1..4).map { TeamParticipant(it.toLong(), "Player $it", "FW", 50) }
        assertFailsWith<IllegalArgumentException> {
            balancer.balance(players, 2, TeamConstraints(together = listOf(1L to 2L), apart = listOf(1L to 2L)))
        }
        assertFailsWith<IllegalArgumentException> {
            balancer.balance(players, 2, TeamConstraints(together = listOf(1L to 2L, 2L to 3L)))
        }
    }

    @Test
    fun `pairs strong and weak players for balanced totals`() {
        val players = listOf(100, 99, 2, 1).mapIndexed { index, rating ->
            TeamParticipant(index.toLong() + 1, "Player", "PG", rating)
        }
        val teams = balancer.balance(players, 2)
        assertEquals(listOf(101, 101), teams.map { it.sumOf { member -> member.rating } })
    }

    @Test
    fun `varied team and participant counts preserve every player and balanced sizes`() {
        for (size in 2..35) {
            val players = (1..size).map { TeamParticipant(it.toLong(), "Player", "PG", (it * 17) % 100 + 1) }
            for (count in 2..size) {
                val teams = balancer.balance(players, count)
                assertEquals(count, teams.size)
                assertEquals(players.map { it.memberId }.sorted(), teams.flatten().map { it.memberId }.sorted())
                assertTrue(teams.all { it.isNotEmpty() })
                assertTrue(teams.maxOf { it.size } - teams.minOf { it.size } <= 1)
            }
        }
    }

    @Test
    fun `equal rating tie breaking does not depend on request order`() {
        val players = (1..7).map { TeamParticipant(it.toLong(), "Same name", "PG", 50) }
        assertEquals(balancer.balance(players, 3), balancer.balance(players.reversed(), 3))
    }

    @Test
    fun `invalid team counts duplicate participants and invalid ratings are rejected`() {
        val player = TeamParticipant(1, "Player", "PG", 70)
        for (count in listOf(-1, 0, 1, 3)) {
            assertFailsWith<IllegalArgumentException> { balancer.balance(listOf(player, player.copy(memberId = 2)), count) }
        }
        assertFailsWith<IllegalArgumentException> { balancer.balance(emptyList(), 2) }
        assertFailsWith<IllegalArgumentException> { balancer.balance(listOf(player, player), 2) }
        assertFailsWith<IllegalArgumentException> { balancer.balance(listOf(player, player.copy(memberId = 2, rating = 101)), 2) }
    }
}
