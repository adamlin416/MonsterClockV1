package com.greensilver.monsterclock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.SkuDetailsParams
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.greensilver.monsterclock.database.AppDatabase
import com.greensilver.monsterclock.database.DatabaseProvider
import com.greensilver.monsterclock.database.Evolution
import com.greensilver.monsterclock.database.Monster
import com.greensilver.monsterclock.database.MonsterServing
import com.greensilver.monsterclock.enums.MonsterType
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

// test switch, remember to switch to all false before release
var testDeleteDB = false
var testFirstLaunch = false
var deactivateQualifiedUser = false

class MainActivity : AppCompatActivity(), EggSelectDialogFragment.GifSelectionListener, PayToUnlockHatcherDialogFragment.ChoosePayListener {
    private val activityMainResourceId = R.layout.activity_main_test2
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
    private var isAddButtonSpotlighted: Boolean = false
    private var needSpotlightOnSetClock: Boolean = false
    private var mRewardedAd: RewardedAd? = null
    private lateinit var billingClient: BillingClient
    private lateinit var sharedPref: SharedPreferences
    private var qualifiedUser: Boolean = false

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

    private fun attachOldMonsterServingTags(monsterServing: MonsterServing, monster: Monster) {
        monsterServing.needNewTimer = true
        monsterServing.pixelGifName = monster.pixelGifName
        monsterServing.monsterType = monster.monsterType
        monsterServing.evolveSeconds = monster.evolveSeconds
        // count remaining seconds to continue countdown
        val timeDiffer = Instant.now().epochSecond - monsterServing.serveStartTime.epochSecond
        val remainingSeconds = monsterServing.evolveSeconds - timeDiffer
        monsterServing.evolveRemainingSeconds = remainingSeconds.toInt()
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

    private fun showEggSelectDialog() {
        val dialog = EggSelectDialogFragment()
        dialog.listener = this
        dialog.show(supportFragmentManager, "EggSelectDialogFragment")
    }

    private fun showPayToUnlockHatcherDialog() {
        val dialog = PayToUnlockHatcherDialogFragment()
        dialog.listener = this
        dialog.show(supportFragmentManager, "PayToUnlockHatcherDialogFragment")
    }

    override fun onPaySelected() {
        val productDetailsParamsList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(getString(R.string.sku_skip_ad)) // 替換為您的產品ID
                .setProductType(BillingClient.ProductType.INAPP) // 或者 BillingClient.ProductType.SUBS
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productDetailsParamsList)
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                for (productDetails in productDetailsList) {
                    val flowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                        ))
                        .build()
                    billingClient.launchBillingFlow(this, flowParams)
                }
            }
        }
    }

    override fun onWatchAdSelected() {
        // Lead to Ad
        if (mRewardedAd != null) {
            mRewardedAd?.show(this, OnUserEarnedRewardListener { rewardItem ->
                // 使用者看完廣告，應獲得獎勵
                val rewardAmount = rewardItem.amount
                val rewardType = rewardItem.type
                println("User earned the reward: $rewardAmount $rewardType")
                qualifiedUser = false
                println("Watch Ad qualifiedUser: $qualifiedUser")
                // go to select egg dialog
                showEggSelectDialog()
                // 在這裡處理獎勵的邏輯（例如增加遊戲幣、解鎖功能等）
            })
        } else {
            println("The rewarded ad wasn't ready yet.")
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
            //println("allServingMonsters: $allServingMonsters")
            val servingMonster = db.monsterServingDao().getMonsterServingByPosition(position)
            val evolution = db.evolutionDao().getEvolutionByMonsterId(monsterId)
            val possibleEvolutions: Map<String,Int?> = mapOf(
                "morning" to evolution!!.morningEvolutionId,
                "balance" to evolution!!.balanceEvolutionId,
                "night" to evolution!!.nightEvolutionId
            )

            if (possibleEvolutions.values.any { it != null }) {
                // if time now - serveStartTime > 10sec, then evolve
                //println("servingMonster buggy: $servingMonster")
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
        println("newServing start time timeDiffer anchor: $newServing.serveStartTime")
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

    private fun loadRewardAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, getString(R.string.admob_rewarded_id), adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(rewardedAd: RewardedAd) {
                println("onAdLoaded")
                mRewardedAd = rewardedAd
                mRewardedAd?.fullScreenContentCallback = object: FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        // 廣告被關閉後重新加載
                        loadRewardAd()
                    }
                }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                println("onAdFailedToLoad ${loadAdError.message}")
                mRewardedAd = null
            }
        })
    }

    private fun loadBannerAd() {
        val adRequest = AdRequest.Builder().build()
        val adView = findViewById<AdView>(R.id.adView)

        adView.adListener = object: AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()
                // set guidance at first time launch
                if (firstLaunch && !isAddButtonSpotlighted) {
                    showAddButtonGuidance()
                    //with (sharedPref.edit()) {
                    //    putBoolean(getString(R.string.shared_pref_check_first_init), false)
                    //    commit()
                    //}
                }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                // still need to show guidance at first time launch
                if (firstLaunch && !isAddButtonSpotlighted) {
                    showAddButtonGuidance()
                    with (sharedPref.edit()) {
                        putBoolean(getString(R.string.shared_pref_check_first_init), false)
                        commit()
                    }
                }
                // 廣告加載失敗，調整 addButton 至底部
                adView.visibility = View.GONE
                moveAddButtonToBottom()
            }
        }
        adView.loadAd(adRequest)
    }

    private fun removeBannerAd() {
        val adView = findViewById<AdView>(R.id.adView)
        adView.visibility = View.GONE
    }
    private fun moveAddButtonToBottom() {
        // 廣告不加載，調整 addButton 至底部
        val constraintSet = ConstraintSet()
        val constraintLayout = findViewById<ConstraintLayout>(R.id.mainViewGroup)
        constraintSet.clone(constraintLayout)
        constraintSet.connect(R.id.addButton, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 15)
        constraintSet.applyTo(constraintLayout)
    }

    private fun checkUserQualification() {
        // check shared preferences
        qualifiedUser = sharedPref.getBoolean(getString(R.string.shared_pref_check_skip_ad), false)
        println("qualifiedUser check pref: $qualifiedUser")
        // check if user has paid
        val queryPurchasesParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(queryPurchasesParams) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    if (getString(R.string.shared_pref_check_skip_ad) in purchase.products && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        qualifiedUser = true
                        // write to encrypted shared preferences
                        with (sharedPref.edit()) {
                            putBoolean(getString(R.string.shared_pref_check_skip_ad), true)
                            commit()
                        }
                    } else {
                        qualifiedUser = false
                    }
                }
            }
        }
        // if zec user, then qualifiedUser = true
        val isZec = sharedPref.getBoolean(getString(R.string.shared_pref_zec), false)
        if (isZec) {
            qualifiedUser = true
        }

        // test
        if (deactivateQualifiedUser) {
            qualifiedUser = false
        }
    }

    private fun initEncryptedSharedPreferences() {
        // encrpyted shared preferences trigger error, deprecate for now
        try {
            //println("Start EncryptedPrefs")
            //val masterKey = MasterKey.Builder(this)
            //    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            //    .build()
            //println("Got masterKey: $masterKey")
            //
            //println("Start crate prefs")
            //sharedPref = EncryptedSharedPreferences.create(
            //    this,
            //    getString(R.string.shared_pref_name),
            //    masterKey,
            //    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            //    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            //)
            //println("Got sharedPref: $sharedPref")
            sharedPref = getSharedPreferences(
                getString(R.string.shared_pref_name),
                Context.MODE_PRIVATE
            )
        } catch (e: Exception) {
            println("EncryptedPrefs ${getString(R.string.shared_pref_name)}")
            Log.e("EncryptedPrefs", "Failed to initialize encrypted preferences: ${e.message}", e)
            e.printStackTrace()
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidThreeTen.init(this)
        db = DatabaseProvider.getDatabase(this)
        setContentView(activityMainResourceId)

        // get shared preferences
        initEncryptedSharedPreferences()
        firstLaunch = sharedPref.getBoolean(getString(R.string.shared_pref_check_first_init), true)

        // init billing client
        val purchaseUpdateListener = PurchasesUpdatedListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        // 購買成功，這裡處理購買後的邏輯
                        removeBannerAd()
                        showEggSelectDialog()
                        // write to encrypted shared preferences
                        qualifiedUser = true
                        with (sharedPref.edit()) {
                            putBoolean(getString(R.string.shared_pref_check_skip_ad), true)
                            commit()
                        }
                    }
                }
            } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                // 使用者取消購買
                showPayToUnlockHatcherDialog()
            } else {
                // 處理其他購買錯誤
                showPayToUnlockHatcherDialog()
                println("billingResult.responseCode: ${billingResult.responseCode}")
            }
        }
        billingClient = BillingClient.newBuilder(this)
            .setListener(purchaseUpdateListener)
            .enablePendingPurchases() // 必須啟用待處理購買
            .build()
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // 計費客戶端已就緒
                }
            }
            override fun onBillingServiceDisconnected() {
                // 計費服務中斷連接
            }
        })

        // init admob
        checkUserQualification()
        if (!qualifiedUser) {
            MobileAds.initialize(this) { }
            loadRewardAd()
            loadBannerAd()
        } else {
            moveAddButtonToBottom()
        }

        // test while developing
        if (BuildConfig.DEBUG && testDeleteDB) {
            // 清空資料庫或重置 SharedPreferences
            DatabaseProvider.clearDatabase(this)
            testDeleteDB = false
        }
        if (BuildConfig.DEBUG && testFirstLaunch) {
            firstLaunch = true
            testFirstLaunch = false
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

            // recover monsters tags
            for (i in monsterServings.indices) {
                // get monster
                val monster = withContext(Dispatchers.IO) {
                    db.monsterDao().getMonsterById(monsterServings[i].monsterId)
                }
                // set tags
                attachOldMonsterServingTags(monsterServings[i], monster)
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
            with (sharedPref.edit()) {
                putBoolean(getString(R.string.shared_pref_check_first_init), false)
                commit()
            }
            if (addButtonSpotlight != null) {
                addButtonSpotlight?.finish()
                addButtonSpotlight = null
                isAddButtonSpotlighted = true
            }
            if (qualifiedUser || monsterServingList.size < 1) {
                showEggSelectDialog()
            } else {
                // show warning to IPA to unlock new column
                showPayToUnlockHatcherDialog()
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

            val view = LayoutInflater.from(parent.context).inflate(R.layout.main_items_test2, parent, false)
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
                println("monster position: $position, monsterId: ${monsterServing.servingId}")
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
                        gifImageView.setBackgroundResource(R.drawable.border_style_sharp_edge)
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
                        println("monster position removed: $position")
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
                //if (monsterServing.needNewTimer) {
                //    println("monster.needsTimerUpdate: ${monsterServing.needNewTimer}")
                //    countdownTimer?.cancel()
                //    countdownTimer = null
                //    monsterServing.needNewTimer = false
                //}
                countdownTimer?.cancel()
                val timeDiffer = Instant.now().epochSecond - monsterServing.serveStartTime.epochSecond
                val remainingSeconds = if (monsterServing.evolveSeconds - timeDiffer > 0) {
                    monsterServing.evolveSeconds - timeDiffer
                } else {
                    0
                }
                //println("timeDiffer remainingSeconds: $remainingSeconds, position: $position, monsterId: ${monsterServing.servingId}")
                if (monsterServing.environment == MonsterType.NIGHT) {
                    countdownTimerTextView.setTextColor(Color.argb(255, 255, 255, 255))
                }
                countdownTimer = object : CountDownTimer(remainingSeconds*1000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        // 更新 TextView 的文本
                        val remainSeconds = (millisUntilFinished / 1000).toInt()
                        val hours = remainSeconds / 60 / 60
                        val minutes = (remainSeconds / 60) % 60
                        val seconds = remainSeconds % 60
                        countdownTimerTextView.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                        //println("countdownTimerTextView.text: ${countdownTimerTextView.text}")
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