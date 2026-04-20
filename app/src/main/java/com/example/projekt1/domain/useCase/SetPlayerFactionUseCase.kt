package com.example.projekt1.domain.useCase

import com.example.projekt1.Faction
import com.example.projekt1.domain.repository.GameRepository

class SetPlayerFactionUseCase(
    private val repository: GameRepository
) {

    suspend operator fun invoke(
        playerNumber: Int,
        faction: Faction
    ) {
        repository.setPlayerFaction(playerNumber, faction)
    }
}
