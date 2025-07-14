//package com.beatquest
//
//import android.app.Dialog
//import android.content.Context
//import android.content.pm.PackageManager
//import android.hardware.Sensor
//import android.hardware.SensorEvent
//import android.hardware.SensorEventListener
//import android.hardware.SensorManager
//import android.os.Bundle
//import android.util.Log
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.Button
//import android.widget.ImageView
//import android.widget.LinearLayout
//import android.widget.ProgressBar
//import android.widget.TextView
//import android.widget.Toast
//import androidx.core.content.ContextCompat
//import androidx.fragment.app.Fragment
//import com.bumptech.glide.Glide
//import com.bumptech.glide.request.RequestOptions
//import com.google.android.gms.auth.api.signin.GoogleSignIn
//import com.google.android.gms.fitness.Fitness
//import com.google.android.gms.fitness.FitnessOptions
//import com.google.android.gms.fitness.data.DataType
//import com.google.android.gms.fitness.data.Field
//import com.google.android.gms.fitness.request.DataReadRequest
//import com.google.android.gms.fitness.request.DataSourcesRequest
//import com.google.android.gms.fitness.request.SensorRequest
//import com.google.firebase.database.DataSnapshot
//import com.google.firebase.database.DatabaseError
//import com.google.firebase.database.FirebaseDatabase
//import com.google.firebase.database.ValueEventListener
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.cancel
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.isActive
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.tasks.await
//import java.text.SimpleDateFormat
//import java.util.Calendar
//import java.util.Locale
//import java.util.concurrent.TimeUnit
//import kotlin.random.Random
//
//data class DailyMission(
//    val description: String,
//    val xpReward: Int,
//    val type: String,
//    val goal: Float,
//    val duration: Long? = null
//)
//
//class HomeFragment : Fragment(), SensorEventListener {
//
//    private lateinit var exerciseText: TextView
//    private lateinit var heartRateText: TextView
//    private lateinit var sleepText: TextView
//    private lateinit var sleepProgress: ProgressBar
//    private lateinit var hpBar: ProgressBar
//    private lateinit var levelText: TextView
//    private lateinit var xpText: TextView
//    private lateinit var dailyMissionText: TextView
//    private lateinit var missionXPText: TextView
//    private lateinit var coroutineScope: CoroutineScope
//    private lateinit var mapImage: ImageView
//    private lateinit var playerHero: ImageView
//    private lateinit var skill1: LinearLayout
//    private lateinit var skill2: LinearLayout
//    private lateinit var skill3: LinearLayout
//    private lateinit var skill1Image: ImageView
//    private lateinit var skill2Image: ImageView
//    private lateinit var skill3Image: ImageView
//    private var waitingDialog: Dialog? = null
//    private var lastBpm: Float = 0f
//    private var playerLevel: Int = 1
//    private var playerXP: Long = 0
//    private var currentSteps: Float = 0f
//    private var initialStepCount: Float? = null
//    private var heartRateStartTime: Long? = null
//    private var currentMission: DailyMission? = null
//    private var sensorManager: SensorManager? = null
//    private var missionCompleted: Boolean = false
//
//    private val userId: String by lazy {
//        val email = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions).email ?: "anonymous"
//        email.replace(".", "_")
//    }
//    private val database by lazy {
//        FirebaseDatabase.getInstance("https://wewe-b3760-default-rtdb.asia-southeast1.firebasedatabase.app").reference
//    }
//
//    private val fitnessOptions: FitnessOptions by lazy {
//        FitnessOptions.builder()
//            .addDataType(DataType.TYPE_MOVE_MINUTES, FitnessOptions.ACCESS_READ)
//            .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
//            .addDataType(DataType.TYPE_SLEEP_SEGMENT, FitnessOptions.ACCESS_READ)
//            .build()
//    }
//
//    private val missions = listOf(
//        DailyMission("Accumulate 25 minutes of movement today", 400, "movement", 25f),
//        DailyMission("Reach a heart rate of 105+ BPM at any point during activity", 200, "heart_rate", 105f),
//        DailyMission("Accumulate 3,000 steps throughout the day", 350, "steps", 3000f),
//        DailyMission("Accumulate 7.5 hours of sleep or more", 150, "sleep", 450f),
//        DailyMission("Reach a heart rate of 100+ BPM for at least 2 minutes", 250, "heart_rate_duration", 100f, 120_000L),
//        DailyMission("Maintain a heart rate of 98+ BPM for 3 minutes straight", 250, "heart_rate_duration", 98f, 180_000L)
//    )
//
//
//    private fun getXpForNextLevel(level: Int): Long {
//        return when (level) {
//            1 -> 1040L
//            2 -> 3150L
//            else -> 9999999L
//        }
//    }
//
//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View? {
//        val view = inflater.inflate(R.layout.fragment_home, container, false)
//
//        try {
//            exerciseText = view.findViewById(R.id.exerciseText)
//            heartRateText = view.findViewById(R.id.heartRateText)
//            sleepText = view.findViewById(R.id.sleepText)
//            sleepProgress = view.findViewById(R.id.sleepProgress)
//            hpBar = view.findViewById(R.id.hp)
//            levelText = view.findViewById(R.id.lvl)
//            xpText = view.findViewById(R.id.plyrXP)
//            dailyMissionText = view.findViewById(R.id.dailyMission)
//            missionXPText = view.findViewById(R.id.missionXP)
//            mapImage = view.findViewById(R.id.mapImage)
//            playerHero = view.findViewById(R.id.playerAvtr)
//            skill1 = view.findViewById(R.id.skill1)
//            skill2 = view.findViewById(R.id.skill2)
//            skill3 = view.findViewById(R.id.skill3)
//            skill1Image = view.findViewById(R.id.skill1_icon)
//            skill2Image = view.findViewById(R.id.skill2_icon)
//            skill3Image = view.findViewById(R.id.skill3_icon)
//            coroutineScope = CoroutineScope(Dispatchers.Main + Job())
//
//            if (skill1Image == null || skill2Image == null || skill3Image == null) {
//                Log.e("HomeFragment", "Skill image views are null. Check fragment_home.xml for skill1_icon, skill2_icon, skill3_icon")
//                Toast.makeText(requireContext(), "Error: Skill icons not found", Toast.LENGTH_LONG).show()
//                return view
//            }
//
//            skill1.setOnClickListener {
//                if (playerLevel >= 1) {
//                    val testUserId = "test_challenger_gmail_com"
//                    Log.d("HomeFragment", "Skill1 clicked, triggering challenge from $testUserId")
//                    sendTestChallenge(testUserId)
//                } else {
//                    Log.d("HomeFragment", "Skill1 is locked (level $playerLevel < 1)")
//                }
//            }
//
//            skill2.setOnClickListener {
//                if (playerLevel >= 2) {
//                    Log.d("HomeFragment", "Skill2 clicked (unlocked)")
//                } else {
//                    Log.d("HomeFragment", "Skill2 is locked (level $playerLevel < 2)")
//                }
//            }
//            skill3.setOnClickListener {
//                if (playerLevel >= 3) {
//                    Log.d("HomeFragment", "Skill3 clicked (unlocked)")
//                } else {
//                    Log.d("HomeFragment", "Skill3 is locked (level $playerLevel < 3)")
//                }
//            }
//
//            Glide.with(this)
//                .load(R.drawable.assasin)
//                .apply(RequestOptions().fitCenter())
//                .into(playerHero)
//
//            Glide.with(this)
//                .load(R.drawable.background_map)
//                .apply(RequestOptions()
//                    .centerCrop()
//                    .transform(com.bumptech.glide.load.resource.bitmap.RoundedCorners(100)))
//                .into(mapImage)
//
//            Log.d("HomeFragment", "User ID: $userId")
//
//            saveDataToFirebase("is_online", true)
//            setupOnDisconnect()
//            fetchDataSources()
//            fetchFitnessData()
//            startRealTimeHeartRate()
//            startPeriodicDataFetch()
//            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACTIVITY_RECOGNITION)
//                == PackageManager.PERMISSION_GRANTED) {
//                setupStepCounter()
//            } else {
//                Log.w("HomeFragment", "ACTIVITY_RECOGNITION permission not granted")
//                Toast.makeText(requireContext(), "Step counter unavailable: Permission denied", Toast.LENGTH_LONG).show()
//            }
//            loadDataFromFirebase()
//        } catch (e: Exception) {
//            Log.e("HomeFragment", "Error in onCreateView: ${e.message}", e)
//            Toast.makeText(requireContext(), "Initialization error", Toast.LENGTH_LONG).show()
//        }
//
//        return view
//    }
//
//    override fun onResume() {
//        super.onResume()
//        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACTIVITY_RECOGNITION)
//            == PackageManager.PERMISSION_GRANTED) {
//            sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.also { sensor ->
//                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
//                Log.d("HomeFragment", "Step counter sensor registered")
//            }
//        }
//    }
//
//    override fun onPause() {
//        super.onPause()
//        sensorManager?.unregisterListener(this)
//        saveDataToFirebase("steps", currentSteps)
//        Log.d("HomeFragment", "Step counter sensor unregistered, saved steps: $currentSteps")
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        saveDataToFirebase("is_online", false)
//        saveDataToFirebase("steps", currentSteps)
//        coroutineScope.cancel()
//        stopRealTimeHeartRate()
//        waitingDialog?.dismiss()
//    }
//
//    override fun onSensorChanged(event: SensorEvent?) {
//        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER && !missionCompleted) {
//            val totalSteps = event.values[0]
//            if (initialStepCount == null) {
//                initialStepCount = totalSteps
//            }
//            currentSteps = totalSteps - (initialStepCount ?: totalSteps)
//            Log.d("HomeFragment", "Steps detected: $currentSteps")
//            saveDataToFirebase("steps", currentSteps)
//            checkMissionProgress()
//        }
//    }
//
//    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
//        Log.d("HomeFragment", "Step sensor accuracy changed: $accuracy")
//    }
//
//    private fun setupStepCounter() {
//        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
//        sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.also { sensor ->
//            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
//            Log.d("HomeFragment", "Step counter sensor initialized")
//        } ?: Log.e("HomeFragment", "Step counter sensor not available")
//    }
//
//    private fun updateSkillIcons() {
//        if (!isAdded) return
//        try {
//            skill1Image.setImageResource(if (playerLevel >= 1) R.drawable.skill1 else R.drawable.lock)
//            skill1.isEnabled = playerLevel >= 1
//            skill2Image.setImageResource(if (playerLevel >= 2) R.drawable.skill2 else R.drawable.lock)
//            skill2.isEnabled = playerLevel >= 2
//            skill3Image.setImageResource(if (playerLevel >= 3) R.drawable.skill3 else R.drawable.lock)
//            skill3.isEnabled = playerLevel >= 3
//            Log.d("HomeFragment", "Updated skill icons: level=$playerLevel")
//        } catch (e: Exception) {
//            Log.e("HomeFragment", "Error updating skill icons: ${e.message}", e)
//        }
//    }
//
//    private fun sendTestChallenge(challengerId: String) {
//        coroutineScope.launch(Dispatchers.IO) {
//            try {
//                Log.d("HomeFragment", "Creating test challenge from $challengerId to $userId")
//                val challengeRef = database.child("challenges").child(userId)
//                val updates = mapOf(
//                    "challenged" to true,
//                    "challengerId" to challengerId
//                )
//                challengeRef.updateChildren(updates)
//                    .addOnSuccessListener {
//                        Log.d("HomeFragment", "Successfully set challenge: $updates")
//                    }
//                    .addOnFailureListener { exception ->
//                        Log.e("HomeFragment", "Failed to set challenge: ${exception.message}", exception)
//                    }
//            } catch (e: Exception) {
//                Log.e("HomeFragment", "Exception in sendTestChallenge: ${e.message}", e)
//            }
//        }
//    }
//
//    private fun cancelChallenge(targetUserId: String) {
//        coroutineScope.launch(Dispatchers.IO) {
//            try {
//                Log.d("HomeFragment", "Canceling challenge to $targetUserId")
//                val challengeRef = database.child("challenges").child(userId)
//                val updates = mapOf(
//                    "challenged" to false,
//                    "challengerId" to null
//                )
//                challengeRef.updateChildren(updates)
//                    .addOnSuccessListener {
//                        Log.d("HomeFragment", "Successfully cleared challenge")
//                    }
//                    .addOnFailureListener { exception ->
//                        Log.e("HomeFragment", "Failed to clear challenge: ${exception.message}")
//                    }
//            } catch (e: Exception) {
//                Log.e("HomeFragment", "Exception in cancelChallenge: ${e.message}", e)
//            }
//        }
//    }
//
//    private fun setupOnDisconnect() {
//        if (!isAdded) return
//        try {
//            val userRef = database.child("users").child(userId).child("is_online")
//            userRef.onDisconnect().setValue(false).addOnCompleteListener { task ->
//                if (task.isSuccessful) {
//                    Log.d("HomeFragment", "onDisconnect set to false for userId: $userId")
//                } else {
//                    Log.e("HomeFragment", "Failed to set onDisconnect: ${task.exception?.message}")
//                }
//            }
//        } catch (e: Exception) {
//            Log.e("HomeFragment", "Error in setupOnDisconnect: ${e.message}", e)
//        }
//    }
//
//    private fun fetchDataSources() {
//        if (!isAdded) return
//        try {
//            val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
//            Fitness.getSensorsClient(requireActivity(), account)
//                .findDataSources(
//                    DataSourcesRequest.Builder()
//                        .setDataTypes(DataType.TYPE_MOVE_MINUTES, DataType.TYPE_HEART_RATE_BPM)
//                        .build()
//                )
//                .addOnSuccessListener { dataSources ->
//                    val sourceInfo = dataSources.map { source ->
//                        "${source.appPackageName ?: "Unknown"} (Device: ${source.device?.toString() ?: "Unknown"}, Type: ${source.dataType.name})"
//                    }
//                    Log.d("HomeFragment", "Data sources: $sourceInfo")
//                }
//                .addOnFailureListener { exception ->
//                    Log.e("HomeFragment", "Failed to fetch data sources: ${exception.message}")
//                }
//        } catch (e: Exception) {
//            Log.e("HomeFragment", "Error in fetchDataSources: ${e.message}", e)
//        }
//    }
//
//    private fun fetchFitnessData() {
//        if (!isAdded) return
//        try {
//            val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
//            val endTime = System.currentTimeMillis()
//            val calendar = Calendar.getInstance()
//            calendar.set(Calendar.HOUR_OF_DAY, 0)
//            calendar.set(Calendar.MINUTE, 0)
//            calendar.set(Calendar.SECOND, 0)
//            calendar.set(Calendar.MILLISECOND, 0)
//            val startTimeToday = calendar.timeInMillis
//            calendar.add(Calendar.DAY_OF_MONTH, -30)
//            val startTimeMonth = calendar.timeInMillis
//            val startTimeRecent = endTime - TimeUnit.MINUTES.toMillis(10)
//
//            Log.d("HomeFragment", "Google account: ${account.email ?: "No email"}")
//
//            val moveMinutesRequest = DataReadRequest.Builder()
//                .aggregate(DataType.TYPE_MOVE_MINUTES)
//                .bucketByTime(1, TimeUnit.DAYS)
//                .setTimeRange(startTimeToday, endTime, TimeUnit.MILLISECONDS)
//                .build()
//
//            Fitness.getHistoryClient(requireActivity(), account)
//                .readData(moveMinutesRequest)
//                .addOnSuccessListener { response ->
//                    val moveMinutes = response.buckets.firstOrNull()?.getDataSet(DataType.TYPE_MOVE_MINUTES)
//                        ?.dataPoints?.sumOf { it.getValue(Field.FIELD_DURATION).asInt().toDouble() } ?: 0.0
//                    exerciseText.text = "${moveMinutes.toInt()}"
//                    saveDataToFirebase("exercise_minutes", moveMinutes.toInt())
//                    Log.d("HomeFragment", "Move minutes (today): $moveMinutes")
//                    checkMissionProgress()
//                }
//                .addOnFailureListener { exception ->
//                    Log.e("HomeFragment", "Failed to fetch move minutes: ${exception.message}")
//                }
//
//            val heartRecentRequest = DataReadRequest.Builder()
//                .read(DataType.TYPE_HEART_RATE_BPM)
//                .setTimeRange(startTimeRecent, endTime, TimeUnit.MILLISECONDS)
//                .build()
//
//            Fitness.getHistoryClient(requireActivity(), account)
//                .readData(heartRecentRequest)
//                .addOnSuccessListener { response ->
//                    val bpm = response.dataSets.firstOrNull()?.dataPoints?.lastOrNull()
//                        ?.getValue(Field.FIELD_BPM)?.asFloat() ?: 0f
//                    if (bpm > 0f) {
//                        lastBpm = bpm
//                        heartRateText.text = "${bpm.toInt()}"
//                        saveDataToFirebase("heart_rate", bpm.toInt())
//                        checkHeartRateMission(bpm)
//                    } else {
//                        heartRateText.text = "${lastBpm.toInt()}"
//                        fetchRawHeartRate(startTimeMonth, endTime)
//                    }
//                }
//                .addOnFailureListener { exception ->
//                    Log.e("HomeFragment", "Failed to fetch recent heart rate: ${exception.message}")
//                    heartRateText.text = "${lastBpm.toInt()}"
//                    fetchRawHeartRate(startTimeMonth, endTime)
//                }
//
//            val sleepRequest = DataReadRequest.Builder()
//                .read(DataType.TYPE_SLEEP_SEGMENT)
//                .setTimeRange(startTimeMonth, endTime, TimeUnit.MILLISECONDS)
//                .build()
//
//            Fitness.getHistoryClient(requireActivity(), account)
//                .readData(sleepRequest)
//                .addOnSuccessListener { response ->
//                    val sleepMinutes = response.dataSets.firstOrNull()?.dataPoints?.sumOf {
//                        val start = it.getStartTime(TimeUnit.MINUTES)
//                        val end = it.getEndTime(TimeUnit.MINUTES)
//                        (end - start).toInt()
//                    } ?: 0
//                    val uninterruptedSleep = response.dataSets.firstOrNull()?.dataPoints
//                        ?.maxByOrNull { it.getEndTime(TimeUnit.MINUTES) - it.getStartTime(TimeUnit.MINUTES) }
//                        ?.let { it.getEndTime(TimeUnit.MINUTES) - it.getStartTime(TimeUnit.MINUTES) }?.toInt() ?: 0
//                    val hours = sleepMinutes / 60
//                    val minutes = sleepMinutes % 60
//                    sleepText.text = "${hours}h ${minutes}m"
//                    val maxSleepMinutes = 480
//                    sleepProgress.progress = if (sleepMinutes > maxSleepMinutes) 100 else (sleepMinutes.toFloat() / maxSleepMinutes * 100).toInt()
//                    val baseHp = 50
//                    val hpIncrease = (hours * 5).coerceAtMost(50)
//                    hpBar.progress = (baseHp + hpIncrease).coerceAtMost(100)
//                    saveDataToFirebase("sleep_hours", "${hours}h ${minutes}m")
//                    saveDataToFirebase("sleep_progress", sleepProgress.progress)
//                    saveDataToFirebase("hp", hpBar.progress)
//                    saveDataToFirebase("sleep_minutes", sleepMinutes)
//                    saveDataToFirebase("uninterrupted_sleep", uninterruptedSleep)
//                    Log.d("HomeFragment", "Sleep minutes: $sleepMinutes, Uninterrupted: $uninterruptedSleep, HP: ${hpBar.progress}")
//                    checkMissionProgress()
//                }
//                .addOnFailureListener { exception ->
//                    Log.e("HomeFragment", "Failed to fetch sleep: ${exception.message}")
//                }
//        } catch (e: Exception) {
//            Log.e("HomeFragment", "Error in fetchFitnessData: ${e.message}", e)
//        }
//    }
//
//    private fun startRealTimeHeartRate() {
//        if (!isAdded) return
//        try {
//            val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
//
//            Fitness.getRecordingClient(requireActivity(), account)
//                .subscribe(DataType.TYPE_HEART_RATE_BPM)
//                .addOnSuccessListener {
//                    Log.d("HomeFragment", "Subscribed to real-time heart rate")
//                }
//                .addOnFailureListener { exception ->
//                    Log.e("HomeFragment", "Failed to subscribe to heart rate: ${exception.message}")
//                }
//
//            Fitness.getSensorsClient(requireActivity(), account)
//                .add(
//                    SensorRequest.Builder()
//                        .setDataType(DataType.TYPE_HEART_RATE_BPM)
//                        .setSamplingRate(10, TimeUnit.SECONDS)
//                        .build()
//                ) { dataPoint ->
//                    val bpm = dataPoint.getValue(Field.FIELD_BPM).asFloat()
//                    if (bpm > 0f) {
//                        lastBpm = bpm
//                        heartRateText.text = "${bpm.toInt()}"
//                        saveDataToFirebase("heart_rate", bpm.toInt())
//                        checkHeartRateMission(bpm)
//                    }
//                    Log.d("HomeFragment", "Real-time BPM: $bpm")
//                }
//                .addOnFailureListener { exception ->
//                    Log.e("HomeFragment", "Failed to add sensor listener: ${exception.message}")
//                }
//        } catch (e: Exception) {
//            Log.e("HomeFragment", "Error in startRealTimeHeartRate: ${e.message}", e)
//        }
//    }
//
//    private fun checkHeartRateMission(bpm: Float) {
//        if (!isAdded || missionCompleted || currentMission == null) return
//        try {
//            val mission = currentMission!!
//            if (mission.type == "heart_rate" && bpm >= mission.goal) {
//                completeMission(mission)
//            } else if (mission.type == "heart_rate_duration" && bpm >= mission.goal) {
//                if (heartRateStartTime == null) {
//                    heartRateStartTime = System.currentTimeMillis()
//                    Log.d("HomeFragment", "Heart rate duration started: BPM=$bpm")
//                }
//                val elapsed = System.currentTimeMillis() - (heartRateStartTime ?: 0)
//                if (elapsed >= mission.duration!!) {
//                    completeMission(mission)
//                }
//            } else if (mission.type == "heart_rate_duration" && bpm < mission.goal) {
//                heartRateStartTime = null
//                Log.d("HomeFragment", "Heart rate duration reset: BPM=$bpm below goal")
//            }
//        } catch (e: Exception) {
//            Log.e("HomeFragment", "Error in checkHeartRateMission: ${e.message}", e)
//        }
//    }
//
//    private fun completeMission(mission: DailyMission) {
//        if (!isAdded || missionCompleted) return
//        missionCompleted = true
//        playerXP += mission.xpReward
//        val xpForNextLevel = getXpForNextLevel(playerLevel)
//        if (playerLevel < 3 && playerXP >= xpForNextLevel) {
//            playerLevel += 1
//            playerXP -= xpForNextLevel
//            saveDataToFirebase("level", playerLevel)
//            updateSkillIcons()
//        }
//        coroutineScope.launch(Dispatchers.IO) {
//            try {
//
//                val xpTask = database.child("users").child(userId).child("xp").setValue(playerXP)
//                xpTask.addOnSuccessListener {
//                    Log.d("HomeFragment", "Successfully saved XP: $playerXP")
//                    coroutineScope.launch(Dispatchers.Main) {
//                        xpText.text = "XP $playerXP/${getXpForNextLevel(playerLevel)}"
//                        levelText.text = playerLevel.toString()
//                        Toast.makeText(requireContext(), "Mission Completed: ${mission.description}! +${mission.xpReward} XP", Toast.LENGTH_LONG).show()
//                        Log.d("HomeFragment", "Mission completed: ${mission.description}, XP: $playerXP, Level: $playerLevel")
//                    }
//                }.addOnFailureListener { exception ->
//                    Log.e("HomeFragment", "Failed to save XP: ${exception.message}", exception)
//                    coroutineScope.launch(Dispatchers.Main) {
//                        Toast.makeText(requireContext(), "Failed to save XP", Toast.LENGTH_LONG).show()
//                    }
//                }
//                xpTask.await()
//
//                database.child("users").child(userId).child("daily_mission").removeValue()
//                coroutineScope.launch(Dispatchers.Main) {
//                    currentMission = null
//                    missionCompleted = false
//                    loadDailyMission()
//                }
//            } catch (e: Exception) {
//                Log.e("HomeFragment", "Error in completeMission: ${e.message}", e)
//                coroutineScope.launch(Dispatchers.Main) {
//                    Toast.makeText(requireContext(), "Error completing mission", Toast.LENGTH_LONG).show()
//                }
//            }
//        }
//    }
//
//    private fun checkMissionProgress() {
//        if (!isAdded || missionCompleted || currentMission == null) return
//        coroutineScope.launch(Dispatchers.IO) {
//            try {
//                val snapshot = database.child("users").child(userId).get().await()
//                val mission = currentMission!!
//                when (mission.type) {
//                    "steps" -> {
//                        val steps = snapshot.child("steps").getValue(Float::class.java) ?: currentSteps
//                        if (steps >= mission.goal) {
//                            coroutineScope.launch(Dispatchers.Main) { completeMission(mission) }
//                        } else if (steps >= 100) {
//                            coroutineScope.launch(Dispatchers.Main) {
//                                val currentXpForNextLevel = getXpForNextLevel(playerLevel)
//                                playerXP += 10
//                                if (playerLevel < 3 && playerXP >= currentXpForNextLevel) {
//                                    playerLevel += 1
//                                    playerXP -= currentXpForNextLevel
//                                    saveDataToFirebase("level", playerLevel)
//                                    updateSkillIcons()
//                                }
//                                saveDataToFirebase("xp", playerXP)
//                                xpText.text = "XP $playerXP/$currentXpForNextLevel"
//                                levelText.text = playerLevel.toString()
//                                Toast.makeText(requireContext(), "Test: 100 steps reached! +10 XP", Toast.LENGTH_SHORT).show()
//                            }
//                        }
//                    }
//                    "movement" -> {
//                        val moveMinutes = snapshot.child("exercise_minutes").getValue(Int::class.java) ?: 0
//                        if (moveMinutes >= mission.goal) {
//                            coroutineScope.launch(Dispatchers.Main) { completeMission(mission) }
//                        }
//                    }
//                    "sleep" -> {
//                        val sleepMinutes = snapshot.child("sleep_minutes").getValue(Int::class.java) ?: 0
//                        if (sleepMinutes >= mission.goal) {
//                            coroutineScope.launch(Dispatchers.Main) { completeMission(mission) }
//                        }
//                    }
//                    "sleep_uninterrupted" -> {
//                        val uninterruptedSleep = snapshot.child("uninterrupted_sleep").getValue(Int::class.java) ?: 0
//                        if (uninterruptedSleep >= mission.goal) {
//                            coroutineScope.launch(Dispatchers.Main) { completeMission(mission) }
//                        }
//                    }
//                    "heart_rate" -> {
//                        val heartRate = snapshot.child("heart_rate").getValue(Int::class.java) ?: 0
//                        if (heartRate >= mission.goal) {
//                            coroutineScope.launch(Dispatchers.Main) { completeMission(mission) }
//                        }
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e("HomeFragment", "Failed to check mission progress: ${e.message}")
//            }
//        }
//    }
//
//    private fun stopRealTimeHeartRate() {
//        if (!isAdded) return
//        try {
//            val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
//            Fitness.getSensorsClient(requireActivity(), account)
//                .remove { dataPoint ->
//                    Log.d("HomeFragment", "Removed sensor listener for BPM")
//                    true
//                }
//                .addOnSuccessListener {
//                    Log.d("HomeFragment", "Successfully unsubscribed from heart rate updates")
//                }
//                .addOnFailureListener { exception ->
//                    Log.e("HomeFragment", "Failed to unsubscribe from heart rate: ${exception.message}")
//                }
//        } catch (e: Exception) {
//            Log.e("HomeFragment", "Error in stopRealTimeHeartRate: ${e.message}", e)
//        }
//    }
//
//    private fun startPeriodicDataFetch() {
//        coroutineScope.launch {
//            while (isActive) {
//                fetchFitnessData()
//                delay(5000)
//            }
//        }
//    }
//
//    private fun fetchRawHeartRate(startTime: Long, endTime: Long) {
//        if (!isAdded) return
//        try {
//            val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
//            val heartRawRequest = DataReadRequest.Builder()
//                .read(DataType.TYPE_HEART_RATE_BPM)
//                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
//                .build()
//
//            Fitness.getHistoryClient(requireActivity(), account)
//                .readData(heartRawRequest)
//                .addOnSuccessListener { response ->
//                    val bpm = response.dataSets.firstOrNull()?.dataPoints?.lastOrNull()
//                        ?.getValue(Field.FIELD_BPM)?.asFloat() ?: 0f
//                    if (bpm > 0f) {
//                        lastBpm = bpm
//                        heartRateText.text = "${bpm.toInt()}"
//                        saveDataToFirebase("heart_rate", bpm.toInt())
//                        checkHeartRateMission(bpm)
//                    } else {
//                        heartRateText.text = "${lastBpm.toInt()}"
//                    }
//                    Log.d("HomeFragment", "Raw heart rate points: ${response.dataSets.firstOrNull()?.dataPoints?.size ?: 0}, BPM: $bpm")
//                }
//                .addOnFailureListener { exception ->
//                    Log.e("HomeFragment", "Failed to fetch raw heart rate: ${exception.message}")
//                    heartRateText.text = "${lastBpm.toInt()}"
//                }
//        } catch (e: Exception) {
//            Log.e("HomeFragment", "Error in fetchRawHeartRate: ${e.message}", e)
//        }
//    }
//
//    private fun saveDataToFirebase(key: String, value: Any) {
//        if (!isAdded) return
//        Log.d("HomeFragment", "Attempting to save: $key = $value for userId: $userId")
//        coroutineScope.launch(Dispatchers.IO) {
//            try {
//                val userRef = database.child("users").child(userId).child(key)
//                userRef.setValue(value).addOnSuccessListener {
//                    Log.d("HomeFragment", "Successfully saved to Firebase: $key = $value")
//                }.addOnFailureListener { exception ->
//                    Log.e("HomeFragment", "Failed to save data to Firebase: $key = $value, ${exception.message}", exception)
//                    coroutineScope.launch(Dispatchers.Main) {
//                        Toast.makeText(requireContext(), "Failed to save $key", Toast.LENGTH_LONG).show()
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e("HomeFragment", "Exception in saveDataToFirebase: ${e.message}", e)
//                coroutineScope.launch(Dispatchers.Main) {
//                    Toast.makeText(requireContext(), "Error saving data", Toast.LENGTH_LONG).show()
//                }
//            }
//        }
//    }
//
//    private fun loadDailyMission() {
//        if (!isAdded) return
//        coroutineScope.launch(Dispatchers.IO) {
//            try {
//                val snapshot = database.child("users").child(userId).child("daily_mission").get().await()
//                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
//                if (snapshot.exists()) {
//                    val savedDate = snapshot.child("date").getValue(String::class.java)
//                    if (savedDate == currentDate && !missionCompleted) {
//                        currentMission = DailyMission(
//                            description = snapshot.child("description").getValue(String::class.java) ?: "",
//                            xpReward = snapshot.child("xpReward").getValue(Int::class.java) ?: 0,
//                            type = snapshot.child("type").getValue(String::class.java) ?: "",
//                            goal = snapshot.child("goal").getValue(Float::class.java) ?: 0f,
//                            duration = snapshot.child("duration").getValue(Long::class.java)
//                        )
//                        missionCompleted = snapshot.child("completed").getValue(Boolean::class.java) ?: false
//                        coroutineScope.launch(Dispatchers.Main) {
//                            dailyMissionText.text = currentMission?.description
//                            missionXPText.text = "+${currentMission?.xpReward}xp"
//                            checkMissionProgress()
//                        }
//                    } else {
//                        selectNewMission(currentDate)
//                    }
//                } else {
//                    selectNewMission(currentDate)
//                }
//            } catch (e: Exception) {
//                Log.e("HomeFragment", "Failed to load daily mission: ${e.message}")
//                coroutineScope.launch(Dispatchers.Main) {
//                    selectNewMission(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time))
//                }
//            }
//        }
//    }
//
//    private fun selectNewMission(currentDate: String) {
//        if (!isAdded) return
//        currentMission = missions[Random.nextInt(missions.size)]
//        missionCompleted = false
//        coroutineScope.launch(Dispatchers.Main) {
//            dailyMissionText.text = currentMission?.description
//            missionXPText.text = "+${currentMission?.xpReward}xp"
//        }
//        val missionData = mapOf(
//            "description" to currentMission?.description,
//            "xpReward" to currentMission?.xpReward,
//            "type" to currentMission?.type,
//            "goal" to currentMission?.goal,
//            "duration" to currentMission?.duration,
//            "progress" to 0f,
//            "date" to currentDate,
//            "completed" to false
//        )
//        saveDataToFirebase("daily_mission", missionData)
//        Log.d("HomeFragment", "Selected new mission: ${currentMission?.description}")
//        checkMissionProgress()
//    }
//
//    private fun loadDataFromFirebase() {
//        if (!isAdded) return
//        Log.d("HomeFragment", "Attempting to load data for userId: $userId")
//        coroutineScope.launch(Dispatchers.IO) {
//            try {
//                val userRef = database.child("users").child(userId)
//                userRef.addListenerForSingleValueEvent(object : ValueEventListener {
//                    override fun onDataChange(snapshot: DataSnapshot) {
//                        try {
//                            if (snapshot.exists()) {
//                                coroutineScope.launch(Dispatchers.Main) {
//                                    exerciseText.text = "${snapshot.child("exercise_minutes").getValue(Int::class.java) ?: 0} min"
//                                    heartRateText.text = "${snapshot.child("heart_rate").getValue(Int::class.java) ?: 0}"
//                                    sleepText.text = snapshot.child("sleep_hours").getValue(String::class.java) ?: "0h 0m"
//                                    sleepProgress.progress = snapshot.child("sleep_progress").getValue(Int::class.java) ?: 0
//                                    hpBar.progress = snapshot.child("hp").getValue(Int::class.java) ?: 0
//                                    playerLevel = snapshot.child("level").getValue(Int::class.java) ?: 1
//                                    if (playerLevel > 3) playerLevel = 3
//
//                                    val xpValue = snapshot.child("xp").value
//                                    playerXP = when (xpValue) {
//                                        is Long -> xpValue
//                                        is Int -> xpValue.toLong()
//                                        is String -> xpValue.toLongOrNull() ?: 0L
//                                        else -> 0L
//                                    }
//                                    currentSteps = snapshot.child("steps").getValue(Float::class.java) ?: 0f
//                                    levelText.text = playerLevel.toString()
//                                    xpText.text = "XP $playerXP/${getXpForNextLevel(playerLevel)}"
//                                    updateSkillIcons()
//                                    Log.d("HomeFragment", "Data loaded from Firebase: Online: ${snapshot.child("is_online").getValue(Boolean::class.java)}, Level: $playerLevel, XP: $playerXP, Steps: $currentSteps")
//                                    loadDailyMission()
//                                }
//                            } else {
//                                Log.w("HomeFragment", "No data found for user_id: $userId")
//                                coroutineScope.launch(Dispatchers.Main) {
//                                    playerLevel = 1
//                                    playerXP = 0
//                                    levelText.text = "1"
//                                    xpText.text = "XP 0/${getXpForNextLevel(playerLevel)}"
//                                    updateSkillIcons()
//                                    saveDataToFirebase("level", 1)
//                                    saveDataToFirebase("xp", 0L)
//                                    loadDailyMission()
//                                }
//                            }
//                        } catch (e: Exception) {
//                            Log.e("HomeFragment", "Error processing Firebase data: ${e.message}", e)
//                            coroutineScope.launch(Dispatchers.Main) {
//                                Toast.makeText(requireContext(), "Failed to load user data", Toast.LENGTH_LONG).show()
//                            }
//                        }
//                    }
//
//                    override fun onCancelled(error: DatabaseError) {
//                        Log.e("HomeFragment", "Firebase data load cancelled: ${error.message}")
//                        coroutineScope.launch(Dispatchers.Main) {
//                            Toast.makeText(requireContext(), "Failed to connect to database", Toast.LENGTH_LONG).show()
//                        }
//                    }
//                })
//            } catch (e: Exception) {
//                Log.e("HomeFragment", "Failed to load data from Firebase: ${e.message}", e)
//                coroutineScope.launch(Dispatchers.Main) {
//                    Toast.makeText(requireContext(), "Error loading data", Toast.LENGTH_LONG).show()
//                }
//            }
//        }
//    }
//}

