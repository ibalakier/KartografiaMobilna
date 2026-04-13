package com.example.projekt1

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projekt1.data.repository.GameRepositoryImpl
import com.example.projekt1.domain.model.GameData
import com.example.projekt1.domain.useCase.FetchGameDataUseCase
import com.example.projekt1.domain.useCase.SetPlayerFactionUseCase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {

    companion object {
        private const val TAG = "pw.GameViewModel"
    }

    // TODO Dependency Injection
    private val repository = GameRepositoryImpl(FirebaseFirestore.getInstance())
    private val fetchGameData = FetchGameDataUseCase(repository)
    private val setPlayerFaction = SetPlayerFactionUseCase(repository)

    // Statystyki
    var roundNumber by mutableIntStateOf(1)
        private set

    var gold by mutableIntStateOf(100)
        private set

    var food by mutableIntStateOf(50)
        private set

    private val _initialGameData = MutableStateFlow<GameData?>(null)
    val initialGameData: StateFlow<GameData?> = _initialGameData

    init {
        viewModelScope.launch {
            val data = fetchGameData()
            Log.d(
                TAG,
                "activeRound=${data?.activeRound}; " +
                        "roomState=${data?.roomState?.name}; " +
                        "playerNumber=${data?.playerNumber}; " +
                        "faction=${data?.faction?.name}"
            )
            _initialGameData.value = data
        }
    }

    fun selectFaction(
        faction: Faction?
    ) {
        if (faction == null) {
            Log.e(TAG, "Faction is required.")
            return
        }
        val data = _initialGameData.value
        if (data == null) {
            Log.e(TAG, "Game data is not set.")
            return
        }
        viewModelScope.launch {
            setPlayerFaction(data.playerNumber, faction)
        }
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