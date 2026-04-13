package com.example.projekt1.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projekt1.Frakcja
import com.example.projekt1.domain.model.GameData
import com.example.projekt1.domain.useCase.ObserveGameDataUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class GameViewModel(
    observeGameDataUseCase: ObserveGameDataUseCase
) : ViewModel() {

    private val _gameData = MutableStateFlow<GameData?>(null)
    val gameData: StateFlow<GameData?> = _gameData

    init {
        observeGameDataUseCase().onEach { data ->
            _gameData.value = data
        }.launchIn(viewModelScope)
    }

    fun onFrakcjaSelected(frakcja: Frakcja, onNavigate: (String) -> Unit) {
        onNavigate("game_screen/${frakcja.name}")
    }
}
