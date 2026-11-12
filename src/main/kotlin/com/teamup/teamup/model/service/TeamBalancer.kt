package com.teamup.teamup.model.service

import com.teamup.teamup.model.team.TeamParticipant
import com.teamup.teamup.model.team.TeamConstraints
import com.teamup.teamup.model.team.PositionRoles
import org.springframework.stereotype.Component

@Component
class TeamBalancer {
    fun balance(participants: List<TeamParticipant>, teamCount: Int, constraints: TeamConstraints): List<List<TeamParticipant>> {
        if (constraints.together.isEmpty() && constraints.apart.isEmpty() && !constraints.balancePositions) {
            return balance(participants, teamCount)
        }
        require(teamCount in 2..participants.size && participants.map { it.memberId }.distinct().size == participants.size)
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
        var visited = 0
        fun search(index: Int): Boolean {
            if (index == groups.size) return true
            if (++visited > 100_000) return false
            val group = groups[index]
            val choices = teams.indices.filter { team ->
                teams[team].size + group.players.size <= capacities[team] &&
                    apart.none { (a, b) -> (a == group.root && b in roots[team]) || (b == group.root && a in roots[team]) }
            }.sortedWith(compareBy<Int> { team ->
                if (constraints.balancePositions) group.players.sumOf { player ->
                    val role = PositionRoles.of(player.position)
                    teams[team].sumOf { member ->
                        val existing = PositionRoles.of(member.position)
                        if (existing.specific == role.specific) 4 else if (existing.family == role.family) 1 else 0
                    }
                } * 1000 else 0
            }.thenBy { totals[it] }.thenBy { teams[it].size }.thenBy { it })
            for (team in choices) {
                teams[team].addAll(group.players); roots[team].add(group.root); totals[team] += group.rating
                if (search(index + 1)) return true
                totals[team] -= group.rating; roots[team].remove(group.root)
                repeat(group.players.size) { teams[team].removeAt(teams[team].lastIndex) }
            }
            return false
        }
        require(search(0)) { "Cannot satisfy all team conditions" }
        return teams.map { it.toList() }
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
