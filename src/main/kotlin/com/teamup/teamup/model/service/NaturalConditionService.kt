package com.teamup.teamup.model.service

import com.teamup.teamup.model.team.TeamConstraints
import com.teamup.teamup.model.team.TeamParticipant
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class NaturalConditionService(private val embeddings: EmbeddingConstraintClient) {
    private val positionTerms = Regex(
        "포지션|가드|센터|포워드|윙|미드필더|수비|공격|\\b(?:LWB|RWB|LB|RB|CB|GK|DF|MF|CM|CDM|CAM|LM|RM|LW|RW|ST|CF|FW|PG|SG|SF|PF)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun interpret(text: String?, players: List<TeamParticipant>): TeamConstraints {
        if (text.isNullOrBlank()) return TeamConstraints()
        if (text.length > 500) invalid("조건은 500자 이하로 입력해 주세요.")
        val clauses = text.split('\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (clauses.isEmpty() || clauses.size > 5 || clauses.any { it.length > 150 }) invalid("조건은 한 줄에 하나씩 최대 5개 입력해 주세요.")
        val duplicateNames = players.groupBy { it.name.trim() }.filterValues { it.size > 1 }.keys
        val named = clauses.map { clause ->
            val matches = players.filter { it.name.isNotBlank() && clause.contains(it.name.trim()) }
            if (matches.any { it.name.trim() in duplicateNames }) invalid("같은 이름의 선수가 있어 조건을 해석할 수 없습니다.")
            matches
        }
        val masked = clauses.mapIndexed { index, clause ->
            named[index].sortedByDescending { it.name.length }.fold(clause) { value, player -> value.replace(player.name.trim(), "선수") }
        }
        val classified = embeddings.classify(masked)
        val together = mutableListOf<Pair<Long, Long>>()
        val apart = mutableListOf<Pair<Long, Long>>()
        var balancePositions = false
        val descriptions = mutableListOf<String>()
        clauses.forEachIndexed { index, clause ->
            val type = classified[index].type
            val playersInClause = named[index]
            val negatedTogether = Regex("(같은 팀|같이|함께).{0,20}(않|말|못|마|안|금지)").containsMatchIn(masked[index])
            val negatedApart = Regex("(다른 팀|따로|떨어|분리).{0,20}(않|말|못|마|안|금지)").containsMatchIn(masked[index])
            val negative = Regex("다른 팀|따로|떨어|분리").containsMatchIn(masked[index]) || negatedTogether
            val positive = Regex("같은 팀|함께|같이|한 팀|묶|붙여").containsMatchIn(masked[index]) && !negatedTogether
            when (type) {
                "PAIR_RELATION" -> {
                    if (playersInClause.size != 2) invalid("같은 팀·다른 팀 조건에는 참석 선수 2명의 이름이 필요합니다.")
                    if (negatedApart || (negative && positive)) invalid("조건의 방향이 모호합니다. 같은 팀 또는 다른 팀 중 하나만 명시해 주세요.")
                    if (!negative && !positive) invalid("같은 팀인지 다른 팀인지 명시해 주세요.")
                    val pair = playersInClause.map { it.memberId }.sorted().let { it[0] to it[1] }
                    if (negative) apart += pair else together += pair
                    descriptions += "${playersInClause[0].name}·${playersInClause[1].name} ${if (negative) "다른 팀" else "같은 팀"}"
                }
                "POSITION_BALANCE" -> {
                    if (playersInClause.isNotEmpty()) invalid("포지션 균형 조건에는 선수 이름을 넣지 말아 주세요.")
                    if (!positionTerms.containsMatchIn(clause) ||
                        !Regex("균형|고루|고르|분배|나눠|몰리지|섞어|맞춰").containsMatchIn(clause)) invalid("포지션을 골고루 나누는 조건인지 확인해 주세요.")
                    balancePositions = true
                    descriptions += "포지션 균형"
                }
                else -> invalid("해석할 수 없는 조건입니다: $clause")
            }
        }
        if (together.any { it in apart }) invalid("같은 두 선수에게 같은 팀과 다른 팀 조건을 동시에 적용할 수 없습니다.")
        return TeamConstraints(together.distinct(), apart.distinct(), balancePositions, descriptions.distinct())
    }

    private fun invalid(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}
