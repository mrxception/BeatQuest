package com.beatquest

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log
import android.view.View
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import androidx.fragment.app.FragmentContainerView
import android.view.ViewGroup
import android.content.SharedPreferences

class MainActivity : AppCompatActivity() {

    private lateinit var coroutineScope: CoroutineScope
    private lateinit var sharedPreferences: SharedPreferences
    private val userId: String by lazy { getOrCreateUserId() }
    private val database by lazy {
        FirebaseDatabase.getInstance("https://wewe-b3760-default-rtdb.asia-southeast1.firebasedatabase.app").reference
    }

    companion object {
        const val PREFS_NAME = "BeatQuestPrefs"
        const val KEY_USER_ID = "userId"
    }

    private fun getOrCreateUserId(): String {
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUserId = sharedPreferences.getString(KEY_USER_ID, null)
        if (savedUserId != null) {
            Log.d("MainActivity", "Retrieved saved userId: $savedUserId")
            return savedUserId
        }

        val account = GoogleSignIn.getLastSignedInAccount(this)
        val email = account?.email
        val userId = if (email != null) {
            email.replace(".", "_")
        } else {
            generateUniqueAnonId()
        }

        sharedPreferences.edit().putString(KEY_USER_ID, userId).apply()
        Log.d("MainActivity", "Generated and saved new userId: $userId")
        return userId
    }

    private fun generateUniqueAnonId(): String {
        var anonId: String
        var attempt = 1
        while (true) {
            anonId = "anon$attempt"
            var exists = false
            runBlocking(Dispatchers.IO) {
                try {
                    val snapshot = database.child("users").child(anonId).get().await()
                    exists = snapshot.exists()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error checking anonId existence: ${e.message}")
                }
            }
            if (!exists) return anonId
            attempt++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        coroutineScope = CoroutineScope(Dispatchers.Main)

        val account = GoogleSignIn.getLastSignedInAccount(this)
        val userName = account?.displayName ?: userId.replace("_", ".").substringBefore("@gmail.com")
        val photoUrl = account?.photoUrl

        val welcomeText = findViewById<TextView>(R.id.welcomeText)
        welcomeText.text = userName

        val profileImage = findViewById<ImageView>(R.id.profileImage)
        if (photoUrl != null) {
            Glide.with(this)
                .load(photoUrl)
                .transform(CircleCrop())
                .placeholder(R.drawable.avatar)
                .error(R.drawable.avatar)
                .into(profileImage)
        } else {
            profileImage.setImageResource(R.drawable.avatar)
        }

        val btnHome = findViewById<ImageButton>(R.id.btnHome)
        val btnBattle = findViewById<ImageButton>(R.id.btnBattle)
        val btnLeaderBoard = findViewById<ImageButton>(R.id.btnLdrbrd)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment().apply {
                    arguments = Bundle().apply { putString("userId", userId) }
                })
                .commit()
            updateIndicators(true, false, false)
        }

