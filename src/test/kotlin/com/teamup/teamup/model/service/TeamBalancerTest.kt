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
