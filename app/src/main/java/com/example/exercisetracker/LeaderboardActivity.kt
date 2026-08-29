package com.example.exercisetracker

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class LeaderboardActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: LeaderboardAdapter
    private val userStatsList = mutableListOf<UserStats>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupRecyclerView()
        setupNavigation()
        fetchLeaderboardData()
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.leaderboardRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = LeaderboardAdapter(userStatsList)
        recyclerView.adapter = adapter
    }

    private fun setupNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigation.selectedItemId = R.id.nav_leaderboard

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_leaderboard -> true
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun fetchLeaderboardData() {
        db.collection("users")
            .orderBy("totalReps", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { documents ->
                userStatsList.clear()
                if (documents.isEmpty) {
                    Log.d("Leaderboard", "No users found in Firestore")
                    // If no one is on leaderboard, show current user if they exist
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        Toast.makeText(this, "Leaderboard is empty. Have you saved a workout yet?", Toast.LENGTH_LONG).show()
                    }
                }
                for (doc in documents) {
                    try {
                        val stats = doc.toObject(UserStats::class.java).copy(userId = doc.id)
                        userStatsList.add(stats)
                    } catch (e: Exception) {
                        Log.e("Leaderboard", "Error parsing user: ${e.message}")
                    }
                }
                adapter.updateUsers(userStatsList)
            }
            .addOnFailureListener { e ->
                Log.e("Leaderboard", "Error fetching: ${e.message}")
                Toast.makeText(this, "Failed to load rankings", Toast.LENGTH_SHORT).show()
            }
    }
}