package com.beatquest

import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException

data class DailyMission(
    val description: String,
    val xpReward: Int,
    val type: String,
    val goal: Float,
    val duration: Long? = null
)

class HomeFragment : Fragment(), SensorEventListener {

    private lateinit var exerciseText: TextView
    private lateinit var heartRateText: TextView
    private lateinit var sleepText: TextView
    private lateinit var sleepProgress: ProgressBar
    private lateinit var hpBar: ProgressBar
    private lateinit var levelText: TextView
    private lateinit var xpText: TextView
    private lateinit var dailyMissionText: TextView
    private lateinit var missionXPText: TextView
    private lateinit var coroutineScope: CoroutineScope
    private lateinit var mapImage: ImageView
    private lateinit var playerHero: ImageView
    private lateinit var skill1: LinearLayout
    private lateinit var skill2: LinearLayout
    private lateinit var skill3: LinearLayout
    private lateinit var skill1Image: ImageView
    private lateinit var skill2Image: ImageView
    private lateinit var skill3Image: ImageView
    private var waitingDialog: Dialog? = null
    private var lastBpm: Float = 0f
    private var playerLevel: Int = 1
    private var playerXP: Long = 0
    private var currentSteps: Float = 0f
    private var initialStepCount: Float? = null
    private var heartRateStartTime: Long? = null
    private var currentMission: DailyMission? = null
    private var sensorManager: SensorManager? = null
    private var missionCompleted: Boolean = false

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
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    private val defaultMissions = listOf(
        DailyMission("Accumulate 25 minutes of movement today", 400, "movement", 25f),
        DailyMission("Reach a heart rate of 105+ BPM at any point during activity", 200, "heart_rate", 105f),
        DailyMission("Accumulate 3,000 steps throughout the day", 350, "steps", 3000f),
        DailyMission("Accumulate 7.5 hours of sleep or more", 150, "sleep", 450f),
        DailyMission("Reach a heart rate of 100+ BPM for at least 2 minutes", 250, "heart_rate_duration", 100f, 120_000L),
        DailyMission("Maintain a heart rate of 98+ BPM for 3 minutes straight", 250, "heart_rate_duration", 98f, 180_000L)
    )

