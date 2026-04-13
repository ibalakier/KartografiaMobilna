package com.example.projekt1.domain.repository

import com.example.projekt1.Faction
import com.example.projekt1.domain.model.GameData

interface GameRepository {
    suspend fun fetchGameData(): GameData?
    suspend fun setPlayerFaction(playerNumber: Int, faction: Faction)
}
