package com.example.projekt1.domain.model

import com.example.projekt1.Faction

data class GameData(
    val activeRound: Int,
    var roomState: RoomState,
    val playerNumber: Int,
    val faction: Faction?,
    // Nowe pola synchronizowane z bazą
    val goldPlayer1: Int = 100,
    val foodPlayer1: Int = 50,
    val goldPlayer2: Int = 100,
    val foodPlayer2: Int = 50,
    val conqueredRadiusKm: Double = 0.0,
    val activePlayerNumber: Int = 1   // czyja tura: 1 lub 2
)