    private fun getXpForNextLevel(level: Int): Long {
        return when (level) {
            1 -> 1040L
            2 -> 3150L
            else -> 9999999L
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        try {
            exerciseText = view.findViewById(R.id.exerciseText)
            heartRateText = view.findViewById(R.id.heartRateText)
            sleepText = view.findViewById(R.id.sleepText)
            sleepProgress = view.findViewById(R.id.sleepProgress)
            hpBar = view.findViewById(R.id.hp)
            levelText = view.findViewById(R.id.lvl)
            xpText = view.findViewById(R.id.plyrXP)
            dailyMissionText = view.findViewById(R.id.dailyMission)
            missionXPText = view.findViewById(R.id.missionXP)
            mapImage = view.findViewById(R.id.mapImage)
            playerHero = view.findViewById(R.id.playerAvtr)
            skill1 = view.findViewById(R.id.skill1)
            skill2 = view.findViewById(R.id.skill2)
            skill3 = view.findViewById(R.id.skill3)
            skill1Image = view.findViewById(R.id.skill1_icon)
            skill2Image = view.findViewById(R.id.skill2_icon)
            skill3Image = view.findViewById(R.id.skill3_icon)
            coroutineScope = CoroutineScope(Dispatchers.Main + Job())

            if (skill1Image == null || skill2Image == null || skill3Image == null) {
                Log.e("HomeFragment", "Skill image views are null. Check fragment_home.xml for skill1_icon, skill2_icon, skill3_icon")
                Toast.makeText(requireContext(), "Error: Skill icons not found", Toast.LENGTH_LONG).show()
                return view
            }

            skill1.setOnClickListener {
                if (playerLevel >= 1) {
                    val testUserId = "test_challenger_gmail_com"
                    Log.d("HomeFragment", "Skill1 clicked, triggering challenge from $testUserId")
                    sendTestChallenge(testUserId)
                } else {
                    Log.d("HomeFragment", "Skill1 is locked (level $playerLevel < 1)")
                }
            }

            skill2.setOnClickListener {
                if (playerLevel >= 2) {
                    Log.d("HomeFragment", "Skill2 clicked (unlocked)")
                } else {
                    Log.d("HomeFragment", "Skill2 is locked (level $playerLevel < 2)")
                }
            }
            skill3.setOnClickListener {
                if (playerLevel >= 3) {
                    Log.d("HomeFragment", "Skill3 clicked (unlocked)")
                } else {
                    Log.d("HomeFragment", "Skill3 is locked (level $playerLevel < 3)")
                }
            }

            Glide.with(this)
                .load(R.drawable.assasin)
                .apply(RequestOptions().fitCenter())
                .into(playerHero)


            Glide.with(this)
                .load(R.drawable.background_map)
                .apply(RequestOptions()
                    .centerCrop()
                    .transform(com.bumptech.glide.load.resource.bitmap.RoundedCorners(100)))
                .into(mapImage)

            Log.d("HomeFragment", "User ID: $userId")

            saveDataToFirebase("is_online", true)
            setupOnDisconnect()
            fetchDataSources()
            fetchFitnessData()
            startRealTimeHeartRate()
            startPeriodicDataFetch()
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED) {
                setupStepCounter()
            } else {
                Log.w("HomeFragment", "ACTIVITY_RECOGNITION permission not granted")
                Toast.makeText(requireContext(), "Step counter unavailable: Permission denied", Toast.LENGTH_LONG).show()
            }
            loadDataFromFirebase()
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error in onCreateView: ${e.message}", e)
            Toast.makeText(requireContext(), "Initialization error", Toast.LENGTH_LONG).show()
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACTIVITY_RECOGNITION)
            == PackageManager.PERMISSION_GRANTED) {
            sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.also { sensor ->
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d("HomeFragment", "Step counter sensor registered")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        saveDataToFirebase("steps", currentSteps)
        Log.d("HomeFragment", "Step counter sensor unregistered, saved steps: $currentSteps")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        saveDataToFirebase("is_online", false)
        saveDataToFirebase("steps", currentSteps)
        coroutineScope.cancel()
        stopRealTimeHeartRate()
        waitingDialog?.dismiss()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER && !missionCompleted) {
            val totalSteps = event.values[0]
            if (initialStepCount == null) {
                initialStepCount = totalSteps
            }
            currentSteps = totalSteps - (initialStepCount ?: totalSteps)
            Log.d("HomeFragment", "Steps detected: $currentSteps")
            saveDataToFirebase("steps", currentSteps)
            checkMissionProgress()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("HomeFragment", "Step sensor accuracy changed: $accuracy")
    }

    private fun setupStepCounter() {
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.also { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("HomeFragment", "Step counter sensor initialized")
        } ?: Log.e("HomeFragment", "Step counter sensor not available")
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
            Log.d("HomeFragment", "Updated skill icons: level=$playerLevel")
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error updating skill icons: ${e.message}", e)
        }
    }