        btnHome.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment().apply {
                    arguments = Bundle().apply { putString("userId", userId) }
                })
                .addToBackStack(null)
                .commit()
            updateIndicators(true, false, false)
        }

        btnBattle.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, BattleFragment().apply {
                    arguments = Bundle().apply { putString("userId", userId) }
                })
                .addToBackStack(null)
                .commit()
            updateIndicators(false, true, false)
        }

        btnLeaderBoard.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LeaderboardFragment().apply {
                    arguments = Bundle().apply { putString("userId", userId) }
                })
                .addToBackStack(null)
                .commit()
            updateIndicators(false, false, true)
        }

        saveDataToFirebase("is_online", true)
        setupOnDisconnect()
        monitorChallenges()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveDataToFirebase("is_online", false)
        coroutineScope.cancel()
    }

    private fun setupOnDisconnect() {
        val userRef = database.child("users").child(userId).child("is_online")
        userRef.onDisconnect().setValue(false).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("MainActivity", "onDisconnect set to false for userId: $userId")
            } else {
                Log.e("MainActivity", "Failed to set onDisconnect: ${task.exception?.message}")
            }
        }
    }

    private fun monitorChallenges() {
        database.child("challenges").child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.child("challenged").getValue(Boolean::class.java) == true) {
                    val challengerId = snapshot.child("challengerId").getValue(String::class.java)
                    if (challengerId != null) {
                        coroutineScope.launch {
                            if (!isInBattle()) {
                                showChallengePopup(challengerId)
                            } else {
                                Log.d("MainActivity", "Cannot accept challenge: Already in battle")
                                saveDataToFirebase("challenged", false, "challenges/$userId")
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Challenge monitoring cancelled: ${error.message}")
            }
        })
    }

    private fun showChallengePopup(challengerId: String) {
        val dialogFragment = ChallengeDialogFragment.newInstance(challengerId)
        dialogFragment.show(supportFragmentManager, "ChallengeDialog")
    }

    fun acceptChallenge(challengerId: String) {
        coroutineScope.launch {
            if (!isInBattle()) {
                Log.d("MainActivity", "Accepted challenge from $challengerId")
                Toast.makeText(this@MainActivity, "You accepted $challengerId's challenge!", Toast.LENGTH_SHORT).show()
                saveDataToFirebase("challenged", false, "challenges/$userId")
                saveDataToFirebase("in_battle", true)
                saveDataToFirebase("battle_with", challengerId)
                database.child("challenges").child(challengerId).child("challenged").setValue(false).await()
                database.child("users").child(challengerId).child("in_battle").setValue(true).await()
                database.child("users").child(challengerId).child("battle_with").setValue(userId).await()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, BattleFragment().apply {
                        arguments = Bundle().apply { putString("userId", userId) }
                    })
                    .addToBackStack(null)
                    .commit()
                updateIndicators(false, true, false)
            } else {
                Log.d("MainActivity", "Cannot accept challenge: Already in battle")
            }
        }
    }

    fun declineChallenge(challengerId: String) {
        Log.d("MainActivity", "Declined challenge from $challengerId")
        saveDataToFirebase("challenged", false, "challenges/$userId")
        database.child("challenges").child(challengerId).child("challenged").setValue(false)
    }

    private suspend fun isInBattle(): Boolean {
        return try {
            val snapshot = database.child("users").child(userId).child("in_battle").get().await()
            snapshot.getValue(Boolean::class.java) ?: false
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking battle state: ${e.message}")
            false
        }
    }

    fun saveDataToFirebase(key: String, value: Any, path: String = "users/$userId") {
        Log.d("MainActivity", "Attempting to save: $key = $value at path: $path")
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val userRef = database.child(path).child(key)
                userRef.setValue(value).addOnSuccessListener {
                    Log.d("MainActivity", "Successfully saved to Firebase: $key = $value at $path")
                }.addOnFailureListener { exception ->
                    Log.e("MainActivity", "Failed to save data to Firebase: ${exception.message}", exception)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Exception in saveDataToFirebase: ${e.message}", e)
            }
        }
    }

    fun updateIndicators(homeVisible: Boolean, statsVisible: Boolean, profileVisible: Boolean) {
        findViewById<View>(R.id.indicatorHome).visibility = if (homeVisible) View.VISIBLE else View.GONE
        findViewById<View>(R.id.indicatorStats).visibility = if (statsVisible) View.VISIBLE else View.GONE
        findViewById<View>(R.id.indicatorLeaderBoard).visibility = if (profileVisible) View.VISIBLE else View.GONE
        findViewById<View>(R.id.user_header).visibility = if (profileVisible) View.GONE else View.VISIBLE

        val fragmentContainer = findViewById<FragmentContainerView>(R.id.fragment_container)
        val layoutParams = fragmentContainer.layoutParams as ViewGroup.MarginLayoutParams
        if (profileVisible) {
            layoutParams.setMargins(0, 0, 0, 0)
        } else {
            val verticalMarginPx = (20 * resources.displayMetrics.density).toInt()
            val horizontalMarginPx = (15 * resources.displayMetrics.density).toInt()
            layoutParams.setMargins(horizontalMarginPx, verticalMarginPx, horizontalMarginPx, verticalMarginPx)
        }
        fragmentContainer.layoutParams = layoutParams
        Log.d("MainActivity", "Set fragment_container margins: vertical=${if (profileVisible) 0 else 20}dp, horizontal=${if (profileVisible) 0 else 15}dp for profileVisible=$profileVisible")
    }
}