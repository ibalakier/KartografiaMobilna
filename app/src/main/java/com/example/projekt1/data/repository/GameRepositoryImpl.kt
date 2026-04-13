package com.example.projekt1.data.repository

import com.example.projekt1.domain.model.GameData
import com.example.projekt1.domain.repository.GameRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class GameRepositoryImpl(
    private val firestore: FirebaseFirestore
) : GameRepository {

    companion object {
        private const val COLLECTION_PATH = "game"
        private const val DOCUMENT_PATH = "ifEs8o9KugHUmURSGScJ"
        private const val FIELD_NAME_ACTIVE_ROUND = "activeRound"
    }

    override fun observeGameData(): Flow<GameData?> = callbackFlow {
        val docRef = firestore
            .collection(COLLECTION_PATH)
            .document(DOCUMENT_PATH)
        val listener = docRef.addSnapshotListener { snapshot, exception ->
            if (exception != null) {
                close(exception)
                return@addSnapshotListener
            }
            val data = snapshot?.data?.let {
                GameData(
                    activeRound = it[FIELD_NAME_ACTIVE_ROUND] as? Int ?: 0
                )
            }
            trySend(data)
        }
        awaitClose { listener.remove() }
    }
}