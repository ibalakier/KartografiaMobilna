package com.example.projekt1.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.projekt1.domain.useCase.ObserveGameDataUseCase

class GameViewModelFactory(
    private val observeGameDataUseCase: ObserveGameDataUseCase
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            return GameViewModel(observeGameDataUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
