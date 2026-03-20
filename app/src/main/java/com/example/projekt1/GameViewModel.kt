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
    fun onFrakcjaSelected(frakcja: Frakcja, onNavigate: (String) -> Unit) {
        onNavigate("game_screen/${frakcja.name}")
    }
}