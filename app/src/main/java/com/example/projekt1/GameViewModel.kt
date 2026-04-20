package com.example.projekt1

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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

    private val repository = GameRepositoryImpl(FirebaseFirestore.getInstance())
    private val fetchGameData = FetchGameDataUseCase(repository)
    private val setPlayerFaction = SetPlayerFactionUseCase(repository)

    private val _initialGameData = MutableStateFlow<GameData?>(null)
    val initialGameData: StateFlow<GameData?> = _initialGameData

    private val _gameData = MutableStateFlow<GameData?>(null)
    val gameData: StateFlow<GameData?> = _gameData

    // playerNumber ustawiany przez joinRoom() — nigdy z parseSnapshot
    private var _playerNumber: Int = 0
    var playerNumber by mutableIntStateOf(0)
        private set

    var gold by mutableIntStateOf(100)
        private set

    var food by mutableIntStateOf(50)
        private set

    var conqueredRadiusKm by mutableDoubleStateOf(0.0)
        private set

    var activePlayerNumber by mutableIntStateOf(1)
        private set

    var roundNumber by mutableIntStateOf(1)
        private set

    // Pokój pełny — nie można dołączyć
    private val _roomFull = MutableStateFlow(false)
    val roomFull: StateFlow<Boolean> = _roomFull

    val isMyTurn: Boolean
        get() = activePlayerNumber == _playerNumber

    init {
        viewModelScope.launch {
            // Krok 1: atomowo zarezerwuj slot w pokoju
            val assignedNumber = repository.joinRoom()
            Log.d(TAG, "joinRoom: assignedNumber=$assignedNumber")

            if (assignedNumber == 0) {
                // Pokój pełny
                _roomFull.value = true
                return@launch
            }

            _playerNumber = assignedNumber
            playerNumber = assignedNumber

            // Krok 2: pobierz dane gry
            val data = fetchGameData()
            Log.d(TAG, "INIT: roomState=${data?.roomState?.name} activePlayer=${data?.activePlayerNumber}")
            _initialGameData.value = data
            _gameData.value = data

            if (data != null) {
                roundNumber = data.activeRound.coerceAtLeast(1)
                conqueredRadiusKm = data.conqueredRadiusKm
                activePlayerNumber = data.activePlayerNumber

                if (_playerNumber == 1) {
                    gold = data.goldPlayer1
                    food = data.foodPlayer1
                } else {
                    gold = data.goldPlayer2
                    food = data.foodPlayer2
                }

                startObservingGame()
            }
        }
    }

    private fun startObservingGame() {
        viewModelScope.launch {
            repository.observeGameData().collect { data ->
                if (data == null) return@collect
                _gameData.value = data
                conqueredRadiusKm = data.conqueredRadiusKm
                activePlayerNumber = data.activePlayerNumber
                roundNumber = data.activeRound.coerceAtLeast(1)

                Log.d(TAG, "OBSERVE: activePlayer=${data.activePlayerNumber} myNumber=$_playerNumber isMyTurn=$isMyTurn roomState=${data.roomState}")

                if (_playerNumber == 1) {
                    gold = data.goldPlayer1
                    food = data.foodPlayer1
                } else {
                    gold = data.goldPlayer2
                    food = data.foodPlayer2
                }
            }
        }
    }

    fun selectFaction(faction: Faction?) {
        if (faction == null) { Log.e(TAG, "Faction is required."); return }
        viewModelScope.launch {
            setPlayerFaction(_playerNumber, faction)
        }
    }

    fun addGold(amount: Int) {
        gold = (gold + amount).coerceAtLeast(0)
        pushStateToFirestore()
    }

    fun addFood(amount: Int) {
        food = (food + amount).coerceAtLeast(0)
        pushStateToFirestore()
    }

    fun updateConqueredRadius(newRadius: Double) {
        conqueredRadiusKm = newRadius.coerceAtLeast(0.0)
        pushStateToFirestore()
    }

    fun endTurn() {
        val nextPlayer = if (_playerNumber == 1) 2 else 1
        activePlayerNumber = nextPlayer
        roundNumber++
        pushStateToFirestore()
    }

    fun resetGame() {
        viewModelScope.launch {
            try {
                repository.resetGame()
            } catch (e: Exception) {
                Log.e(TAG, "Błąd resetu: ${e.message}")
            }
        }
    }

    private fun pushStateToFirestore() {
        viewModelScope.launch {
            try {
                repository.updateGameState(
                    playerNumber = _playerNumber,
                    gold = gold,
                    food = food,
                    conqueredRadiusKm = conqueredRadiusKm,
                    activePlayerNumber = activePlayerNumber
                )
            } catch (e: Exception) {
                Log.e(TAG, "Błąd zapisu: ${e.message}")
            }
        }
    }

    fun nextRound() = endTurn()
}