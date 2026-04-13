package com.example.projekt1.domain.repository

import com.example.projekt1.domain.model.GameData
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    fun observeGameData(): Flow<GameData?>
}
