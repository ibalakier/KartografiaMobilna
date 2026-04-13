package com.example.projekt1.domain.useCase

import com.example.projekt1.domain.model.GameData
import com.example.projekt1.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow

class ObserveGameDataUseCase(
    private val repository: GameRepository
) {
    operator fun invoke(): Flow<GameData?> {
        return repository.observeGameData()
    }
}
