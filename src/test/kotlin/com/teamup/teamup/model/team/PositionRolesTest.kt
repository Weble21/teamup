package com.teamup.teamup.model.team

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PositionRolesTest {
    @Test
    fun `football aliases keep their detailed position and family`() {
        assertEquals(PositionRoles.of("LWB"), PositionRoles.of("레프트윙백"))
        assertEquals("FOOTBALL_DEFENDER", PositionRoles.of("LWB").family)
        assertEquals("FOOTBALL_MIDFIELDER", PositionRoles.of("MF").family)
        assertEquals("FOOTBALL_FORWARD", PositionRoles.of("LW").family)
        assertEquals("FOOTBALL_FORWARD", PositionRoles.of("ST").family)
    }

    @Test
    fun `basketball aliases keep guard forward and center distinctions`() {
        assertEquals(PositionRoles.of("PG"), PositionRoles.of("포인트가드"))
        assertEquals(PositionRoles.of("SF"), PositionRoles.of("스몰포워드"))
        assertEquals(PositionRoles.of("C"), PositionRoles.of("센터"))
        assertEquals(PositionRoles.of("G").family, PositionRoles.of("PG").family)
        assertEquals(PositionRoles.of("F").family, PositionRoles.of("SF").family)
    }
}
