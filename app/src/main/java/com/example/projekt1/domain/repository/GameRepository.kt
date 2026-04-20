package com.example.projekt1.domain.repository

import com.example.projekt1.Faction
import com.example.projekt1.domain.model.GameData
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    suspend fun joinRoom(): Int
    suspend fun fetchGameData(): GameData?
    fun observeGameData(): Flow<GameData?>
    suspend fun setPlayerFaction(playerNumber: Int, faction: Faction)
    suspend fun updateGameState(
        playerNumber: Int,
        gold: Int,
        food: Int,
        conqueredRadiusKm: Double,
        activePlayerNumber: Int
    )
    suspend fun resetGame()
}