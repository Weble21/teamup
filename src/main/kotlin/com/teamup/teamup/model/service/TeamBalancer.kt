package com.teamup.teamup.model.service

import com.teamup.teamup.model.team.TeamParticipant
import com.teamup.teamup.model.team.TeamConstraints
import com.teamup.teamup.model.team.PositionRoles
import org.springframework.stereotype.Component

@Component
class TeamBalancer {
    internal var clock: () -> Long = System::nanoTime
    internal var budgetNanos: Long = 5_000_000_000L
    fun balance(participants: List<TeamParticipant>, teamCount: Int, constraints: TeamConstraints): List<List<TeamParticipant>> {
        require(teamCount in 2..participants.size && participants.map { it.memberId }.distinct().size == participants.size)
        require(participants.all { it.rating in 1..100 })
        val byId = participants.associateBy { it.memberId }
        val parent = participants.associate { it.memberId to it.memberId }.toMutableMap()
        fun root(id: Long): Long {
            var current = id
            while (parent.getValue(current) != current) current = parent.getValue(current)
            return current
        }
        constraints.together.forEach { (first, second) ->
            require(first in byId && second in byId && first != second)
            parent[root(second)] = root(first)
        }
        val groups = participants.groupBy { root(it.memberId) }.entries
            .map { Group(it.key, it.value.sortedBy { player -> player.memberId }) }
            .sortedWith(compareByDescending<Group> { it.players.size }.thenByDescending { it.rating }.thenBy { it.root })
        val apart = constraints.apart.map { (first, second) ->
            require(first in byId && second in byId && first != second)
            val a = root(first); val b = root(second)
            require(a != b) { "Same-team and different-team constraints conflict" }
            a to b
        }
        val capacities = IntArray(teamCount) { participants.size / teamCount + if (it < participants.size % teamCount) 1 else 0 }
        require(groups.all { it.players.size <= capacities.max() }) { "A same-team group is larger than a team" }
        val teams = List(teamCount) { mutableListOf<TeamParticipant>() }
        val roots = List(teamCount) { mutableSetOf<Long>() }
        val totals = LongArray(teamCount)
        val deadline = clock() + budgetNanos
        var timedOut = false
        var best: List<List<TeamParticipant>>? = null
        var bestScore: CandidateScore? = null
        val roles = participants.associate { it.memberId to PositionRoles.of(it.position) }
        fun search(index: Int) {
            if (clock() - deadline >= 0) { timedOut = true; return }
            if (index == groups.size) {
                val score = scoreCandidate(teams, roles)
                if (bestScore == null || score.betterThan(bestScore!!)) {
                    bestScore = score
                    best = teams.map { it.toList() }
                }
                return
            }
            val group = groups[index]
            val choices = teams.indices.filter { team ->
                teams[team].size + group.players.size <= capacities[team] &&
                    apart.none { (a, b) -> (a == group.root && b in roots[team]) || (b == group.root && a in roots[team]) }
            }.sortedWith(compareBy<Int> { totals[it].toDouble() / maxOf(1, teams[it].size) }.thenBy { teams[it].size }.thenBy { it })
            val emptyCapacities = mutableSetOf<Int>()
            for (team in choices) {
                if (teams[team].isEmpty() && !emptyCapacities.add(capacities[team])) continue
                teams[team].addAll(group.players); roots[team].add(group.root); totals[team] += group.rating
                search(index + 1)
                totals[team] -= group.rating; roots[team].remove(group.root)
                repeat(group.players.size) { teams[team].removeAt(teams[team].lastIndex) }
                if (timedOut) return
            }
        }
        search(0)
        if (best == null && timedOut) throw TeamSearchTimeoutException()
        require(best != null) { "Cannot satisfy all team conditions" }
        return best!!
    }

    // Sum pairwise squared differences in team proportions: specific role weight 4, family weight 1.
    internal fun scoreCandidate(teams: List<List<TeamParticipant>>, roles: Map<Long, com.teamup.teamup.model.team.PositionRole> =
        teams.flatten().associate { it.memberId to PositionRoles.of(it.position) }): CandidateScore {
        val averages = teams.map { team -> team.sumOf { it.rating }.toDouble() / team.size / 10.0 }
        fun imbalance(selector: (com.teamup.teamup.model.team.PositionRole) -> String): Double =
            roles.values.map(selector).toSet().sumOf { key ->
                val proportions = teams.map { team -> team.count { selector(roles.getValue(it.memberId)) == key }.toDouble() / team.size }
                proportions.indices.sumOf { i -> (i + 1 until proportions.size).sumOf { j ->
                    val diff = proportions[i] - proportions[j]; diff * diff
                } }
            }
        return CandidateScore(averages.max() - averages.min(), 4 * imbalance { it.specific } + imbalance { it.family })
    }

    internal data class CandidateScore(val difference: Double, val positionPenalty: Double) {
        fun betterThan(other: CandidateScore): Boolean {
            val acceptable = difference < 0.8 - 1e-10
            val otherAcceptable = other.difference < 0.8 - 1e-10
            if (acceptable != otherAcceptable) return acceptable
            fun compare(a: Double, b: Double) = when { a < b - 1e-10 -> -1; a > b + 1e-10 -> 1; else -> 0 }
            val primary = if (acceptable) compare(positionPenalty, other.positionPenalty) else compare(difference, other.difference)
            return if (primary != 0) primary < 0 else if (acceptable) compare(difference, other.difference) < 0 else compare(positionPenalty, other.positionPenalty) < 0
        }
    }

    private data class Group(val root: Long, val players: List<TeamParticipant>) {
        val rating: Long get() = players.sumOf { it.rating.toLong() }
    }

    fun balance(participants: List<TeamParticipant>, teamCount: Int): List<List<TeamParticipant>> {
        require(teamCount >= 2 && teamCount <= participants.size) { "Invalid team count" }
        require(participants.map { it.memberId }.distinct().size == participants.size) { "Duplicate participants" }
        require(participants.all { it.rating in 1..100 }) { "Invalid rating" }

        val capacities = IntArray(teamCount) { index ->
            participants.size / teamCount + if (index < participants.size % teamCount) 1 else 0
        }
        val teams = List(teamCount) { mutableListOf<TeamParticipant>() }
        val totals = LongArray(teamCount)
        // Place stronger players first into the lowest-total team with an available slot.
        // Stable tie-breaking makes identical input produce the same candidate.
        for (participant in participants.sortedWith(compareByDescending<TeamParticipant> { it.rating }.thenBy { it.memberId })) {
            val target = teams.indices.filter { teams[it].size < capacities[it] }
                .minWith(compareBy<Int> { totals[it] }.thenBy { teams[it].size }.thenBy { it })
            teams[target].add(participant)
            totals[target] += participant.rating.toLong()
        }

        val assignedIds = teams.flatten().map { it.memberId }
        check(assignedIds.size == participants.size && assignedIds.toSet() == participants.map { it.memberId }.toSet())
        check(teams.indices.all { teams[it].size == capacities[it] })
        return teams.map { it.toList() }
    }
}

class TeamSearchTimeoutException : RuntimeException("Team search timed out")