    private fun sendTestChallenge(challengerId: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d("HomeFragment", "Creating test challenge from $challengerId to $userId")
                val challengeRef = database.child("challenges").child(userId)
                val updates = mapOf(
                    "challenged" to true,
                    "challengerId" to challengerId
                )
                challengeRef.updateChildren(updates)
                    .addOnSuccessListener {
                        Log.d("HomeFragment", "Successfully set challenge: $updates")
                    }
                    .addOnFailureListener { exception ->
                        Log.e("HomeFragment", "Failed to set challenge: ${exception.message}", exception)
                    }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Exception in sendTestChallenge: ${e.message}", e)
            }
        }
    }

    private fun cancelChallenge(targetUserId: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d("HomeFragment", "Canceling challenge to $targetUserId")
                val challengeRef = database.child("challenges").child(userId)
                val updates = mapOf(
                    "challenged" to false,
                    "challengerId" to null
                )
                challengeRef.updateChildren(updates)
                    .addOnSuccessListener {
                        Log.d("HomeFragment", "Successfully cleared challenge")
                    }
                    .addOnFailureListener { exception ->
                        Log.e("HomeFragment", "Failed to clear challenge: ${exception.message}")
                    }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Exception in cancelChallenge: ${e.message}", e)
            }
        }
    }

    private fun setupOnDisconnect() {
        if (!isAdded) return
        try {
            val userRef = database.child("users").child(userId).child("is_online")
            userRef.onDisconnect().setValue(false).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("HomeFragment", "onDisconnect set to false for userId: $userId")
                } else {
                    Log.e("HomeFragment", "Failed to set onDisconnect: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error in setupOnDisconnect: ${e.message}", e)
        }
    }

    private fun fetchDataSources() {
        if (!isAdded) return
        try {
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
                    Log.d("HomeFragment", "Data sources: $sourceInfo")
                }
                .addOnFailureListener { exception ->
                    Log.e("HomeFragment", "Failed to fetch data sources: ${exception.message}")
                }
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error in fetchDataSources: ${e.message}", e)
        }
    }

    private fun fetchFitnessData() {
        if (!isAdded) return
        try {
            val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
            val endTime = System.currentTimeMillis()
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTimeToday = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, -30)
            val startTimeMonth = calendar.timeInMillis
            val startTimeRecent = endTime - TimeUnit.MINUTES.toMillis(10)

            Log.d("HomeFragment", "Google account: ${account.email ?: "No email"}")

            val moveMinutesRequest = DataReadRequest.Builder()
                .aggregate(DataType.TYPE_MOVE_MINUTES)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTimeToday, endTime, TimeUnit.MILLISECONDS)
                .build()

            Fitness.getHistoryClient(requireActivity(), account)
                .readData(moveMinutesRequest)
                .addOnSuccessListener { response ->
                    val moveMinutes = response.buckets.firstOrNull()?.getDataSet(DataType.TYPE_MOVE_MINUTES)
                        ?.dataPoints?.sumOf { it.getValue(Field.FIELD_DURATION).asInt().toDouble() } ?: 0.0
                    exerciseText.text = "${moveMinutes.toInt()}"
                    saveDataToFirebase("exercise_minutes", moveMinutes.toInt())
                    Log.d("HomeFragment", "Move minutes (today): $moveMinutes")
                    checkMissionProgress()
                }
                .addOnFailureListener { exception ->
                    Log.e("HomeFragment", "Failed to fetch move minutes: ${exception.message}")
                }

            val heartRecentRequest = DataReadRequest.Builder()
                .read(DataType.TYPE_HEART_RATE_BPM)
                .setTimeRange(startTimeRecent, endTime, TimeUnit.MILLISECONDS)
                .build()

            Fitness.getHistoryClient(requireActivity(), account)
                .readData(heartRecentRequest)
                .addOnSuccessListener { response ->
                    val bpm = response.dataSets.firstOrNull()?.dataPoints?.lastOrNull()
                        ?.getValue(Field.FIELD_BPM)?.asFloat() ?: 0f
                    if (bpm > 0f) {
                        lastBpm = bpm
                        heartRateText.text = "${bpm.toInt()}"
                        saveDataToFirebase("heart_rate", bpm.toInt())
                        checkHeartRateMission(bpm)
                    } else {
                        heartRateText.text = "${lastBpm.toInt()}"
                        fetchRawHeartRate(startTimeMonth, endTime)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("HomeFragment", "Failed to fetch recent heart rate: ${exception.message}")
                    heartRateText.text = "${lastBpm.toInt()}"
                    fetchRawHeartRate(startTimeMonth, endTime)
                }

            val sleepRequest = DataReadRequest.Builder()
                .read(DataType.TYPE_SLEEP_SEGMENT)
                .setTimeRange(startTimeMonth, endTime, TimeUnit.MILLISECONDS)
                .build()

            Fitness.getHistoryClient(requireActivity(), account)
                .readData(sleepRequest)
                .addOnSuccessListener { response ->
                    val sleepMinutes = response.dataSets.firstOrNull()?.dataPoints?.sumOf {
                        val start = it.getStartTime(TimeUnit.MINUTES)
                        val end = it.getEndTime(TimeUnit.MINUTES)
                        (end - start).toInt()
                    } ?: 0
                    val uninterruptedSleep = response.dataSets.firstOrNull()?.dataPoints
                        ?.maxByOrNull { it.getEndTime(TimeUnit.MINUTES) - it.getStartTime(TimeUnit.MINUTES) }
                        ?.let { it.getEndTime(TimeUnit.MINUTES) - it.getStartTime(TimeUnit.MINUTES) }?.toInt() ?: 0
                    val hours = sleepMinutes / 60
                    val minutes = sleepMinutes % 60
                    sleepText.text = "${hours}h ${minutes}m"
                    val maxSleepMinutes = 480
                    sleepProgress.progress = if (sleepMinutes > maxSleepMinutes) 100 else (sleepMinutes.toFloat() / maxSleepMinutes * 100).toInt()
                    val baseHp = 50
                    val hpIncrease = (hours * 5).coerceAtMost(50)
                    hpBar.progress = (baseHp + hpIncrease).coerceAtMost(100)
                    saveDataToFirebase("sleep_hours", "${hours}h ${minutes}m")
                    saveDataToFirebase("sleep_progress", sleepProgress.progress)
                    saveDataToFirebase("hp", hpBar.progress)
                    saveDataToFirebase("sleep_minutes", sleepMinutes)
                    saveDataToFirebase("uninterrupted_sleep", uninterruptedSleep)
                    Log.d("HomeFragment", "Sleep minutes: $sleepMinutes, Uninterrupted: $uninterruptedSleep, HP: ${hpBar.progress}")
                    checkMissionProgress()
                }
                .addOnFailureListener { exception ->
                    Log.e("HomeFragment", "Failed to fetch sleep: ${exception.message}")
                }
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error in fetchFitnessData: ${e.message}", e)
        }
    }

    private fun startRealTimeHeartRate() {
        if (!isAdded) return
        try {
            val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)

            Fitness.getRecordingClient(requireActivity(), account)
                .subscribe(DataType.TYPE_HEART_RATE_BPM)
                .addOnSuccessListener {
                    Log.d("HomeFragment", "Subscribed to real-time heart rate")
                }
                .addOnFailureListener { exception ->
                    Log.e("HomeFragment", "Failed to subscribe to heart rate: ${exception.message}")
                }

            Fitness.getSensorsClient(requireActivity(), account)
                .add(
                    SensorRequest.Builder()
                        .setDataType(DataType.TYPE_HEART_RATE_BPM)
                        .setSamplingRate(10, TimeUnit.SECONDS)
                        .build()
                ) { dataPoint ->
                    val bpm = dataPoint.getValue(Field.FIELD_BPM).asFloat()
                    if (bpm > 0f) {
                        lastBpm = bpm
                        heartRateText.text = "${bpm.toInt()}"
                        saveDataToFirebase("heart_rate", bpm.toInt())
                        checkHeartRateMission(bpm)
                    }
                    Log.d("HomeFragment", "Real-time BPM: $bpm")
                }
                .addOnFailureListener { exception ->
                    Log.e("HomeFragment", "Failed to add sensor listener: ${exception.message}")
                }
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error in startRealTimeHeartRate: ${e.message}", e)
        }
    }

    private fun checkHeartRateMission(bpm: Float) {
        if (!isAdded || missionCompleted || currentMission == null) return
        try {
            val mission = currentMission!!
            if (mission.type == "heart_rate" && bpm >= mission.goal) {
                completeMission(mission)
            } else if (mission.type == "heart_rate_duration" && bpm >= mission.goal) {
                if (heartRateStartTime == null) {
                    heartRateStartTime = System.currentTimeMillis()
                    Log.d("HomeFragment", "Heart rate duration started: BPM=$bpm")
                }
                val elapsed = System.currentTimeMillis() - (heartRateStartTime ?: 0)
                if (elapsed >= mission.duration!!) {
                    completeMission(mission)
                }
            } else if (mission.type == "heart_rate_duration" && bpm < mission.goal) {
                heartRateStartTime = null
                Log.d("HomeFragment", "Heart rate duration reset: BPM=$bpm below goal")
            }
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error in checkHeartRateMission: ${e.message}", e)
        }
    }

    private fun completeMission(mission: DailyMission) {
        if (!isAdded || missionCompleted) return
        missionCompleted = true
        playerXP += mission.xpReward
        val xpForNextLevel = getXpForNextLevel(playerLevel)
        if (playerLevel < 3 && playerXP >= xpForNextLevel) {
            playerLevel += 1
            playerXP -= xpForNextLevel
            saveDataToFirebase("level", playerLevel)
            updateSkillIcons()
        }
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val xpTask = database.child("users").child(userId).child("xp").setValue(playerXP)
                xpTask.addOnSuccessListener {
                    Log.d("HomeFragment", "Successfully saved XP: $playerXP")
                    coroutineScope.launch(Dispatchers.Main) {
                        xpText.text = "XP $playerXP/${getXpForNextLevel(playerLevel)}"
                        levelText.text = playerLevel.toString()
                        Toast.makeText(requireContext(), "Mission Completed: ${mission.description}! +${mission.xpReward} XP", Toast.LENGTH_LONG).show()
                        Log.d("HomeFragment", "Mission completed: ${mission.description}, XP: $playerXP, Level: $playerLevel")
                    }
                }.addOnFailureListener { exception ->
                    Log.e("HomeFragment", "Failed to save XP: ${exception.message}", exception)
                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to save XP", Toast.LENGTH_LONG).show()
                    }
                }
                xpTask.await()
                database.child("users").child(userId).child("daily_mission").removeValue()
                coroutineScope.launch(Dispatchers.Main) {
                    currentMission = null
                    missionCompleted = false
                    loadDailyMission()
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error in completeMission: ${e.message}", e)
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error completing mission", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkMissionProgress() {
        if (!isAdded || missionCompleted || currentMission == null) return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val snapshot = database.child("users").child(userId).get().await()
                val mission = currentMission!!
                when (mission.type) {
                    "steps" -> {
                        val steps = snapshot.child("steps").getValue(Float::class.java) ?: currentSteps
                        if (steps >= mission.goal) {
                            coroutineScope.launch(Dispatchers.Main) { completeMission(mission) }
                        } else if (steps >= 100) {
                            coroutineScope.launch(Dispatchers.Main) {
                                val currentXpForNextLevel = getXpForNextLevel(playerLevel)
                                playerXP += 10
                                if (playerLevel < 3 && playerXP >= currentXpForNextLevel) {
                                    playerLevel += 1
                                    playerXP -= currentXpForNextLevel
                                    saveDataToFirebase("level", playerLevel)
                                    updateSkillIcons()
                                }
                                saveDataToFirebase("xp", playerXP)
                                xpText.text = "XP $playerXP/$currentXpForNextLevel"
                                levelText.text = playerLevel.toString()
                                Toast.makeText(requireContext(), "Test: 100 steps reached! +10 XP", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    "movement" -> {
                        val moveMinutes = snapshot.child("exercise_minutes").getValue(Int::class.java) ?: 0
                        if (moveMinutes >= mission.goal) {
                            coroutineScope.launch(Dispatchers.Main) { completeMission(mission) }
                        }
                    }
                    "sleep" -> {
                        val sleepMinutes = snapshot.child("sleep_minutes").getValue(Int::class.java) ?: 0
                        if (sleepMinutes >= mission.goal) {
                            coroutineScope.launch(Dispatchers.Main) { completeMission(mission) }
                        }
                    }
                    "heart_rate" -> {
                        val heartRate = snapshot.child("heart_rate").getValue(Int::class.java) ?: 0
                        if (heartRate >= mission.goal) {
                            coroutineScope.launch(Dispatchers.Main) { completeMission(mission) }
                        }
                    }
                    "heart_rate_duration" -> {
                        // Handled in checkHeartRateMission
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Failed to check mission progress: ${e.message}")
            }
        }
    }

    private fun stopRealTimeHeartRate() {
        if (!isAdded) return
        try {
            val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
            Fitness.getSensorsClient(requireActivity(), account)
                .remove { dataPoint ->
                    Log.d("HomeFragment", "Removed sensor listener for BPM")
                    true
                }
                .addOnSuccessListener {
                    Log.d("HomeFragment", "Successfully unsubscribed from heart rate updates")
                }
                .addOnFailureListener { exception ->
                    Log.e("HomeFragment", "Failed to unsubscribe from heart rate: ${exception.message}")
                }
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error in stopRealTimeHeartRate: ${e.message}", e)
        }
    }

    private fun startPeriodicDataFetch() {
        coroutineScope.launch {
            while (isActive) {
                fetchFitnessData()
                delay(5000)
            }
        }
    }

    private fun fetchRawHeartRate(startTime: Long, endTime: Long) {
        if (!isAdded) return
        try {
            val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
            val heartRawRequest = DataReadRequest.Builder()
                .read(DataType.TYPE_HEART_RATE_BPM)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()

            Fitness.getHistoryClient(requireActivity(), account)
                .readData(heartRawRequest)
                .addOnSuccessListener { response ->
                    val bpm = response.dataSets.firstOrNull()?.dataPoints?.lastOrNull()
                        ?.getValue(Field.FIELD_BPM)?.asFloat() ?: 0f
                    if (bpm > 0f) {
                        lastBpm = bpm
                        heartRateText.text = "${bpm.toInt()}"
                        saveDataToFirebase("heart_rate", bpm.toInt())
                        checkHeartRateMission(bpm)
                    } else {
                        heartRateText.text = "${lastBpm.toInt()}"
                    }
                    Log.d("HomeFragment", "Raw heart rate points: ${response.dataSets.firstOrNull()?.dataPoints?.size ?: 0}, BPM: $bpm")
                }
                .addOnFailureListener { exception ->
                    Log.e("HomeFragment", "Failed to fetch raw heart rate: ${exception.message}")
                    heartRateText.text = "${lastBpm.toInt()}"
                }
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error in fetchRawHeartRate: ${e.message}", e)
        }
    }

    private fun saveDataToFirebase(key: String, value: Any) {
        if (!isAdded) return
        Log.d("HomeFragment", "Attempting to save: $key = $value for userId: $userId")
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val userRef = database.child("users").child(userId).child(key)
                userRef.setValue(value).addOnSuccessListener {
                    Log.d("HomeFragment", "Successfully saved to Firebase: $key = $value")
                }.addOnFailureListener { exception ->
                    Log.e("HomeFragment", "Failed to save data to Firebase: $key = $value, ${exception.message}", exception)
                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to save $key", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Exception in saveDataToFirebase: ${e.message}", e)
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error saving data", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadDailyMission() {
        if (!isAdded) return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val snapshot = database.child("users").child(userId).child("daily_mission").get().await()
                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
                if (snapshot.exists()) {
                    val savedDate = snapshot.child("date").getValue(String::class.java)
                    if (savedDate == currentDate && !missionCompleted) {
                        currentMission = DailyMission(
                            description = snapshot.child("description").getValue(String::class.java) ?: "",
                            xpReward = snapshot.child("xpReward").getValue(Int::class.java) ?: 0,
                            type = snapshot.child("type").getValue(String::class.java) ?: "",
                            goal = snapshot.child("goal").getValue(Float::class.java) ?: 0f,
                            duration = snapshot.child("duration").getValue(Long::class.java)
                        )
                        missionCompleted = snapshot.child("completed").getValue(Boolean::class.java) ?: false
                        coroutineScope.launch(Dispatchers.Main) {
                            dailyMissionText.text = currentMission?.description
                            missionXPText.text = "+${currentMission?.xpReward}xp"
                            checkMissionProgress()
                        }
                    } else {
                        selectNewMission(currentDate)
                    }
                } else {
                    selectNewMission(currentDate)
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Failed to load daily mission: ${e.message}")
                coroutineScope.launch(Dispatchers.Main) {
                    selectNewMission(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time))
                }
            }
        }
    }

    private fun selectNewMission(currentDate: String) {
        if (!isAdded) return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val prompt = """
                    You are a fitness game mission generator for BeatQuest, a mobile game that uses Google Fit data (steps, movement minutes, heart rate, sleep) to drive gameplay. Generate a single daily mission for a player. The mission must be one of the following types:
                    - "steps": Achieve a specific number of steps (e.g., 2,000–10,000 steps).
                    - "movement": Accumulate a specific number of active minutes (e.g., 15–60 minutes).
                    - "heart_rate": Reach a specific heart rate (e.g., 100–130 BPM) at any point.
                    - "sleep": Achieve a specific sleep duration (e.g., 6–8 hours).
                    - "heart_rate_duration": Maintain a heart rate above a threshold (e.g., 95–110 BPM) for a duration (e.g., 2–5 minutes).
                    The mission should include:
                    - A description (e.g., "Walk 5,000 steps today").
                    - An XP reward (100–500 XP, proportional to difficulty).
                    - A type (one of the above).
                    - A goal (numeric value, e.g., 5000 for steps, 100 for heart_rate, 360 for sleep in minutes, etc.).
                    - A duration (for heart_rate_duration missions only, in milliseconds, e.g., 120000 for 2 minutes).
                    Return the response as a JSON object with fields: description, xpReward, type, goal, and duration (null for non-heart_rate_duration missions). Example:
                    {
                      "description": "Walk 5,000 steps today",
                      "xpReward": 300,
                      "type": "steps",
                      "goal": 5000,
                      "duration": null
                    }
                """.trimIndent()

                val requestBody = JSONObject()
                    .put("model", "deepseek/deepseek-v3-0324")
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                    .put("stream", false)
                    .toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://router.huggingface.co/novita/v3/openai/chat/completions")
                    .header("Authorization", "Bearer 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")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d("HomeFragment", "Raw API response: $responseBody")
                    try {
                        val json = JSONObject(responseBody)
                        val missionJson = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                        Log.d("HomeFragment", "Mission JSON: $missionJson")
                        val cleanedMissionJson = missionJson
                            .replace("```json", "")
                            .replace("```", "")
                            .trim()
                        try {
                            val missionData = JSONObject(cleanedMissionJson)
                            currentMission = DailyMission(
                                description = missionData.getString("description"),
                                xpReward = missionData.getInt("xpReward"),
                                type = missionData.getString("type"),
                                goal = missionData.getDouble("goal").toFloat(),
                                duration = if (missionData.isNull("duration")) null else missionData.getLong("duration")
                            )
                            Log.d("HomeFragment", "Fetched AI mission: ${currentMission?.description}, XP: ${currentMission?.xpReward}")
                        } catch (e: JSONException) {
                            Log.e("HomeFragment", "Failed to parse mission JSON: $cleanedMissionJson, error: ${e.message}", e)
                            currentMission = defaultMissions[Random.nextInt(defaultMissions.size)]
                            coroutineScope.launch(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Error parsing mission, using default", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e("HomeFragment", "Failed to parse API response: $responseBody, error: ${e.message}", e)
                        currentMission = defaultMissions[Random.nextInt(defaultMissions.size)]
                        coroutineScope.launch(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Error parsing API response, using default", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Log.e("HomeFragment", "API call failed: ${response.code} ${response.message}, body: ${response.body?.string()}")
                    currentMission = defaultMissions[Random.nextInt(defaultMissions.size)]
                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to fetch mission, using default", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error fetching AI mission: ${e.message}", e)
                currentMission = defaultMissions[Random.nextInt(defaultMissions.size)]
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error fetching mission, using default", Toast.LENGTH_LONG).show()
                }
            }

            missionCompleted = false
            coroutineScope.launch(Dispatchers.Main) {
                dailyMissionText.text = currentMission?.description
                missionXPText.text = "+${currentMission?.xpReward}xp"
            }
            val missionData = mapOf(
                "description" to currentMission?.description,
                "xpReward" to currentMission?.xpReward,
                "type" to currentMission?.type,
                "goal" to currentMission?.goal,
                "duration" to currentMission?.duration,
                "progress" to 0f,
                "date" to currentDate,
                "completed" to false
            )
            saveDataToFirebase("daily_mission", missionData)
            Log.d("HomeFragment", "Selected mission: ${currentMission?.description}")
            checkMissionProgress()
        }
    }

    private fun loadDataFromFirebase() {
        if (!isAdded) return
        Log.d("HomeFragment", "Attempting to load data for userId: $userId")
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val userRef = database.child("users").child(userId)
                userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            if (snapshot.exists()) {
                                coroutineScope.launch(Dispatchers.Main) {
                                    exerciseText.text = "${snapshot.child("exercise_minutes").getValue(Int::class.java) ?: 0} min"
                                    heartRateText.text = "${snapshot.child("heart_rate").getValue(Int::class.java) ?: 0}"
                                    sleepText.text = snapshot.child("sleep_hours").getValue(String::class.java) ?: "0h 0m"
                                    sleepProgress.progress = snapshot.child("sleep_progress").getValue(Int::class.java) ?: 0
                                    hpBar.progress = snapshot.child("hp").getValue(Int::class.java) ?: 0
                                    playerLevel = snapshot.child("level").getValue(Int::class.java) ?: 1
                                    if (playerLevel > 3) playerLevel = 3
                                    val xpValue = snapshot.child("xp").value
                                    playerXP = when (xpValue) {
                                        is Long -> xpValue
                                        is Int -> xpValue.toLong()
                                        is String -> xpValue.toLongOrNull() ?: 0L
                                        else -> 0L
                                    }
                                    currentSteps = snapshot.child("steps").getValue(Float::class.java) ?: 0f
                                    levelText.text = playerLevel.toString()
                                    xpText.text = "XP $playerXP/${getXpForNextLevel(playerLevel)}"
                                    updateSkillIcons()
                                    Log.d("HomeFragment", "Data loaded from Firebase: Online: ${snapshot.child("is_online").getValue(Boolean::class.java)}, Level: $playerLevel, XP: $playerXP, Steps: $currentSteps")
                                    loadDailyMission()
                                }
                            } else {
                                Log.w("HomeFragment", "No data found for user_id: $userId")
                                coroutineScope.launch(Dispatchers.Main) {
                                    playerLevel = 1
                                    playerXP = 0
                                    levelText.text = "1"
                                    xpText.text = "XP 0/${getXpForNextLevel(playerLevel)}"
                                    updateSkillIcons()
                                    saveDataToFirebase("level", 1)
                                    saveDataToFirebase("xp", 0L)
                                    loadDailyMission()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("HomeFragment", "Error processing Firebase data: ${e.message}", e)
                            coroutineScope.launch(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Failed to load user data", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("HomeFragment", "Firebase data load cancelled: ${error.message}")
                        coroutineScope.launch(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Failed to connect to database", Toast.LENGTH_LONG).show()
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e("HomeFragment", "Failed to load data from Firebase: ${e.message}", e)
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error loading data", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}