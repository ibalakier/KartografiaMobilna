package com.example.projekt1

import androidx.lifecycle.ViewModel

class GameViewModel : ViewModel() {
    fun onFrakcjaSelected(frakcja: Frakcja, onNavigate: (String) -> Unit) {
        onNavigate("game_screen/${frakcja.name}")
    }
}