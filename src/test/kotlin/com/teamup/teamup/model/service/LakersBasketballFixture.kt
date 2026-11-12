package com.teamup.teamup.model.service

import com.teamup.teamup.model.entity.SportType

/** Lakers-inspired test data: 18 roster names plus 3 retired players. Positions and ratings are illustrative. */
internal object LakersBasketballFixture {
    const val clubName = "test"
    val sportType = SportType.BASKETBALL
    const val teamCount = 2

    val members = listOf(
        NewClubMember("Adou Thiero", "SG", 50, null, null),
        NewClubMember("Jarred Vanderbilt", "PF", 70, null, null),
        NewClubMember("Matisse Thybulle", "SF", 70, null, null),
        NewClubMember("Quentin Grimes", "SG", 70, null, null),
        NewClubMember("Jaden Hardy", "SG", 60, null, null),
        NewClubMember("Bronny James", "PG", 50, null, null),
        NewClubMember("Collin Sexton", "PG", 80, null, null),
        NewClubMember("Ziaire Williams", "SF", 60, null, null),
        NewClubMember("Jake LaRavia", "PF", 70, null, null),
        NewClubMember("Walker Kessler", "C", 80, null, null),
        NewClubMember("Austin Reaves", "SG", 90, null, null),
        NewClubMember("Carson Cooper", "C", 50, null, null),
        NewClubMember("Chris Mañon", "SG", 50, null, null),
        NewClubMember("Cameron Carr", "SG", 50, null, null),
        NewClubMember("Arthur Kaluma", "PF", 50, null, null),
        NewClubMember("Sandro Mamukelashvili", "C", 70, null, null),
        NewClubMember("Kevon Looney", "C", 70, null, null),
        NewClubMember("Luka Dončić", "PG", 100, null, null),
        NewClubMember("Kobe Bryant", "SG", 100, null, null),
        NewClubMember("Magic Johnson", "PG", 100, null, null),
        NewClubMember("Shaquille O'Neal", "C", 100, null, null),
    )

    val attendingNames = setOf(
        "Jarred Vanderbilt", "Quentin Grimes", "Bronny James", "Collin Sexton",
        "Ziaire Williams", "Jake LaRavia", "Walker Kessler", "Austin Reaves",
        "Sandro Mamukelashvili", "Kevon Looney", "Luka Dončić", "Kobe Bryant",
        "Magic Johnson", "Shaquille O'Neal",
    )
}
