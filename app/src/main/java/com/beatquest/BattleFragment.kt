package com.beatquest

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.DataSourcesRequest
import com.google.android.gms.fitness.request.SensorRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class BattleFragment : Fragment() {

    private lateinit var mainContainer: LinearLayout
    private lateinit var exerciseText: TextView
    private lateinit var heartRateText: TextView
    private lateinit var sleepText: TextView
    private lateinit var sleepProgress: ProgressBar
    private lateinit var playerHpBar: ProgressBar
    private lateinit var playerHpText: TextView
    private lateinit var playerShieldBar: ProgressBar
    private lateinit var playerShieldText: TextView
    private lateinit var playerManaBar: ProgressBar
    private lateinit var playerManaText: TextView
    private lateinit var enemyHpBar: ProgressBar
    private lateinit var enemyHpText: TextView
    private lateinit var enemyShieldBar: ProgressBar
    private lateinit var enemyShieldText: TextView
    private lateinit var enemyManaBar: ProgressBar
    private lateinit var enemyManaText: TextView
    private lateinit var playerName: TextView
    private lateinit var enemyName: TextView
    private lateinit var mapImage: ImageView
    private lateinit var playerHero: ImageView
    private lateinit var enemyHero: ImageView
    private lateinit var skill1: LinearLayout
    private lateinit var skill2: LinearLayout
    private lateinit var skill3: LinearLayout
    private lateinit var skill1Image: ImageView
    private lateinit var skill2Image: ImageView
    private lateinit var skill3Image: ImageView
    private lateinit var btnSurrender: Button
    private lateinit var coroutineScope: CoroutineScope
    private var onlineChallengeDialog: Dialog? = null
    private var waitingDialog: Dialog? = null
    private var gameOverDialog: Dialog? = null
    private var onlineUsersListener: ValueEventListener? = null
    private var battleStatusListener: ValueEventListener? = null
    private var enemyAttackListener: ValueEventListener? = null
    private var enemyStatsListener: ValueEventListener? = null
    private var currentSearchQuery: String = ""
    private var lastBpm: Float = 0f
    private var battleStartTime: Long = 0L
    private var skill1LastUsed: Long = 0L
    private var skill2LastUsed: Long = 0L
    private var skill3LastUsed: Long = 0L
    private var playerLevel: Int = 1
    private val skillCooldown = 30_000L

    private val userId: String by lazy {
        arguments?.getString("userId") ?: throw IllegalStateException("userId not provided")
    }

    private val database by lazy {
        FirebaseDatabase.getInstance("https://wewe-b3760-default-rtdb.asia-southeast1.firebasedatabase.app").reference
    }

    private val fitnessOptions: FitnessOptions by lazy {
        FitnessOptions.builder()
            .addDataType(DataType.TYPE_MOVE_MINUTES, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_SLEEP_SEGMENT, FitnessOptions.ACCESS_READ)
            .build()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_battle, container, false)

        mainContainer = view.findViewById(R.id.mainContainer)
        exerciseText = view.findViewById(R.id.exerciseText)
        heartRateText = view.findViewById(R.id.heartRateText)
        sleepText = view.findViewById(R.id.sleepText)
        sleepProgress = view.findViewById(R.id.sleepProgress)
        playerHpBar = view.findViewById(R.id.player_hp)
        playerHpText = view.findViewById(R.id.player_hp_text)
        playerShieldBar = view.findViewById(R.id.player_shield)
        playerShieldText = view.findViewById(R.id.player_shield_text)
        playerManaBar = view.findViewById(R.id.player_mana)
        playerManaText = view.findViewById(R.id.player_mana_text)
        enemyHpBar = view.findViewById(R.id.enemy_hp)
        enemyHpText = view.findViewById(R.id.enemy_hp_text)
        enemyShieldBar = view.findViewById(R.id.enemy_shield)
        enemyShieldText = view.findViewById(R.id.enemy_shield_text)
        enemyManaBar = view.findViewById(R.id.enemy_mana)
        enemyManaText = view.findViewById(R.id.enemy_mana_text)
        playerName = view.findViewById(R.id.player_name)
        enemyName = view.findViewById(R.id.enemy_name)
        mapImage = view.findViewById(R.id.mapImage)
        playerHero = view.findViewById(R.id.playerAvtr)
        enemyHero = view.findViewById(R.id.enemyAvtr)
        skill1 = view.findViewById(R.id.skill1)
        skill2 = view.findViewById(R.id.skill2)
        skill3 = view.findViewById(R.id.skill3)
        skill1Image = view.findViewById(R.id.skill1_icon)
        skill2Image = view.findViewById(R.id.skill2_icon)
        skill3Image = view.findViewById(R.id.skill3_icon)
        btnSurrender = view.findViewById(R.id.btn_surrender)
        coroutineScope = CoroutineScope(Dispatchers.Main + Job())

        if (skill1Image == null || skill2Image == null || skill3Image == null) {
            Log.e("BattleFragment", "Skill image views are null. Check fragment_battle.xml for skill1_icon, skill2_icon, skill3_icon")
            Toast.makeText(requireContext(), "Error: Skill icons not found", Toast.LENGTH_LONG).show()
        }

        val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
        playerName.text = account.email?.substringBefore("@gmail.com") ?: userId.replace("_", ".")

        playerHpBar.progress = 50
        playerHpText.text = "50/100"
        playerShieldBar.progress = 25
        playerShieldText.text = "25/50"
        playerManaBar.progress = 0
        playerManaText.text = "0/100"
        saveDataToFirebase("hp", 50)
        saveDataToFirebase("shield", 25)
        saveDataToFirebase("mana", 0)

        Glide.with(this)
            .load(R.drawable.assasin)
            .apply(RequestOptions().fitCenter())
            .into(playerHero)

        Glide.with(this)
            .load(R.drawable.assasin_2)
            .apply(RequestOptions().fitCenter())
            .into(enemyHero)

        setupSkillListeners()
        btnSurrender.setOnClickListener {
            coroutineScope.launch {
                if (isInBattle()) {
                    showGameOverDialog("loss", -5)
                    endBattle("loss")
                }
            }
        }
        saveDataToFirebase("is_online", true)
        setupOnDisconnect()
        fetchDataSources()
        startRealTimeHeartRate()
        startPeriodicDataFetch()
        loadDataFromFirebase()
        setupBattleStatusListener()

        updateSkillIcons()

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        saveDataToFirebase("is_online", false)
        coroutineScope.cancel()
        stopRealTimeHeartRate()
        onlineChallengeDialog?.dismiss()
        waitingDialog?.dismiss()
        gameOverDialog?.dismiss()
        onlineUsersListener?.let {
            database.child("users").removeEventListener(it)
        }
        battleStatusListener?.let {
            database.child("users").child(userId).child("in_battle").removeEventListener(it)
        }
        enemyAttackListener?.let {
            database.child("attacks").child(userId).removeEventListener(it)
        }
        enemyStatsListener?.let {
            runBlocking(Dispatchers.IO) {
                database.child("users").child(userId).child("battle_with").get().await()
                    .getValue(String::class.java)?.let { opponentId ->
                        database.child("users").child(opponentId).removeEventListener(it)
                    }
            }
        }
    }

    private fun setupOnDisconnect() {
        if (!isAdded) return
        val userRef = database.child("users").child(userId).child("is_online")
        userRef.onDisconnect().setValue(false).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("BattleFragment", "onDisconnect set to false for userId: $userId")
            } else {
                Log.e("BattleFragment", "Failed to set onDisconnect: ${task.exception?.message}")
            }
        }
    }

    private fun setupBattleStatusListener() {
        if (!isAdded) return
        battleStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val isInBattle = snapshot.getValue(Boolean::class.java) ?: false
                if (isInBattle) {
                    coroutineScope.launch(Dispatchers.Main) {
                        val battleWith = database.child("users").child(userId).child("battle_with").get().await()
                        val opponentId = battleWith.getValue(String::class.java) ?: "Unknown"
                        enemyName.text = opponentId.substringBefore("@gmail.com").replace("_", ".")
                        Toast.makeText(requireContext(), "$opponentId accepted your challenge!", Toast.LENGTH_SHORT).show()
                        battleStartTime = System.currentTimeMillis()
                        saveDataToFirebase("battle_start_time", battleStartTime)
                        fetchFitnessData()
                        saveDataToFirebase("hp", playerHpBar.progress)
                        saveDataToFirebase("shield", playerShieldBar.progress)
                        saveDataToFirebase("mana", playerManaBar.progress)
                        delay(2000)
                        loadEnemyData(opponentId)
                        setupEnemyAttackListener(opponentId)
                        startBattleTimer()
                    }
                }
                updateUIForBattleStatus(isInBattle)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("BattleFragment", "Failed to monitor battle status: ${error.message}")
            }
        }
        database.child("users").child(userId).child("in_battle").addValueEventListener(battleStatusListener!!)
    }

    private fun loadEnemyData(opponentId: String) {
        if (!isAdded) return
        Log.d("BattleFragment", "Setting up real-time listener for enemy data: opponentId=$opponentId")
        enemyStatsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                if (snapshot.exists()) {
                    coroutineScope.launch(Dispatchers.Main) {
                        val hp = snapshot.child("hp").getValue(Int::class.java) ?: 50
                        val shield = snapshot.child("shield").getValue(Int::class.java) ?: 25
                        val mana = snapshot.child("mana").getValue(Int::class.java) ?: 0
                        enemyHpBar.progress = hp
                        enemyHpText.text = "$hp/100"
                        enemyShieldBar.progress = shield
                        enemyShieldText.text = "$shield/50"
                        enemyManaBar.progress = mana
                        enemyManaText.text = "$mana/100"
                        Log.d("BattleFragment", "Enemy data updated: HP=$hp, Shield=$shield, Mana=$mana")
                        if (hp <= 0 && snapshot.child("hp").exists()) {
                            Log.d("BattleFragment", "Enemy HP is 0, ending battle")
                            endBattle("win")
                        }
                    }
                } else {
                    Log.w("BattleFragment", "No data found for opponent_id: $opponentId, setting defaults")
                    database.child("users").child(opponentId).child("hp").setValue(50)
                    database.child("users").child(opponentId).child("shield").setValue(25)
                    database.child("users").child(opponentId).child("mana").setValue(0)
                    coroutineScope.launch(Dispatchers.Main) {
                        enemyHpBar.progress = 50
                        enemyHpText.text = "50/100"
                        enemyShieldBar.progress = 25
                        enemyShieldText.text = "25/50"
                        enemyManaBar.progress = 0
                        enemyManaText.text = "0/100"
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("BattleFragment", "Enemy data listener cancelled: ${error.message}")
                coroutineScope.launch(Dispatchers.Main) {
                    enemyHpBar.progress = 50
                    enemyHpText.text = "50/100"
                    enemyShieldBar.progress = 25
                    enemyShieldText.text = "25/50"
                    enemyManaBar.progress = 0
                    enemyManaText.text = "0/100"
                }
            }
        }
        database.child("users").child(opponentId).addValueEventListener(enemyStatsListener!!)
    }

    private fun setupSkillListeners() {
        skill1.setOnClickListener {
            if (playerLevel >= 1) {
                useSkill(1, 10)
            } else {
                Toast.makeText(requireContext(), "Skill 1 is locked! Reach level 1 to unlock.", Toast.LENGTH_SHORT).show()
                Log.d("BattleFragment", "Skill 1 is locked (level $playerLevel < 1)")
            }
        }
        skill2.setOnClickListener {
            if (playerLevel >= 2) {
                useSkill(2, 20)
            } else {
                Toast.makeText(requireContext(), "Skill 2 is locked! Reach level 2 to unlock.", Toast.LENGTH_SHORT).show()
                Log.d("BattleFragment", "Skill 2 is locked (level $playerLevel < 2)")
            }
        }
        skill3.setOnClickListener {
            if (playerLevel >= 3) {
                useSkill(3, 30)
            } else {
                Toast.makeText(requireContext(), "Skill 3 is locked! Reach level 3 to unlock.", Toast.LENGTH_SHORT).show()
                Log.d("BattleFragment", "Skill 3 is locked (level $playerLevel < 3)")
            }
        }
    }

    private fun useSkill(skillNumber: Int, damage: Int) {
        if (!isAdded) return
        coroutineScope.launch(Dispatchers.Main) {
            if (!isSkillUnlocked(skillNumber)) {
                Toast.makeText(requireContext(), "Skill $skillNumber is locked! Reach level $skillNumber to unlock.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val currentTime = System.currentTimeMillis()
            val lastUsed = when (skillNumber) {
                1 -> skill1LastUsed
                2 -> skill2LastUsed
                3 -> skill3LastUsed
                else -> 0L
            }
            if (currentTime - lastUsed < skillCooldown) {
                Toast.makeText(requireContext(), "Skill $skillNumber is on cooldown!", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val mana = playerManaBar.progress
            if (mana < damage) {
                Toast.makeText(requireContext(), "Not enough mana for Skill $skillNumber!", Toast.LENGTH_SHORT).show()
                return@launch
            }

            when (skillNumber) {
                1 -> skill1LastUsed = currentTime
                2 -> skill2LastUsed = currentTime
                3 -> skill3LastUsed = currentTime
            }

            playerManaBar.progress -= damage
            playerManaText.text = "${playerManaBar.progress}/100"
            saveDataToFirebase("mana", playerManaBar.progress)
            val targetId = database.child("users").child(userId).child("battle_with").get().await().getValue(String::class.java)
            if (targetId != null) {
                saveDataToFirebase("attack", mapOf("target" to targetId, "damage" to damage), "attacks/$userId")
                Log.d("BattleFragment", "Sent attack: skill=$skillNumber, damage=$damage, target=$targetId")
            } else {
                Log.e("BattleFragment", "Failed to get battle_with ID for attack")
                Toast.makeText(requireContext(), "Error: Cannot send attack, opponent not found", Toast.LENGTH_SHORT).show()
            }
            Toast.makeText(requireContext(), "Used Skill $skillNumber, dealt $damage damage!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isSkillUnlocked(skillNumber: Int): Boolean {
        return when (skillNumber) {
            1 -> playerLevel >= 1
            2 -> playerLevel >= 2
            3 -> playerLevel >= 3
            else -> false
        }
    }

    private fun updateSkillIcons() {
        if (!isAdded) return
        try {
            skill1Image.setImageResource(if (playerLevel >= 1) R.drawable.skill1 else R.drawable.lock)
            skill1.isEnabled = playerLevel >= 1
            skill2Image.setImageResource(if (playerLevel >= 2) R.drawable.skill2 else R.drawable.lock)
            skill2.isEnabled = playerLevel >= 2
            skill3Image.setImageResource(if (playerLevel >= 3) R.drawable.skill3 else R.drawable.lock)
            skill3.isEnabled = playerLevel >= 3
            Log.d("BattleFragment", "Updated skill icons: level=$playerLevel")
        } catch (e: Exception) {
            Log.e("BattleFragment", "Error updating skill icons: ${e.message}", e)
            Toast.makeText(requireContext(), "Error updating skill icons", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupEnemyAttackListener(opponentId: String) {
        enemyAttackListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                if (snapshot.exists()) {
                    val attack = snapshot.child("attack")
                    val target = attack.child("target").getValue(String::class.java)
                    val damage = attack.child("damage").getValue(Int::class.java) ?: 0
                    if (target == userId && damage > 0) {
                        coroutineScope.launch(Dispatchers.Main) {
                            val shield = playerShieldBar.progress
                            val damageAfterShield = if (shield >= damage) 0 else damage - shield
                            playerShieldBar.progress = (shield - damage).coerceAtLeast(0)
                            playerShieldText.text = "${playerShieldBar.progress}/50"
                            playerHpBar.progress = (playerHpBar.progress - damageAfterShield).coerceAtLeast(0)
                            playerHpText.text = "${playerHpBar.progress}/100"
                            saveDataToFirebase("shield", playerShieldBar.progress)
                            saveDataToFirebase("hp", playerHpBar.progress)
                            database.child("attacks").child(userId).child("attack").setValue(null)
                            Log.d("BattleFragment", "Processed attack from $opponentId: Damage=$damage, Player HP=${playerHpBar.progress}, Shield=${playerShieldBar.progress}")
                            if (playerHpBar.progress <= 0) {
                                endBattle("loss")
                            }
                        }
                    } else {
                        Log.d("BattleFragment", "No valid attack for userId=$userId in attacks/$opponentId")
                    }
                } else {
                    Log.d("BattleFragment", "No attack data in attacks/$opponentId")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("BattleFragment", "Failed to monitor enemy attacks: ${error.message}")
            }
        }
        database.child("attacks").child(opponentId).addValueEventListener(enemyAttackListener!!)
    }

    private fun startBattleTimer() {
        coroutineScope.launch {
            val battleDuration = 5 * 60 * 1000L
            delay(battleDuration)
            if (isActive && isInBattle()) {
                val playerHp = playerHpBar.progress
                val enemyHp = enemyHpBar.progress
                val result = when {
                    playerHp > enemyHp -> "win"
                    playerHp < enemyHp -> "loss"
                    else -> "draw"
                }
                endBattle(result)
            }
        }
    }

    private suspend fun endBattle(result: String) {
        if (!isAdded) return
        val opponentId = database.child("users").child(userId).child("battle_with").get().await().getValue(String::class.java) ?: return
        val trophyChange = when (result) {
            "win" -> 10
            "loss" -> -5
            "draw" -> 2
            else -> 0
        }
        updateTrophies(trophyChange)
        database.child("users").child(opponentId).child("trophies").setValue(
            (database.child("users").child(opponentId).child("trophies").get().await().getValue(Int::class.java) ?: 0) + when (result) {
                "win" -> -5
                "loss" -> 10
                "draw" -> 2
                else -> 0
            }
        )
        saveDataToFirebase("in_battle", false)
        saveDataToFirebase("battle_with", null)
        database.child("users").child(opponentId).child("in_battle").setValue(false)
        database.child("users").child(opponentId).child("battle_with").setValue(null)
    }

    private fun showGameOverDialog(result: String, trophyChange: Int) {
        if (!isAdded) return
        gameOverDialog = Dialog(requireContext()).apply {
            setContentView(R.layout.layout_gameover)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
        }

        val titleTextView = gameOverDialog?.findViewById<TextView>(R.id.title)
        val trophyTextView = gameOverDialog?.findViewById<TextView>(R.id.trophies)
        val playAgainButton = gameOverDialog?.findViewById<Button>(R.id.btn_playagain)
        val homeButton = gameOverDialog?.findViewById<Button>(R.id.btn_home)

        val username = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions).email?.substringBefore("@gmail.com") ?: userId.replace("_", ".")
        titleTextView?.text = when (result) {
            "win" -> "$username WON!"
            "loss" -> "$username LOST!"
            "draw" -> "$username DRAW!"
            else -> "$username"
        }
        trophyTextView?.text = when {
            trophyChange > 0 -> "+$trophyChange 🏆"
            trophyChange < 0 -> "$trophyChange 🏆"
            else -> "0 🏆"
        }

        playAgainButton?.setOnClickListener {
            gameOverDialog?.dismiss()
            showOnlineChallengeDialog()
            Log.d("BattleFragment", "Play Again clicked, showing online challenge dialog")
        }

        homeButton?.setOnClickListener {
            gameOverDialog?.dismiss()
            navigateToHomeFragment()
            Log.d("BattleFragment", "Home clicked, navigating to HomeFragment")
        }

        gameOverDialog?.show()
        Log.d("BattleFragment", "Showing game-over dialog: result=$result, trophyChange=$trophyChange")
    }

    private fun updateTrophies(change: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val currentTrophies = database.child("users").child(userId).child("trophies").get().await().getValue(Int::class.java) ?: 0
                saveDataToFirebase("trophies", (currentTrophies + change).coerceAtLeast(0))
            } catch (e: Exception) {
                Log.e("BattleFragment", "Failed to update trophies: ${e.message}")
            }
        }
    }

    private fun updateUIForBattleStatus(isInBattle: Boolean) {
        if (isInBattle) {
            mainContainer.visibility = View.VISIBLE
            onlineChallengeDialog?.dismiss()
            waitingDialog?.dismiss()
            gameOverDialog?.dismiss()
            Log.d("BattleFragment", "User is in battle, showing main UI and hiding dialogs")
        } else {
            mainContainer.visibility = View.GONE
            waitingDialog?.dismiss()
            if (gameOverDialog?.isShowing != true) {
                showOnlineChallengeDialog()
            }
            Log.d("BattleFragment", "User is not in battle, gameOverDialog is ${if (gameOverDialog?.isShowing == true) "showing" else "not showing"}")
        }
    }

    private suspend fun isInBattle(): Boolean {
        return try {
            val snapshot = database.child("users").child(userId).child("in_battle").get().await()
            snapshot.getValue(Boolean::class.java) ?: false
        } catch (e: Exception) {
            Log.e("BattleFragment", "Error checking battle state: ${e.message}")
            false
        }
    }

    private fun navigateToHomeFragment() {
        if (!isAdded) return
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment().apply {
                arguments = Bundle().apply { putString("userId", userId) }
            })
            .addToBackStack(null)
            .commit()
        (requireActivity() as? MainActivity)?.updateIndicators(true, false, false)
    }

    private fun showOnlineChallengeDialog() {
        if (!isAdded) return
        onlineChallengeDialog = Dialog(requireContext()).apply {
            setContentView(R.layout.layout_battle)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
        }

        val onlineUsersContainer = onlineChallengeDialog?.findViewById<LinearLayout>(R.id.onlineUsersContainer)
        val noUsersText = onlineChallengeDialog?.findViewById<TextView>(R.id.noUsersText)
        val searchUsers = onlineChallengeDialog?.findViewById<EditText>(R.id.searchUsers)
        val cancelButton = onlineChallengeDialog?.findViewById<Button>(R.id.btn_cancel)

        cancelButton?.setOnClickListener {
            onlineChallengeDialog?.dismiss()
            navigateToHomeFragment()
        }

        onlineUsersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                onlineUsersContainer?.removeAllViews()
                var hasOnlineUsers = false

                for (userSnapshot in snapshot.children) {
                    val userEmail = userSnapshot.key?.replace("_", ".") ?: continue
                    if (userEmail == userId.replace("_", ".")) continue
                    val isOnline = userSnapshot.child("is_online").getValue(Boolean::class.java) ?: false
                    val inBattle = userSnapshot.child("in_battle").getValue(Boolean::class.java) ?: false
                    if (isOnline && !inBattle) {
                        val username = userEmail.substringBefore("@gmail.com")
                        if (currentSearchQuery.isEmpty() || username.contains(currentSearchQuery, ignoreCase = true)) {
                            hasOnlineUsers = true
                            addUserToDialog(userEmail, onlineUsersContainer)
                        }
                    }
                }

                if (hasOnlineUsers) {
                    noUsersText?.visibility = View.GONE
                    onlineUsersContainer?.visibility = View.VISIBLE
                } else {
                    noUsersText?.visibility = View.VISIBLE
                    onlineUsersContainer?.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("BattleFragment", "Failed to load online users: ${error.message}")
                noUsersText?.visibility = View.VISIBLE
                onlineUsersContainer?.visibility = View.GONE
            }
        }

        searchUsers?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s.toString().trim()
                database.child("users").get().addOnSuccessListener { snapshot ->
                    onlineUsersListener?.onDataChange(snapshot)
                }
            }
        })

        database.child("users").addValueEventListener(onlineUsersListener!!)
        onlineChallengeDialog?.show()
    }

    private fun addUserToDialog(userEmail: String, container: LinearLayout?) {
        val userView = layoutInflater.inflate(R.layout.challenge_user_item, container, false)
        val userTextView = userView.findViewById<TextView>(R.id.users)
        val duelButton = userView.findViewById<Button>(R.id.btn_duel)

        val username = userEmail.substringBefore("@gmail.com")
        userTextView.text = username
        duelButton.setOnClickListener {
            mainContainer.visibility = View.GONE
            onlineChallengeDialog?.dismiss()
            sendChallenge(userEmail.replace(".", "_"))
            showWaitingDialog(userEmail.replace(".", "_"))
        }

        container?.addView(userView)
    }

    private fun showWaitingDialog(targetUserId: String) {
        if (!isAdded) return
        waitingDialog = Dialog(requireContext()).apply {
            setContentView(R.layout.layout_waiting)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
        }

        val cancelButton = waitingDialog?.findViewById<Button>(R.id.btn_cancel)
        cancelButton?.setOnClickListener {
            cancelChallenge(targetUserId)
            waitingDialog?.dismiss()
            navigateToHomeFragment()
        }

        waitingDialog?.show()
    }

    private fun sendChallenge(targetUserId: String) {
        coroutineScope.launch {
            if (!isInBattle()) {
                Log.d("BattleFragment", "Sending challenge to $targetUserId")
                saveDataToFirebase("challenged", true, "challenges/$targetUserId")
                saveDataToFirebase("challengerId", userId, "challenges/$targetUserId")
            } else {
                Log.d("BattleFragment", "Cannot send challenge: Already in battle")
            }
        }
    }

    private fun cancelChallenge(targetUserId: String) {
        coroutineScope.launch {
            Log.d("BattleFragment", "Canceling challenge to $targetUserId")
            saveDataToFirebase("challenged", false, "challenges/$targetUserId")
            saveDataToFirebase("challengerId", null, "challenges/$targetUserId")
        }
    }

    private fun fetchDataSources() {
        if (!isAdded) return
        val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
        Fitness.getSensorsClient(requireActivity(), account)
            .findDataSources(
                DataSourcesRequest.Builder()
                    .setDataTypes(DataType.TYPE_MOVE_MINUTES, DataType.TYPE_HEART_RATE_BPM)
                    .build()
            )
            .addOnSuccessListener { dataSources ->
                val sourceInfo = dataSources.map { source ->
                    "${source.appPackageName ?: "Unknown"} (Device: ${source.device?.toString() ?: "Unknown"}, Type: ${source.dataType.name})"
                }
                Log.d("BattleFragment", "Data sources: $sourceInfo")
            }
            .addOnFailureListener { exception ->
                Log.e("BattleFragment", "Failed to fetch data sources: ${exception.message}")
            }
    }

    private fun fetchFitnessData() {
        if (!isAdded || battleStartTime == 0L) return
        val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
        val endTime = System.currentTimeMillis()
        val startTimeToday = battleStartTime - TimeUnit.DAYS.toMillis(1)
        val startTimeBattle = battleStartTime

        Log.d("BattleFragment", "Fetching fitness data: Google account=${account.email ?: "No email"}, Battle start time=$battleStartTime")

        val sleepRequest = DataReadRequest.Builder()
            .read(DataType.TYPE_SLEEP_SEGMENT)
            .setTimeRange(startTimeToday, battleStartTime, TimeUnit.MILLISECONDS)
            .build()

        Fitness.getHistoryClient(requireActivity(), account)
            .readData(sleepRequest)
            .addOnSuccessListener { response ->
                val sleepMinutes = response.dataSets.firstOrNull()?.dataPoints?.sumOf {
                    val start = it.getStartTime(TimeUnit.MINUTES)
                    val end = it.getEndTime(TimeUnit.MINUTES)
                    (end - start).toInt()
                } ?: 0
                val hours = sleepMinutes / 60
                val minutes = sleepMinutes % 60
                sleepText.text = "${hours}h ${minutes}m"
                val maxSleepMinutes = 480
                val sleepProgressValue = if (sleepMinutes > maxSleepMinutes) 100 else (sleepMinutes.toFloat() / maxSleepMinutes * 100).toInt()
                sleepProgress.progress = sleepProgressValue
                val baseHp = 50
                val hpIncrease = (hours * 6.25).toInt().coerceAtMost(50)
                val hpValue = (baseHp + hpIncrease).coerceAtMost(100)
                playerHpBar.progress = hpValue
                playerHpText.text = "$hpValue/100"
                saveDataToFirebase("battle_sleep_hours", "${hours}h ${minutes}m")
                saveDataToFirebase("battle_sleep_progress", sleepProgressValue)
                saveDataToFirebase("hp", hpValue)
                Log.d("BattleFragment", "Sleep minutes: $sleepMinutes, Sleep Progress: $sleepProgressValue, HP: $hpValue")
            }
            .addOnFailureListener { exception ->
                Log.e("BattleFragment", "Failed to fetch sleep: ${exception.message}")
                playerHpBar.progress = 50
                playerHpText.text = "50/100"
                saveDataToFirebase("hp", 50)
                saveDataToFirebase("battle_sleep_hours", "0h 0m")
                saveDataToFirebase("battle_sleep_progress", 0)
            }

        val moveMinutesRequest = DataReadRequest.Builder()
            .aggregate(DataType.TYPE_MOVE_MINUTES)
            .bucketByTime(1, TimeUnit.DAYS)
            .setTimeRange(startTimeBattle, endTime, TimeUnit.MILLISECONDS)
            .build()

        Fitness.getHistoryClient(requireActivity(), account)
            .readData(moveMinutesRequest)
            .addOnSuccessListener { response ->
                val moveMinutes = response.buckets.firstOrNull()?.getDataSet(DataType.TYPE_MOVE_MINUTES)
                    ?.dataPoints?.sumOf { it.getValue(Field.FIELD_DURATION).asInt().toDouble() } ?: 0.0
                exerciseText.text = "${moveMinutes.toInt()}"
                val maxMoveMinutes = 60.0
                val manaValue = (moveMinutes / maxMoveMinutes * 100).toInt().coerceAtMost(100)
                playerManaBar.progress = manaValue
                playerManaText.text = "$manaValue/100"
                saveDataToFirebase("exercise_minutes", moveMinutes.toInt())
                saveDataToFirebase("mana", manaValue)
                Log.d("BattleFragment", "Move minutes: $moveMinutes, Mana: $manaValue")
            }
            .addOnFailureListener { exception ->
                Log.e("BattleFragment", "Failed to fetch move minutes: ${exception.message}")
                playerManaBar.progress = 0
                playerManaText.text = "0/100"
                saveDataToFirebase("mana", 0)
            }

        val heartRecentRequest = DataReadRequest.Builder()
            .read(DataType.TYPE_HEART_RATE_BPM)
            .setTimeRange(startTimeBattle, endTime, TimeUnit.MILLISECONDS)
            .build()

        Fitness.getHistoryClient(requireActivity(), account)
            .readData(heartRecentRequest)
            .addOnSuccessListener { response ->
                val bpm = response.dataSets.firstOrNull()?.dataPoints?.lastOrNull()
                    ?.getValue(com.google.android.gms.fitness.data.Field.FIELD_BPM)?.asFloat() ?: 0f
                if (bpm > 0f) {
                    lastBpm = bpm
                    heartRateText.text = "${bpm.toInt()}"
                    val baseShield = 25
                    val shieldIncrease = ((bpm - 60) / 2).toInt().coerceAtLeast(0)
                    val shieldValue = (baseShield + shieldIncrease).coerceAtMost(50)
                    playerShieldBar.progress = shieldValue
                    playerShieldText.text = "$shieldValue/50"
                    saveDataToFirebase("heart_rate", bpm.toInt())
                    saveDataToFirebase("shield", shieldValue)
                } else {
                    heartRateText.text = "${lastBpm.toInt()}"
                    playerShieldBar.progress = 25
                    playerShieldText.text = "25/50"
                    saveDataToFirebase("shield", 25)
                }
                Log.d("BattleFragment", "Heart rate: $bpm, Shield: ${playerShieldBar.progress}")
            }
            .addOnFailureListener { exception ->
                Log.e("BattleFragment", "Failed to fetch heart rate: ${exception.message}")
                heartRateText.text = "${lastBpm.toInt()}"
                playerShieldBar.progress = 25
                playerShieldText.text = "25/50"
                saveDataToFirebase("shield", 25)
            }
    }

    private fun startRealTimeHeartRate() {
        if (!isAdded || battleStartTime == 0L) return
        val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
        Fitness.getRecordingClient(requireActivity(), account)
            .subscribe(DataType.TYPE_HEART_RATE_BPM)
            .addOnSuccessListener {
                Log.d("BattleFragment", "Subscribed to real-time heart rate")
            }
            .addOnFailureListener { exception ->
                Log.e("BattleFragment", "Failed to subscribe to heart rate: ${exception.message}")
            }

        Fitness.getSensorsClient(requireActivity(), account)
            .add(
                SensorRequest.Builder()
                    .setDataType(DataType.TYPE_HEART_RATE_BPM)
                    .setSamplingRate(10, TimeUnit.SECONDS)
                    .build()
            ) { dataPoint ->
                val bpm = dataPoint.getValue(com.google.android.gms.fitness.data.Field.FIELD_BPM).asFloat()
                if (bpm > 0f) {
                    lastBpm = bpm
                    heartRateText.text = "${bpm.toInt()}"
                    val baseShield = 25
                    val shieldIncrease = ((bpm - 60) / 2).toInt().coerceAtLeast(0)
                    val shieldValue = (baseShield + shieldIncrease).coerceAtMost(50)
                    playerShieldBar.progress = shieldValue
                    playerShieldText.text = "$shieldValue/50"
                    saveDataToFirebase("heart_rate", bpm.toInt())
                    saveDataToFirebase("shield", shieldValue)
                }
                Log.d("BattleFragment", "Real-time BPM: $bpm")
            }
            .addOnFailureListener { exception ->
                Log.e("BattleFragment", "Failed to add sensor listener: ${exception.message}")
            }
    }

    private fun stopRealTimeHeartRate() {
        if (!isAdded) return
        val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
        Fitness.getSensorsClient(requireActivity(), account)
            .remove { dataPoint ->
                Log.d("BattleFragment", "Removed sensor listener for BPM")
                true
            }
            .addOnSuccessListener {
                Log.d("BattleFragment", "Successfully unsubscribed from heart rate updates")
            }
            .addOnFailureListener { exception ->
                Log.e("BattleFragment", "Failed to unsubscribe from heart rate: ${exception.message}")
            }
    }

    private fun startPeriodicDataFetch() {
        coroutineScope.launch {
            while (isActive) {
                fetchFitnessData()
                val opponentId = database.child("users").child(userId).child("battle_with").get().await()
                    .getValue(String::class.java)
                if (opponentId != null) {
                }
                delay(5000)
            }
        }
    }

    private fun saveDataToFirebase(key: String, value: Any?, path: String = "users/$userId") {
        if (!isAdded) return
        Log.d("BattleFragment", "Attempting to save: $key = $value for userId: $userId at path: $path")
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val userRef = database.child(path).child(key)
                userRef.setValue(value).addOnSuccessListener {
                    Log.d("BattleFragment", "Successfully saved to Firebase: $key = $value")
                }.addOnFailureListener { exception ->
                    Log.e("BattleFragment", "Failed to save data to Firebase: ${exception.message}", exception)
                    Toast.makeText(requireContext(), "Failed to save $key to Firebase", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("BattleFragment", "Exception in saveDataToFirebase: ${e.message}", e)
                Toast.makeText(requireContext(), "Error saving $key to Firebase", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadDataFromFirebase() {
        if (!isAdded) return
        Log.d("BattleFragment", "Attempting to load data for userId: $userId")
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val userRef = database.child("users").child(userId)
                userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            coroutineScope.launch(Dispatchers.Main) {
                                exerciseText.text = "${snapshot.child("exercise_minutes").getValue(Int::class.java) ?: 0} min"
                                heartRateText.text = "${snapshot.child("heart_rate").getValue(Int::class.java) ?: 0}"
                                sleepText.text = snapshot.child("battle_sleep_hours").getValue(String::class.java) ?: "0h 0m"
                                sleepProgress.progress = snapshot.child("battle_sleep_progress").getValue(Int::class.java) ?: 0
                                val hp = snapshot.child("hp").getValue(Int::class.java) ?: 50
                                val shield = snapshot.child("shield").getValue(Int::class.java) ?: 25
                                val mana = snapshot.child("mana").getValue(Int::class.java) ?: 0
                                playerLevel = snapshot.child("level").getValue(Int::class.java) ?: 1
                                if (playerLevel > 3) playerLevel = 3
                                playerHpBar.progress = hp
                                playerHpText.text = "$hp/100"
                                playerShieldBar.progress = shield
                                playerShieldText.text = "$shield/50"
                                playerManaBar.progress = mana
                                playerManaText.text = "$mana/100"
                                updateSkillIcons()
                                Log.d("BattleFragment", "Data loaded from Firebase: Online: ${snapshot.child("is_online").getValue(Boolean::class.java)}, Level: $playerLevel, HP=$hp, Shield=$shield, Mana=$mana")
                            }
                        } else {
                            Log.w("BattleFragment", "No data found for user_id: $userId, setting defaults")
                            coroutineScope.launch(Dispatchers.Main) {
                                playerLevel = 1
                                playerHpBar.progress = 50
                                playerHpText.text = "50/100"
                                playerShieldBar.progress = 25
                                playerShieldText.text = "25/50"
                                playerManaBar.progress = 0
                                playerManaText.text = "0/100"
                                saveDataToFirebase("level", 1)
                                saveDataToFirebase("hp", 50)
                                saveDataToFirebase("shield", 25)
                                saveDataToFirebase("mana", 0)
                                saveDataToFirebase("battle_sleep_hours", "0h 0m")
                                saveDataToFirebase("battle_sleep_progress", 0)
                                updateSkillIcons()
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("BattleFragment", "Firebase data load cancelled: ${error.message}")
                        coroutineScope.launch(Dispatchers.Main) {
                            playerLevel = 1
                            playerHpBar.progress = 50
                            playerHpText.text = "50/100"
                            playerShieldBar.progress = 25
                            playerShieldText.text = "25/50"
                            playerManaBar.progress = 0
                            playerManaText.text = "0/100"
                            saveDataToFirebase("level", 1)
                            saveDataToFirebase("hp", 50)
                            saveDataToFirebase("shield", 25)
                            saveDataToFirebase("mana", 0)
                            saveDataToFirebase("battle_sleep_hours", "0h 0m")
                            saveDataToFirebase("battle_sleep_progress", 0)
                            updateSkillIcons()
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e("BattleFragment", "Failed to load data from Firebase: ${e.message}", e)
                coroutineScope.launch(Dispatchers.Main) {
                    playerLevel = 1
                    playerHpBar.progress = 50
                    playerHpText.text = "50/100"
                    playerShieldBar.progress = 25
                    playerShieldText.text = "25/50"
                    playerManaBar.progress = 0
                    playerManaText.text = "0/100"
                    saveDataToFirebase("level", 1)
                    saveDataToFirebase("hp", 50)
                    saveDataToFirebase("shield", 25)
                    saveDataToFirebase("mana", 0)
                    saveDataToFirebase("battle_sleep_hours", "0h 0m")
                    saveDataToFirebase("battle_sleep_progress", 0)
                    updateSkillIcons()
                }
            }
        }
    }
}