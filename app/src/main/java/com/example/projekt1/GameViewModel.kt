package com.example.projekt1

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class GameViewModel : ViewModel() {
    // Statystyki
    var roundNumber by mutableIntStateOf(1)

        private set

    var gold by mutableIntStateOf(100)
        private set

    var food by mutableIntStateOf(50)
        private set

    fun onFrakcjaSelected(frakcja: Frakcja, onNavigate: (String) -> Unit) {
        onNavigate("game_screen/${frakcja.name}")
    }

    // Przykładowa funkcja aktualizująca (możesz ją podpiąć pod przycisk "Koniec tury")
    fun nextRound() {
        roundNumber++
        gold += 10
        food -= 5
    }

    fun addGold(amount: Int) {
        gold += amount
    }

    fun addFood(amount: Int) {
        food += amount
    }
}