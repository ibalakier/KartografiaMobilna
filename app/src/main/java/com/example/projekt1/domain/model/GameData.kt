package com.example.projekt1.domain.model

import com.example.projekt1.Faction

data class GameData(
    val activeRound: Int,
    var roomState: RoomState,
    val playerNumber: Int,
    val faction: Faction?
)
