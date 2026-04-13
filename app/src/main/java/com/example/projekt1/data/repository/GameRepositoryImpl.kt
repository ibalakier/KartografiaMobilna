package com.example.projekt1.data.repository

import com.example.projekt1.Faction
import com.example.projekt1.domain.model.GameData
import com.example.projekt1.domain.model.RoomState
import com.example.projekt1.domain.repository.GameRepository
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

class GameRepositoryImpl(
    private val firestore: FirebaseFirestore
) : GameRepository {

    companion object {
        private const val COLLECTION_PATH = "game"
        private const val DOCUMENT_PATH = "ifEs8o9KugHUmURSGScJ"
        private const val FIELD_NAME_ACTIVE_ROUND = "activeRound"
        private const val FIELD_NAME_PLAYER_PREFIX = "player"
        private const val FIELD_NAME_FACTION = "faction"
    }

    private val docRef: DocumentReference
        get() = firestore
            .collection(COLLECTION_PATH)
            .document(DOCUMENT_PATH)

    override suspend fun observeGameData(): GameData? {
        val snapshot = docRef.get(Source.SERVER).await()

        val data = snapshot.data ?: return null

        val player1Faction = data.getFaction(1)
        val player2Faction = data.getFaction(2)

        val roomState = when {
            player1Faction == null && player2Faction == null -> RoomState.EMPTY
            player1Faction != null && player2Faction == null -> RoomState.PLAYER1_ONLY
            else -> RoomState.FULL
        }

        val faction = if (roomState == RoomState.PLAYER1_ONLY) {
            if (player1Faction == Faction.ROgród) Faction.ROlandia else Faction.ROgród
        } else {
            null
        }

        return GameData(
            activeRound = data[FIELD_NAME_ACTIVE_ROUND] as? Int ?: 0,
            playerNumber = if (player1Faction == null) 1 else 2,
            roomState = roomState,
            faction = faction,
        )
    }

    private fun Map<*, *>.getFaction(playerNumber: Int): Faction? {
        val factionString = (this["$FIELD_NAME_PLAYER_PREFIX$playerNumber"] as? Map<*, *>)
            ?.get(FIELD_NAME_FACTION) as? String?
            ?: ""
        return runCatching { Faction.valueOf(factionString) }.getOrNull()
    }

    override suspend fun setPlayerFaction(
        playerNumber: Int,
        faction: Faction
    ) {
        docRef.update(
            mapOf(
                "${FIELD_NAME_PLAYER_PREFIX}$playerNumber.$FIELD_NAME_FACTION" to faction.name
            )
        ).await()
    }
}
