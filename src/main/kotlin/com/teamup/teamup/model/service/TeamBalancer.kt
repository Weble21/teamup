package com.teamup.teamup.model.service

import com.teamup.teamup.model.team.TeamParticipant
import org.springframework.stereotype.Component

@Component
class TeamBalancer {
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
