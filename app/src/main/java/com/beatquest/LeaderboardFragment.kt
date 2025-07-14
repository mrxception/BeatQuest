package com.beatquest

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LeaderboardFragment : Fragment() {

    private lateinit var leaderboardContainer: LinearLayout
    private lateinit var userTextView: TextView
    private lateinit var userTrophiesTextView: TextView
    private lateinit var top1NameTextView: TextView
    private lateinit var top1TrophiesTextView: TextView
    private lateinit var top2NameTextView: TextView
    private lateinit var top2TrophiesTextView: TextView
    private lateinit var top3NameTextView: TextView
    private lateinit var top3TrophiesTextView: TextView
    private lateinit var coroutineScope: CoroutineScope

    private val userId: String by lazy {
        arguments?.getString("userId") ?: throw IllegalStateException("userId not provided")
    }

    private val database by lazy {
        FirebaseDatabase.getInstance("https://wewe-b3760-default-rtdb.asia-southeast1.firebasedatabase.app").reference
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_leaderboard, container, false)

        leaderboardContainer = view.findViewById(R.id.leaderboardContainer)
        userTextView = view.findViewById(R.id.user)
        userTrophiesTextView = view.findViewById(R.id.user_trophies)
        top1NameTextView = view.findViewById(R.id.top1name)
        top1TrophiesTextView = view.findViewById(R.id.top1trophies)
        top2NameTextView = view.findViewById(R.id.top2name)
        top2TrophiesTextView = view.findViewById(R.id.top2trophies)
        top3NameTextView = view.findViewById(R.id.top3name)
        top3TrophiesTextView = view.findViewById(R.id.top3trophies)
        coroutineScope = CoroutineScope(Dispatchers.Main + Job())

        loadLeaderboardData()
        loadUserProfile()

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        coroutineScope.cancel()
    }

    private fun loadLeaderboardData() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val snapshot = database.child("users").get().await()
                val users = mutableListOf<Pair<String, Int>>()

                for (userSnapshot in snapshot.children) {
                    val userId = userSnapshot.key ?: continue
                    val trophies = userSnapshot.child("trophies").getValue(Int::class.java) ?: 0
                    
                    if (userSnapshot.hasChild("is_online") || userSnapshot.hasChild("trophies")) {
                        users.add(Pair(userId, trophies))
                        Log.d("LeaderboardFragment", "Fetched user: $userId, trophies: $trophies")
                    } else {
                        Log.w("LeaderboardFragment", "Skipping invalid user: $userId")
                    }
                }

                if (users.isEmpty()) {
                    Log.w("LeaderboardFragment", "No valid users found in Firebase 'users' node")
                } else {
                    Log.d("LeaderboardFragment", "Fetched ${users.size} valid users")
                }

                
                val sortedUsers = users.sortedWith(compareByDescending<Pair<String, Int>> { it.second }
                    .thenBy { it.first })

                
                coroutineScope.launch(Dispatchers.Main) {
                    
                    while (leaderboardContainer.childCount > 0) {
                        leaderboardContainer.removeViewAt(0)
                    }

                    
                    if (sortedUsers.isNotEmpty()) {
                        top1NameTextView.text = formatUsername(sortedUsers.getOrNull(0)?.first ?: "-")
                        top1TrophiesTextView.text = "🏆${sortedUsers.getOrNull(0)?.second ?: "-"}"
                    } else {
                        top1NameTextView.text = "-"
                        top1TrophiesTextView.text = "🏆-"
                    }
                    if (sortedUsers.size > 1) {
                        top2NameTextView.text = formatUsername(sortedUsers.getOrNull(1)?.first ?: "-")
                        top2TrophiesTextView.text = "🏆${sortedUsers.getOrNull(1)?.second ?: "-"}"
                    } else {
                        top2NameTextView.text = "-"
                        top2TrophiesTextView.text = "🏆-"
                    }
                    if (sortedUsers.size > 2) {
                        top3NameTextView.text = formatUsername(sortedUsers.getOrNull(2)?.first ?: "-")
                        top3TrophiesTextView.text = "🏆${sortedUsers.getOrNull(2)?.second ?: "-"}"
                    } else {
                        top3NameTextView.text = "-"
                        top3TrophiesTextView.text = "🏆-"
                    }

                    
                    for (i in 3 until minOf(sortedUsers.size, 10)) {
                        val user = sortedUsers[i]
                        addLeaderboardRow(i + 1, user.first, user.second)
                    }

                    Log.d("LeaderboardFragment", "Leaderboard loaded: ${sortedUsers.size} users, displayed ranks 4-${minOf(sortedUsers.size, 10)}")
                }
            } catch (e: Exception) {
                Log.e("LeaderboardFragment", "Failed to load leaderboard: ${e.message}", e)
                coroutineScope.launch(Dispatchers.Main) {
                    top1NameTextView.text = "-"
                    top1TrophiesTextView.text = "🏆-"
                    top2NameTextView.text = "-"
                    top2TrophiesTextView.text = "🏆-"
                    top3NameTextView.text = "-"
                    top3TrophiesTextView.text = "🏆-"
                    while (leaderboardContainer.childCount > 0) {
                        leaderboardContainer.removeViewAt(0)
                    }
                    Log.w("LeaderboardFragment", "Cleared leaderboard due to error")
                }
            }
        }
    }

    private fun loadUserProfile() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val snapshot = database.child("users").child(userId).get().await()
                val trophies = snapshot.child("trophies").getValue(Int::class.java) ?: 0
                coroutineScope.launch(Dispatchers.Main) {
                    userTextView.text = formatUsername(userId)
                    userTrophiesTextView.text = "🏆$trophies"
                    Log.d("LeaderboardFragment", "User profile loaded: userId=$userId, trophies=$trophies")
                }
            } catch (e: Exception) {
                Log.e("LeaderboardFragment", "Failed to load user profile: ${e.message}")
                coroutineScope.launch(Dispatchers.Main) {
                    userTextView.text = formatUsername(userId)
                    userTrophiesTextView.text = "🏆-"
                }
            }
        }
    }

    private fun formatUsername(userId: String): String {
        return userId.replace("_", ".").substringBefore("@gmail.com")
    }

    private fun addLeaderboardRow(rank: Int, userId: String, trophies: Int) {
        val rowView = layoutInflater.inflate(R.layout.leaderboard_user_item, leaderboardContainer, false)
        val rankTextView = rowView.findViewById<TextView>(R.id.rank)
        val nameTextView = rowView.findViewById<TextView>(R.id.name)
        val trophiesTextView = rowView.findViewById<TextView>(R.id.trophies)

        rankTextView.text = "$rank"
        nameTextView.text = formatUsername(userId)
        trophiesTextView.text = "🏆$trophies"

        leaderboardContainer.addView(rowView)
        Log.d("LeaderboardFragment", "Added row: rank=$rank, userId=$userId, trophies=$trophies")
    }
}