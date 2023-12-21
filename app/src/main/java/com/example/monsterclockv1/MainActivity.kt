package com.example.monsterclockv1

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Color.argb
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.monsterclockv1.database.AppDatabase
import com.example.monsterclockv1.database.DatabaseProvider
import com.example.monsterclockv1.database.Evolution
import com.example.monsterclockv1.database.Monster
import com.example.monsterclockv1.database.MonsterServing
import com.example.monsterclockv1.enums.MonsterType
import com.jakewharton.threetenabp.AndroidThreeTen
import com.takusemba.spotlight.OnSpotlightListener
import com.takusemba.spotlight.OnTargetListener
import com.takusemba.spotlight.Spotlight
import com.takusemba.spotlight.Target
import com.takusemba.spotlight.effet.RippleEffect
import com.takusemba.spotlight.shape.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.Instant
import java.io.Serializable


var testDeleteDB = true
var testFirstLaunch = true
// check if web is ok, check if zeczec user give code
var qualifiedUser = false

class MainActivity : AppCompatActivity(), EggSelectDialogFragment.GifSelectionListener, PayToUnlockHatcherDialogFragment.ChoosePayListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var addButton: ImageView
    private lateinit var monsterIndexButton :ImageView
    private val monsterServingList = mutableListOf<MonsterServing>() // List of GIF URLs
    private val evolutions = mutableListOf<Evolution>()
    private lateinit var db: AppDatabase
    private lateinit var gifAdapter: GifAdapter
    private lateinit var evolutionResultLauncher: ActivityResultLauncher<Intent>
    private var firstLaunch: Boolean = false
    private var addButtonSpotlight: Spotlight? = null
    private var needSpotlightOnSetClock: Boolean = false

    companion object {
        const val ACTION_CHANGE_MONSTER = "com.example.monsterclockv1.CHANGE_MONSTER"
        const val CHANGE_MONSTER_ID = "com.example.monsterclockv1.CHANGE_MONSTER_ID"
        var setClockButtonSpotlight: Spotlight? = null
        fun sendChangeMonsterBroadcast(monsterId: Int, context: Context) {
            Log.d("MainActivity", "Sending broadcast to change monster: $monsterId")
            val intent = Intent(context, ClockWidget::class.java).apply {
                action = ACTION_CHANGE_MONSTER
                putExtra(CHANGE_MONSTER_ID, monsterId)
            }
            context.sendBroadcast(intent)
            Log.d("Companion", "CHANGE_MONSTER_ID Broadcast sent")
        }
    }

    private fun attachNewMonsterServingTags(monsterServing: MonsterServing, monster: Monster) {
        monsterServing.needNewTimer = true
        monsterServing.pixelGifName = monster.pixelGifName
        monsterServing.monsterType = monster.monsterType
        monsterServing.evolveSeconds = monster.evolveSeconds
        monsterServing.evolveRemainingSeconds = monster.evolveSeconds
        setMonsterDiscovered(monsterServing.monsterId)
    }

    private fun setMonsterDiscovered(monsterId:Int) {
        Thread {
            db.monsterDao().setMonsterDiscoveredById(monsterId)
        }.start()
    }

    override fun onEggSelected(monster: Monster, evolution: Evolution) {
        Thread {
            addNewEggToService(monster, evolution)
        }.start()
    }

    override fun onPaySelected() {
        // TODO: Lead to IPA
        qualifiedUser = true
        println("IPA qualifiedUser: $qualifiedUser")
        // go to select egg dialog
        val dialog = EggSelectDialogFragment()
        dialog.listener = this
        dialog.show(supportFragmentManager, "EggSelectDialogFragment")
    }

    override fun onWatchAdSelected() {
        // TODO: Lead to Ad
        qualifiedUser = false
        println("Watch Ad qualifiedUser: $qualifiedUser")
        // go to select egg dialog
        val dialog = EggSelectDialogFragment()
        dialog.listener = this
        dialog.show(supportFragmentManager, "EggSelectDialogFragment")
    }

    private fun onMonsterEvolve(position: Int, nextEnvironment: MonsterType) {
        lifecycleScope.launch(Dispatchers.IO) {
            // decide new monsterId
            val servingMonster = db.monsterServingDao().getMonsterServingByPosition(position)
            println("servingMonster: $servingMonster")
            val evolution = db.evolutionDao().getEvolutionByMonsterId(servingMonster.monsterId)
            val evolveEnv = servingMonster.environment
            val newMonsterId = when (evolveEnv!!) {
                MonsterType.MORNING -> evolution.morningEvolutionId
                MonsterType.BALANCE -> evolution.balanceEvolutionId
                MonsterType.NIGHT -> evolution.nightEvolutionId
            }
            val newEvolution = db.evolutionDao().getEvolutionByMonsterId(newMonsterId!!)
            val newMonster = db.monsterDao().getMonsterById(newMonsterId)
            println("newMonsterId: $newMonsterId")
            // update new monsterServing
            val newServing = MonsterServing(
                servingId = servingMonster.servingId,
                monsterId = newMonsterId!!,
                serveStartTime = Instant.now(),
                position = position,
                environment = nextEnvironment
            )
            db.monsterServingDao().updateMonsterServing(
                newServing
            )
            // update monsters and evolutions
            attachNewMonsterServingTags(newServing, newMonster)
            monsterServingList[position] = newServing
            evolutions[position] = newEvolution
            runOnUiThread {
                gifAdapter.notifyItemChanged(position)
            }
        }
    }

    private fun checkMonsterEvolution() {
        // check if monsterId has evolution
        // if yes, then check if it is time to evolve
        // if yes, then evolve
        // if no, then do nothing
        monsterServingList.forEach {
            val monsterId = it.monsterId
            val position = monsterServingList.indexOf(it)
            val allServingMonsters = db.monsterServingDao().getAllServingsMonsterSorted()
            println("allServingMonsters: $allServingMonsters")
            val servingMonster = db.monsterServingDao().getMonsterServingByPosition(position)
            val evolution = db.evolutionDao().getEvolutionByMonsterId(monsterId)
            val possibleEvolutions: Map<String,Int?> = mapOf(
                "morning" to evolution!!.morningEvolutionId,
                "balance" to evolution!!.balanceEvolutionId,
                "night" to evolution!!.nightEvolutionId
            )

            if (possibleEvolutions.values.any { it != null }) {
                // if time now - serveStartTime > 10sec, then evolve
                println("servingMonster buggy: $servingMonster")
                val timeDiffer = Instant.now().epochSecond - servingMonster.serveStartTime.epochSecond
                // temporary set to 5 sec for testing
                if (timeDiffer >= it.evolveSeconds) {
                    it.needEvolve = true
                }
            }
        }
        // notify adapter
        runOnUiThread {
            gifAdapter.notifyDataSetChanged()
        }
    }

    private fun startPeriodicCheck() {
        lifecycleScope.launch {
            while (isActive) { // 当Activity处于活动状态时循环执行
                withContext(Dispatchers.IO) {
                    checkMonsterEvolution()
                }
                delay(1000) // 等待5秒

                if (needSpotlightOnSetClock) {
                    println("needSpotlightOnSetClock in resume now: $needSpotlightOnSetClock")
                    showSetClockSpotlight()
                    needSpotlightOnSetClock = false
                }
            }
        }
    }

    private fun evolutionActivityLauncher() {
        evolutionResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val position = data!!.getIntExtra("POSITION", 0)
                val nextEnvironment = data!!.getStringExtra("NEXT_ENVIRONMENT")
                println("nextEnvironment: $nextEnvironment")
                onMonsterEvolve(position, MonsterType.fromString(nextEnvironment!!))
            }
        }
    }

    fun openEvolutionActivity(evolution: Evolution, position: Int = 0) {
        val intent = Intent(this, EvolutionActivity::class.java)
        intent.putExtra("SERVING_MONSTER", monsterServingList[position] as Parcelable)
        intent.putExtra("EVOLUTION", evolution as Parcelable)
        intent.putExtra("POSITION", position)
        evolutionResultLauncher.launch(intent)
    }

    private fun addNewEggToService(monster: Monster, evolution: Evolution) {
        val allServingMonsters = db.monsterServingDao().getAllServingsMonster()
        val newServing = MonsterServing(
            monsterId = monster.monsterId,
            serveStartTime = Instant.now(),
            environment = monster.monsterType,
            position = allServingMonsters.size
        )
        db.monsterServingDao().insertMonsterServing(
            newServing
        )
        runOnUiThread {
            //monster.servingType = monster.monsterType
            attachNewMonsterServingTags(newServing, monster)
            monsterServingList.add(newServing)
            evolutions.add(evolution)
            gifAdapter.notifyItemInserted(monsterServingList.size - 1)
            if (monsterServingList.size == 1 && firstLaunch) {  // also need to check isFirstLaunch
                needSpotlightOnSetClock = true
                println("needSpotlightOnSetClock set to true: $needSpotlightOnSetClock")
            }
        }
    }

    private fun showAddButtonGuidance() {
        val addButton = findViewById<ImageView>(R.id.addButton) // 确保这里是正确的 ID
        addButton.post {
            addButtonSpotlight = buildSpotlightForButton(addButton, R.layout.guide_add_egg_overlay)
            addButtonSpotlight!!.start() // 启动 Spotlight
        }
    }

    private fun buildSpotlightForButton(button: ImageView, layout:Int): Spotlight {
        val location = IntArray(2)
        button.getLocationInWindow(location)
        val x = location[0] + button.width / 2f
        val y = location[1] + button.height / 5f
        val shapeWidth = button.width.toFloat() // 按钮的宽度
        val shapeHeight = button.height.toFloat() // 按钮的高度
        val cornerRadius = 10f // 圆角半径，可根据需要调整
        println("x: $x, y: $y, shapeWidth: $shapeWidth, shapeHeight: $shapeHeight")

        val addButtonTarget = Target.Builder()
            .setAnchor(x, y)
            .setShape(RoundedRectangle(
                shapeHeight,
                shapeWidth,
                cornerRadius
            ))
            .setEffect(RippleEffect(100f, 200f, argb(255, 255, 255, 255))) // 简单的 Ripple 效果
            .setOverlay(layoutInflater.inflate(layout, null))
            .setOnTargetListener(object : OnTargetListener {
                override fun onStarted() {
                    Log.d("MainActivity", "Simple target is started")
                }

                override fun onEnded() {
                    Log.d("MainActivity", "Simple target is ended")
                }
            })
            .build()

        val spotlight = Spotlight.Builder(this)
            .setTargets(addButtonTarget)
            .setBackgroundColor(R.color.dark_blue_50) // 使用明亮的背景色
            .setDuration(1000L)
            .setAnimation(DecelerateInterpolator(2f))
            .setContainer(findViewById<ViewGroup>(android.R.id.content))
            .setOnSpotlightListener(object : OnSpotlightListener {
                override fun onStarted() {
                    Log.d("MainActivity", "Simple Spotlight is started")
                }

                override fun onEnded() {
                    Log.d("MainActivity", "Simple Spotlight is ended")
                    if (layout == R.layout.guide_set_clock_overlay) {
                        val intent = Intent(this@MainActivity, GuideSetWidgetActivity::class.java)
                        this@MainActivity.startActivity(intent)
                        firstLaunch = false
                    }
                }
            })
            .build()
        return spotlight
    }

    private fun showSetClockSpotlight() {
        recyclerView.post {
            val firstItemView = recyclerView.findViewHolderForAdapterPosition(0)?.itemView
            firstItemView?.let { view ->
                // 假设您要聚焦的按钮的 ID 为 R.id.your_button_id
                val targetButton = view.findViewById<ImageView>(R.id.actionApplyClockButton)
                setClockButtonSpotlight = buildSpotlightForButton(targetButton, R.layout.guide_set_clock_overlay)
                setClockButtonSpotlight!!.start()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidThreeTen.init(this)
        db = DatabaseProvider.getDatabase(this)
        setContentView(R.layout.activity_main)

        // get shared preferences
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        firstLaunch = sharedPref.getBoolean("IsFirstTimeLaunch", true)

        if (BuildConfig.DEBUG && testDeleteDB) {
            // 清空資料庫或重置 SharedPreferences
            DatabaseProvider.clearDatabase(this)
            testDeleteDB = false
        }
        if (BuildConfig.DEBUG && testFirstLaunch) {
            firstLaunch = true
            testFirstLaunch = false
        }

        // set guidance at first time launch
        if (firstLaunch) {
            showAddButtonGuidance()
            with (sharedPref.edit()) {
                putBoolean("IsFirstTimeLaunch", false)
                commit()
            }
        }

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        addButton = findViewById(R.id.addButton)
        monsterIndexButton = findViewById(R.id.indexButton)

        // prepare evolution launcher
        evolutionActivityLauncher()

        // read serving monsters and add to gifList
        lifecycleScope.launch {
            var monsterServings = withContext(Dispatchers.IO) {
                db.monsterServingDao().getAllServingsMonster()
            }

            // for testing
            // if length of servingMonsters is >3, then only keep the oldest 3
            if (monsterServings.size > 3) {
                val monsterServingsSorted = monsterServings.sortedBy { it.servingId }
                val monsterServingsSortedKeep = monsterServingsSorted.subList(monsterServingsSorted.size - 3, monsterServingsSorted.size)

                // delete all serving monsters
                withContext(Dispatchers.IO) {
                    db.monsterServingDao().deleteAll()
                }
                // add serving monsters back
                Thread {
                    db.monsterServingDao().insertMonsterServings(monsterServingsSortedKeep)
                }.start()
                monsterServings = monsterServingsSortedKeep
            }

            // recover monsters tags
            for (i in monsterServings.indices) {
                // count remaining seconds to continue countdown
                val timeDiffer = Instant.now().epochSecond - monsterServings[i].serveStartTime.epochSecond
                val remainingSeconds = monsterServings[i].evolveSeconds - timeDiffer
                monsterServings[i].evolveRemainingSeconds = remainingSeconds.toInt()
                // set environment
                // get evolution
                val evolution = withContext(Dispatchers.IO) {
                    db.evolutionDao().getEvolutionByMonsterId(monsterServings[i].monsterId)
                }
                evolutions.add(evolution!!)
            }
            // add to monstersServingList
            this@MainActivity.monsterServingList.addAll(monsterServings)
            // check if any monster reaches evolution time
            startPeriodicCheck()
            gifAdapter = GifAdapter(this@MainActivity.monsterServingList, this@MainActivity, evolutions)
            recyclerView.adapter = gifAdapter
        }

        // set add button
        addButton.setOnClickListener {
            if (addButtonSpotlight != null) {
                addButtonSpotlight?.finish()
                addButtonSpotlight = null
            }
            if (qualifiedUser || monsterServingList.size < 1) {
                val dialog = EggSelectDialogFragment()
                dialog.listener = this
                dialog.show(supportFragmentManager, "EggSelectDialogFragment")
            } else {
                // show warning to IPA to unlock new column
                val dialog = PayToUnlockHatcherDialogFragment()
                dialog.listener = this
                dialog.show(supportFragmentManager, "PayToUnlockHatcherDialogFragment")
            }
        }

        //set index button
        monsterIndexButton.setOnClickListener {
            val intent = Intent(this, MonsterIndexActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
    }

    class GifAdapter(private val monsterServingList: List<MonsterServing>, private val context: Context, private var evolutions: List<Evolution>) :
        RecyclerView.Adapter<GifAdapter.GifViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GifViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.main_items, parent, false)
            return GifViewHolder(view, context)
        }

        override fun onBindViewHolder(holder: GifViewHolder, position: Int) {
            holder.bind(monsterServingList[position], position, evolutions[position])
        }

        override fun getItemCount(): Int = monsterServingList.size

        class GifViewHolder(itemView: View, private val context: Context) :
            RecyclerView.ViewHolder(itemView) {
            private val gifImageView: ImageView = itemView.findViewById(R.id.gifImageView)
            private val setClockButton: ImageView = itemView.findViewById(R.id.actionApplyClockButton)
            private val removeButton: ImageView = itemView.findViewById(R.id.actionDismissMonsterButton)
            private val showTypeImageView: ImageView = itemView.findViewById(R.id.showTypeImageView)
            private var countdownTimerTextView: TextView = itemView.findViewById(R.id.countdownTimerTextView)
            private var countdownTimer: CountDownTimer? = null

            fun bind(monsterServing: MonsterServing, position:Int, evolution: Evolution) {
                val context = itemView.context
                val resourceId = context.resources.getIdentifier(monsterServing.pixelGifName, "drawable", context.packageName)

                // show monster gif
                Glide.with(itemView.context)
                    .asGif()
                    .load(resourceId)
                    .into(gifImageView)
                // show showType and background
                when (monsterServing.environment) {
                    MonsterType.MORNING -> {
                        Glide.with(itemView.context)
                            .asGif()
                            .load(R.drawable.type_morning_gif)
                            .into(showTypeImageView)
                        gifImageView.setBackgroundResource(R.drawable.border_style)
                    }
                    MonsterType.BALANCE -> {
                        Glide.with(itemView.context)
                            .asGif()
                            .load(R.drawable.type_balance_gif)
                            .into(showTypeImageView)
                        gifImageView.setBackgroundResource(R.drawable.border_style_balance)
                    }
                    MonsterType.NIGHT -> {
                        Glide.with(itemView.context)
                            .asGif()
                            .load(R.drawable.type_night_gif)
                            .into(showTypeImageView)
                        gifImageView.setBackgroundResource(R.drawable.border_style_night)
                    }
                }
                // show evolve warning if needed
                if (monsterServing.needEvolve) {
                    Glide.with(itemView.context)
                        .asGif()
                        .load(R.drawable.evolution_exclaimation)
                        .into(gifImageView)
                }

                // set onClickListener for gifImageView
                gifImageView.setOnClickListener {
                    if (!monsterServing.needEvolve) {
                        val intent = Intent(context, GifDetailActivity::class.java)
                        val resourceId = context.resources.getIdentifier(monsterServing.pixelGifName, "drawable", context.packageName)
                        intent.putExtra("GIF_RESOURCE_ID", resourceId)
                        intent.putExtra("MONSTER_ID", monsterServing.monsterId)
                        context.startActivity(intent)
                    } else {
                        (context as MainActivity).openEvolutionActivity(evolution, position)
                    }
                }

                // set onClickListener for setClockButton
                setClockButton.setOnClickListener {
                    sendChangeMonsterBroadcast(monsterServing.monsterId, context)
                    // pop warning
                    Toast.makeText(context, "Monster clock has set to this monster's style.", Toast.LENGTH_SHORT).show()
                    if (setClockButtonSpotlight != null) {
                        setClockButtonSpotlight?.finish()
                        setClockButtonSpotlight = null
                    }
                }

                // set onClickListener for removeButton
                removeButton.setOnClickListener {
                    // AlertDialog
                    val builder = AlertDialog.Builder(context)
                    builder.setTitle("Remove Monster")
                    builder.setMessage("Are you sure you want to remove this monster?")
                    builder.setPositiveButton("Yes") { _, _ ->
                        // remove monster from database
                        Thread {
                            (context as MainActivity).db.monsterServingDao().deleteMonsterServingByPosition(position)
                            // change remaining monsters' position
                            val remainingMonsters = context.db.monsterServingDao().getAllServingsMonsterSorted()
                            for (i in position until remainingMonsters.size) {
                                context.db.monsterServingDao().updateMonsterServing(
                                    MonsterServing(
                                        servingId = remainingMonsters[i].servingId,
                                        monsterId = remainingMonsters[i].monsterId,
                                        serveStartTime = remainingMonsters[i].serveStartTime,
                                        environment = remainingMonsters[i].environment,
                                        position = i
                                    )
                                )
                            }
                        }.start()
                        // remove monster from monsters
                        (context as MainActivity).monsterServingList.removeAt(position)
                        context.evolutions.removeAt(position)
                        // notify adapter
                        context.runOnUiThread {
                            context.gifAdapter.notifyItemRemoved(position)
                        }
                    }
                    builder.setNegativeButton("No") { _, _ ->
                        // do nothing
                    }
                    val dialog: AlertDialog = builder.create()
                    dialog.show()
                }


                // set count down timer
                if (monsterServing.needNewTimer) {
                    println("monster.needsTimerUpdate: ${monsterServing.needNewTimer}")
                    countdownTimer?.cancel()
                    countdownTimer = null
                    monsterServing.needNewTimer = false
                }
                if (countdownTimer == null) {
                    countdownTimer = object : CountDownTimer(monsterServing.evolveRemainingSeconds.toLong()*1000, 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                            // 更新 TextView 的文本
                            val remainSeconds = (millisUntilFinished / 1000).toInt()
                            val hours = remainSeconds / 60 / 60
                            val minutes = (remainSeconds / 60) % 60
                            val seconds = remainSeconds % 60
                            countdownTimerTextView.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                            if (monsterServing.environment == MonsterType.NIGHT) {
                                countdownTimerTextView.setTextColor(Color.argb(255, 255, 255, 255))
                            }
                            println("countdownTimerTextView.text: ${countdownTimerTextView.text}")
                        }

                        override fun onFinish() {
                            countdownTimerTextView.text = "00:00:00"
                            // 计时结束时的操作 註銷計時器
                            cancel()
                        }
                    }.start()
                }
            }
        }
    }
}