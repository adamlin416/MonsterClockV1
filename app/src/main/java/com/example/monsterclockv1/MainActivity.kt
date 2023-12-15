package com.example.monsterclockv1

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Color.argb
import android.os.Bundle
import android.os.CountDownTimer
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


class MainActivity : AppCompatActivity(), EggSelectDialogFragment.GifSelectionListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var addButton: ImageView
    private val monsters = mutableListOf<Monster>() // List of GIF URLs
    private val evolutions = mutableListOf<Evolution>()
    private lateinit var db: AppDatabase
    private lateinit var gifAdapter: GifAdapter
    private lateinit var evolutionResultLauncher: ActivityResultLauncher<Intent>
    private var firstLaunch: Boolean = false
    private var addButtonSpotlight: Spotlight? = null
    private var needSpotlightOnSetClock: Boolean = false

    // BUG to be fix: 當按下夜晚型態的蛋時，第一個變成驚嘆號之後，再點出新的蛋 會馬上變成驚嘆號

    companion object {
        const val ACTION_CHANGE_MONSTER = "com.example.monsterclockv1.CHANGE_MONSTER"
        const val CHANGE_MONSTER_ID = "com.example.monsterclockv1.CHANGE_MONSTER_ID"
        var setClockButtonSpotlight: Spotlight? = null
    }

    private fun setNewMonsterTags(monster: Monster, type: MonsterType) {
        monster.servingType = type
        monster.needNewTimer = true
        monster.evolveRemainingSeconds = monster.evolveSeconds!!
        setMonsterDiscovered(monster.monsterId)
    }

    private fun setMonsterDiscovered(monsterId:Int) {
        Thread {
            db.monsterDao().setMonsterDiscoveredById(monsterId)
        }.start()
    }

    override fun onEggSelected(monster: Monster, evolution: Evolution) {
        Thread {
            addNewEggToService(monster)
        }.start()
        runOnUiThread {
            //monster.servingType = monster.monsterType
            setNewMonsterTags(monster, monster.monsterType)
            monsters.add(monster)
            evolutions.add(evolution)
            gifAdapter.notifyItemInserted(monsters.size - 1)
            if (monsters.size == 1) {  // also need to check isFirstLaunch
                needSpotlightOnSetClock = true
                println("needSpotlightOnSetClock set to true: $needSpotlightOnSetClock")
            }
        }
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
            db.monsterServingDao().updateMonsterServing(
                MonsterServing(
                    servingId = servingMonster.servingId,
                    monsterId = newMonsterId!!,
                    serveStartTime = Instant.now(),
                    position = position,
                    environment = nextEnvironment
                )
            )
            // update monsters and evolutions
            setNewMonsterTags(newMonster, nextEnvironment)
            monsters[position] = newMonster
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
        monsters.forEach {
            val monsterId = it.monsterId
            val position = monsters.indexOf(it)
            val allServingMonsters = db.monsterServingDao().getAllServingsMonsterSorted()
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

    fun openEvolutionActivity(evolutionList: List<Int?>, position: Int = 0) {
        val intent = Intent(this, EvolutionActivity::class.java)
        intent.putExtra("EVOLUTION_LIST", evolutionList as Serializable)
        intent.putExtra("POSITION", position)
        evolutionResultLauncher.launch(intent)
    }

    private fun addNewMonsterToService(monster: Monster, position: Int, environment: MonsterType) {
        db.monsterServingDao().insertMonsterServing(
            MonsterServing(
                monsterId = monster.monsterId,
                serveStartTime = Instant.now(),
                environment = environment,
                position = position
            )
        )
    }

    private fun addNewEggToService(monster: Monster) {
        val allServingMonsters = db.monsterServingDao().getAllServingsMonster()
        db.monsterServingDao().insertMonsterServing(
            MonsterServing(
                monsterId = monster.monsterId,
                serveStartTime = Instant.now(),
                environment = monster.monsterType,
                position = allServingMonsters.size
            )
        )
    }

    private fun showAddButtonGuidance() {
        val addButton = findViewById<ImageView>(R.id.addButton) // 确保这里是正确的 ID
        addButton.post {
            addButtonSpotlight = buildSpotlightForButton(addButton)
            addButtonSpotlight!!.start() // 启动 Spotlight
        }
    }

    private fun buildSpotlightForButton(button: ImageView): Spotlight {
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
            .setOverlay(layoutInflater.inflate(R.layout.simple_overlay, null))
            .setOnTargetListener(object : OnTargetListener {
                override fun onStarted() {
                    Log.d("MainActivity", "Simple target is started")
                    Toast.makeText(this@MainActivity, "Simple target is started", Toast.LENGTH_SHORT).show()
                }

                override fun onEnded() {
                    Log.d("MainActivity", "Simple target is ended")
                    Toast.makeText(this@MainActivity, "Simple target is ended", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@MainActivity, "Simple Spotlight is started", Toast.LENGTH_SHORT).show()
                }

                override fun onEnded() {
                    Log.d("MainActivity", "Simple Spotlight is ended")
                    Toast.makeText(this@MainActivity, "Simple Spotlight is ended", Toast.LENGTH_SHORT).show()
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
                setClockButtonSpotlight = buildSpotlightForButton(targetButton)
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

        //if (BuildConfig.DEBUG) {
        //    // 清空資料庫或重置 SharedPreferences
        //    DatabaseProvider.clearDatabase(this)
        //    showAddButtonGuidance()
        //}

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

        // prepare evolution launcher
        evolutionActivityLauncher()

        // read serving monsters and add to gifList
        lifecycleScope.launch {
            var monsterServings = withContext(Dispatchers.IO) {
                db.monsterServingDao().getAllServingsMonster()
            }
            var monsters = withContext(Dispatchers.IO) {
                monsterServings.map {
                    db.monsterDao().getMonsterById(it.monsterId)
                }
            }
            println("servingMonsters: $monsters")

            // for testing
            // if length of servingMonsters is >3, then only keep the oldest 3
            if (monsters.size > 3) {
                val monstersSorted = monsters.sortedBy { it.monsterId }
                val monstersSortedKeep = monstersSorted.subList(monstersSorted.size - 3, monstersSorted.size)
                val monsterServingsSorted = monsterServings.sortedBy { it.servingId }
                val monsterServingsSortedKeep = monsterServingsSorted.subList(monsterServingsSorted.size - 3, monsterServingsSorted.size)

                println("monstersSortedKeep: $monstersSortedKeep")
                // delete all serving monsters
                withContext(Dispatchers.IO) {
                    db.monsterServingDao().deleteAll()
                }
                // add serving monsters back
                Thread {
                    for (i in monstersSortedKeep.indices) {
                        addNewMonsterToService(monstersSortedKeep[i], i, monsterServingsSortedKeep[i].environment!!)
                    }
                }.start()
                monsters = monstersSortedKeep
                monsterServings = monsterServingsSortedKeep
            }

            // recover monsters tags
            for (i in monsters.indices) {
                // count remaining seconds to continue countdown
                val timeDiffer = Instant.now().epochSecond - monsterServings[i].serveStartTime.epochSecond
                val remainingSeconds = monsters[i].evolveSeconds - timeDiffer
                monsters[i].evolveRemainingSeconds = remainingSeconds.toInt()
                // set environment
                monsters[i].servingType = monsterServings[i].environment!!
                // get evolution
                val evolution = withContext(Dispatchers.IO) {
                    db.evolutionDao().getEvolutionByMonsterId(monsters[i].monsterId)
                }
                evolutions.add(evolution!!)
            }
            // add to monsters
            for (monster in monsters) {

                this@MainActivity.monsters.add(monster)
            }
            // check if any monster reaches evolution time
            startPeriodicCheck()
            gifAdapter = GifAdapter(this@MainActivity.monsters, this@MainActivity, evolutions)
            recyclerView.adapter = gifAdapter
        }

        // set add button
        addButton.setOnClickListener {
            addButtonSpotlight?.finish()
            val dialog = EggSelectDialogFragment()
            dialog.listener = this
            dialog.show(supportFragmentManager, "EggSelectDialogFragment")
        }
    }

    override fun onResume() {
        super.onResume()
    }

    class GifAdapter(private val monsters: List<Monster>, private val context: Context, private var evolutions: List<Evolution>) :
        RecyclerView.Adapter<GifAdapter.GifViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GifViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.main_items, parent, false)
            return GifViewHolder(view, context)
        }

        override fun onBindViewHolder(holder: GifViewHolder, position: Int) {
            holder.bind(monsters[position], position, evolutions[position])
        }

        override fun getItemCount(): Int = monsters.size

        class GifViewHolder(itemView: View, private val context: Context) :
            RecyclerView.ViewHolder(itemView) {
            private val gifImageView: ImageView = itemView.findViewById(R.id.gifImageView)
            private val setClockButton: ImageView = itemView.findViewById(R.id.actionApplyClockButton)
            private val removeButton: ImageView = itemView.findViewById(R.id.actionDismissMonsterButton)
            private val showTypeImageView: ImageView = itemView.findViewById(R.id.showTypeImageView)
            private var countdownTimerTextView: TextView = itemView.findViewById(R.id.countdownTimerTextView)
            private var countdownTimer: CountDownTimer? = null

            private fun sendChangeMonsterBroadcast(monsterId: Int) {
                Log.d("MainActivity", "Sending broadcast to change monster: $monsterId")
                val intent = Intent(context, ClockWidget::class.java).apply {
                    action = ACTION_CHANGE_MONSTER
                    putExtra(CHANGE_MONSTER_ID, monsterId)
                }
                context.sendBroadcast(intent)
                Log.d("MainActivity", "CHANGE_MONSTER_ID Broadcast sent")
            }

            fun bind(monster: Monster, position:Int, evolution: Evolution) {
                val context = itemView.context
                val resourceId = context.resources.getIdentifier(monster.pixelGifName, "drawable", context.packageName)

                // show monster gif
                Glide.with(itemView.context)
                    .asGif()
                    .load(resourceId)
                    .into(gifImageView)
                // show showType and background
                when (monster.servingType) {
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
                if (monster.needEvolve) {
                    Glide.with(itemView.context)
                        .asGif()
                        .load(R.drawable.evolution_exclaimation)
                        .into(gifImageView)
                }

                // set onClickListener for gifImageView
                gifImageView.setOnClickListener {
                    if (!monster.needEvolve) {
                        val intent = Intent(context, GifDetailActivity::class.java)
                        val resourceId = context.resources.getIdentifier(monster.pixelFocusedGifName, "drawable", context.packageName)
                        intent.putExtra("FOCUSED_GIF_RESOURCE_ID", resourceId)
                        context.startActivity(intent)
                    } else {
                        // put Map<String,Int> to intent info
                        val evolutionList: List<Int?> = listOf(
                            evolution.morningEvolutionId,
                            evolution.balanceEvolutionId,
                            evolution.nightEvolutionId
                        )
                        println("evolutionMap: $evolutionList")
                        (context as MainActivity).openEvolutionActivity(evolutionList, position)
                    }
                }

                // set onClickListener for setClockButton
                setClockButton.setOnClickListener {
                    sendChangeMonsterBroadcast(monster.monsterId)
                    setClockButtonSpotlight?.finish()
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
                        (context as MainActivity).monsters.removeAt(position)
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
                    //// remove monster from database
                    //Thread {
                    //    (context as MainActivity).db.monsterServingDao().deleteMonsterServingByPosition(position)
                    //    // change remaining monsters' position
                    //    val remainingMonsters = context.db.monsterServingDao().getAllServingsMonsterSorted()
                    //    for (i in position until remainingMonsters.size) {
                    //        context.db.monsterServingDao().updateMonsterServing(
                    //            MonsterServing(
                    //                servingId = remainingMonsters[i].servingId,
                    //                monsterId = remainingMonsters[i].monsterId,
                    //                serveStartTime = remainingMonsters[i].serveStartTime,
                    //                environment = remainingMonsters[i].environment,
                    //                position = i
                    //            )
                    //        )
                    //    }
                    //}.start()
                    //// remove monster from monsters
                    //(context as MainActivity).monsters.removeAt(position)
                    //context.evolutions.removeAt(position)
                    //// notify adapter
                    //context.runOnUiThread {
                    //    context.gifAdapter.notifyItemRemoved(position)
                    //}
                }


                // set count down timer
                if (monster.needNewTimer) {
                    println("monster.needsTimerUpdate: ${monster.needNewTimer}")
                    countdownTimer?.cancel()
                    countdownTimer = null
                    monster.needNewTimer = false
                }
                if (countdownTimer == null) {
                    countdownTimer = object : CountDownTimer(monster.evolveRemainingSeconds.toLong()*1000, 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                            // 更新 TextView 的文本
                            val remainSeconds = (millisUntilFinished / 1000).toInt()
                            val hours = remainSeconds / 60 / 60
                            val minutes = (remainSeconds / 60) % 60
                            val seconds = remainSeconds % 60
                            countdownTimerTextView.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                            if (monster.servingType == MonsterType.NIGHT) {
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