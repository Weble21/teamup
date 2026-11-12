package com.teamup.teamup.model.team

data class PositionRole(val specific: String, val family: String)

/** Normalizes free-text position aliases only when comparing team balance. Stored labels stay unchanged. */
object PositionRoles {
    fun of(position: String): PositionRole {
        val key = position.trim().uppercase().replace(Regex("[\\s_-]+"), "")
        val specific = when (key) {
            // 축구
            "골키퍼", "GOALKEEPER" -> "GK"
            "레프트윙백", "LEFTWINGBACK" -> "LWB"
            "라이트윙백", "RIGHTWINGBACK" -> "RWB"
            "윙백", "WINGBACK" -> "WB"
            "레프트백", "LEFTBACK" -> "LB"
            "라이트백", "RIGHTBACK" -> "RB"
            "센터백", "CENTREBACK", "CENTERBACK" -> "CB"
            "미드필더", "MIDFIELDER" -> "MF"
            "볼란치", "VOLANTE" -> "VOL"
            "중앙미드필더", "CENTRALMIDFIELDER" -> "CM"
            "수비형미드필더", "DEFENSIVEMIDFIELDER" -> "CDM"
            "공격형미드필더", "ATTACKINGMIDFIELDER" -> "CAM"
            "레프트윙", "LEFTWING" -> "LW"
            "라이트윙", "RIGHTWING" -> "RW"
            "스트라이커", "STRIKER" -> "ST"
            // 농구
            "포인트가드", "POINTGUARD" -> "PG"
            "슈팅가드", "SHOOTINGGUARD" -> "SG"
            "스몰포워드", "SMALLFORWARD" -> "SF"
            "파워포워드", "POWERFORWARD" -> "PF"
            "센터", "CENTER", "CENTRE" -> "C"
            "가드", "GUARD" -> "G"
            "포워드", "FORWARD" -> "F"
            
            // 리그 오브 레전드
            "탑", "탑솔러", "TOP" -> "TOP"
            "정글", "백정", "JUNGLE" -> "JUNGLE"
            "미드", "황족", "MIDDLE" -> "MIDDLE"
            "바텀", "원딜", "BOTTOM" -> "BOTTOM"
            "서포터", "서폿", "SUPPORT" -> "SUPPORT"

            else -> key
        }
        val family = when (specific) {
            "GK" -> "FOOTBALL_GOALKEEPER"
            "LWB", "RWB", "LB", "RB", "CB", "DF" -> "FOOTBALL_DEFENDER"
            "MF", "VOL", "CM", "CDM", "CAM", "LM", "RM" -> "FOOTBALL_MIDFIELDER"
            "LW", "RW", "ST", "CF", "FW" -> "FOOTBALL_FORWARD"
            "PG", "SG", "G" -> "BASKETBALL_GUARD"
            "SF", "PF", "F" -> "BASKETBALL_FORWARD"
            "C" -> "BASKETBALL_CENTER"
            else -> specific
        }
        return PositionRole(specific, family)
    }
}
