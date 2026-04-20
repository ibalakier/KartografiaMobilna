package com.example.projekt1.data.repository

import com.example.projekt1.Faction
import com.example.projekt1.domain.model.GameData
import com.example.projekt1.domain.model.RoomState
import com.example.projekt1.domain.repository.GameRepository
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class GameRepositoryImpl(
    private val firestore: FirebaseFirestore
) : GameRepository {

    companion object {
        private const val COLLECTION_PATH = "game"
        private const val DOCUMENT_PATH = "ifEs8o9KugHUmURSGScJ"
        private const val FIELD_ACTIVE_ROUND = "activeRound"
        private const val FIELD_PLAYER_PREFIX = "player"
        private const val FIELD_FACTION = "faction"
        private const val FIELD_GOLD = "gold"
        private const val FIELD_FOOD = "food"
        private const val FIELD_CONQUERED_RADIUS = "conqueredRadiusKm"
        private const val FIELD_ACTIVE_PLAYER = "activePlayerNumber"
    }

    private val docRef: DocumentReference
        get() = firestore.collection(COLLECTION_PATH).document(DOCUMENT_PATH)

    /**
     * Atomowo rezerwuje slot gracza używając transakcji.
     * Sprawdza pole "slot" które jest 0, 1 lub 2.
     * Zwraca przypisany numer gracza (1 lub 2), lub 0 jeśli pokój pełny.
     */
    override suspend fun joinRoom(): Int {
        var assignedNumber = 0
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val slot = snapshot.getLong("slot")?.toInt() ?: 0
            when (slot) {
                0 -> {
                    assignedNumber = 1
                    transaction.update(docRef, "slot", 1L)
                }
                1 -> {
                    assignedNumber = 2
                    transaction.update(docRef, "slot", 2L)
                }
                else -> {
                    assignedNumber = 0 // pełny
                }
            }
        }.await()
        return assignedNumber
    }

    override suspend fun fetchGameData(): GameData? {
        val snapshot = docRef.get(Source.SERVER).await()
        val data = snapshot.data ?: return null
        val activeRound = snapshot.getLong(FIELD_ACTIVE_ROUND)?.toInt() ?: 0
        return parseSnapshot(data, activeRound)
    }

    override fun observeGameData(): Flow<GameData?> = callbackFlow {
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) { trySend(null); return@addSnapshotListener }
            val data = snapshot.data ?: run { trySend(null); return@addSnapshotListener }
            val activeRound = snapshot.getLong(FIELD_ACTIVE_ROUND)?.toInt() ?: 0
            trySend(parseSnapshot(data, activeRound))
        }
        awaitClose { listener.remove() }
    }

    private fun parseSnapshot(data: Map<String, Any>, activeRound: Int): GameData {
        val player1Faction = data.getFaction(1)
        val player2Faction = data.getFaction(2)

        val roomState = when {
            player1Faction == null && player2Faction == null -> RoomState.EMPTY
            player1Faction != null && player2Faction == null -> RoomState.PLAYER1_ONLY
            else -> RoomState.FULL
        }

        val faction = if (roomState == RoomState.PLAYER1_ONLY) {
            if (player1Faction == Faction.ROgród) Faction.ROlandia else Faction.ROgród
        } else null

        fun playerInt(playerNumber: Int, field: String, default: Int = 100): Int =
            ((data["$FIELD_PLAYER_PREFIX$playerNumber"] as? Map<*, *>)
                ?.get(field) as? Long)?.toInt() ?: default

        return GameData(
            activeRound = activeRound,
            playerNumber = 0,
            roomState = roomState,
            faction = faction,
            goldPlayer1 = playerInt(1, FIELD_GOLD),
            foodPlayer1 = playerInt(1, FIELD_FOOD, 50),
            goldPlayer2 = playerInt(2, FIELD_GOLD),
            foodPlayer2 = playerInt(2, FIELD_FOOD, 50),
            conqueredRadiusKm = (data[FIELD_CONQUERED_RADIUS] as? Double)
                ?: (data[FIELD_CONQUERED_RADIUS] as? Long)?.toDouble() ?: 0.0,
            activePlayerNumber = (data[FIELD_ACTIVE_PLAYER] as? Long)?.toInt() ?: 1
        )
    }

    private fun Map<*, *>.getFaction(playerNumber: Int): Faction? {
        val factionString = (this["$FIELD_PLAYER_PREFIX$playerNumber"] as? Map<*, *>)
            ?.get(FIELD_FACTION) as? String? ?: ""
        return runCatching { Faction.valueOf(factionString) }.getOrNull()
    }

    override suspend fun setPlayerFaction(playerNumber: Int, faction: Faction) {
        docRef.update(mapOf(
            "${FIELD_PLAYER_PREFIX}$playerNumber.$FIELD_FACTION" to faction.name,
            "${FIELD_PLAYER_PREFIX}$playerNumber.$FIELD_GOLD" to 100L,
            "${FIELD_PLAYER_PREFIX}$playerNumber.$FIELD_FOOD" to 50L,
            FIELD_ACTIVE_PLAYER to 1L
        )).await()
    }

    override suspend fun updateGameState(
        playerNumber: Int, gold: Int, food: Int,
        conqueredRadiusKm: Double, activePlayerNumber: Int
    ) {
        docRef.update(mapOf(
            "${FIELD_PLAYER_PREFIX}$playerNumber.$FIELD_GOLD" to gold.toLong(),
            "${FIELD_PLAYER_PREFIX}$playerNumber.$FIELD_FOOD" to food.toLong(),
            FIELD_CONQUERED_RADIUS to conqueredRadiusKm,
            FIELD_ACTIVE_PLAYER to activePlayerNumber.toLong()
        )).await()
    }

    override suspend fun resetGame() {
        docRef.update(mapOf(
            "player1.faction" to "",
            "player2.faction" to "",
            "activePlayerNumber" to 1L,
            "conqueredRadiusKm" to 0.0,
            "slot" to 0L
        )).await()
    }
}