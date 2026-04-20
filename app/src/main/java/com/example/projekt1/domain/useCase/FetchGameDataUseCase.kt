package com.example.projekt1.domain.useCase

import com.example.projekt1.domain.model.GameData
import com.example.projekt1.domain.repository.GameRepository

class FetchGameDataUseCase(
    private val repository: GameRepository
) {

    suspend operator fun invoke(): GameData? {
        return repository.fetchGameData()
    }
}
