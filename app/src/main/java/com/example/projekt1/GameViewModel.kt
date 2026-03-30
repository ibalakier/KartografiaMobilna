package com.example.projekt1

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class GameViewModel : ViewModel() {
    companion object {
        private const val TAG = "GameViewModel"
    }

    init {
        val db = Firebase.firestore
        val docRef = db.collection("game").document("ifEs8o9KugHUmURSGScJ")
        docRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                Log.d(TAG, "Current data: ${snapshot.data}")
            } else {
                Log.d(TAG, "Current data: null")
            }
        }

    }
    val db = Firebase.firestore
    fun updateData() {
        // Create a new user with a first and last name
        val user = hashMapOf(
            "first" to "Ada",
            "last" to "Lovelace",
            "born" to 1815,
        )


        db.collection("game").document("ifEs8o9KugHUmURSGScJ")
            .update(user as Map<String, Any>)
            .addOnSuccessListener {
                // 2. Usunięto parametr documentReference, logujemy po prostu sukces
                Log.d(TAG, "Dokument został pomyślnie zaktualizowany!")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Błąd podczas aktualizacji dokumentu", e)
            }
    }
    fun onFrakcjaSelected(frakcja: Frakcja, onNavigate: (String) -> Unit) {
        onNavigate("game_screen/${frakcja.name}")
    }
}