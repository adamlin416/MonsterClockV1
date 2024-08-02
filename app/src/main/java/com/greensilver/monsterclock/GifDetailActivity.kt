package com.greensilver.monsterclock

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.greensilver.monsterclock.database.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GifDetailActivity : AppCompatActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_focused_gif)

        // 获取传递的 GIF 资源 ID
        val gifResourceId = intent.getIntExtra("GIF_RESOURCE_ID", 0)
        val monsterId = intent.getIntExtra("MONSTER_ID", 0)
        println("GifDetailActivity: gifResourceId = $gifResourceId")
        val focusedGifView: ImageView = findViewById(R.id.focusedGifImageView)
        val focusedGifImageBackButton: Button = findViewById(R.id.focusedGifImageBackButton)
        val focusedGifSetClockButton: ImageView = findViewById(R.id.focusedGifSetClockButton)
        val focusedGifImageTextView: TextView = findViewById(R.id.focusedGifImageTextView)

        // get corresponding monster number image
        val monsterMap = MonsterToClockDrawables.getObjectsById(monsterId)
        val focusedNumber0: ImageView = findViewById(R.id.focusedNumber0)
        val focusedNumber1: ImageView = findViewById(R.id.focusedNumber1)
        val focusedNumber2: ImageView = findViewById(R.id.focusedNumber2)
        val focusedNumber3: ImageView = findViewById(R.id.focusedNumber3)
        val focusedNumber4: ImageView = findViewById(R.id.focusedNumber4)
        val focusedNumber5: ImageView = findViewById(R.id.focusedNumber5)
        val focusedNumber6: ImageView = findViewById(R.id.focusedNumber6)
        val focusedNumber7: ImageView = findViewById(R.id.focusedNumber7)
        val focusedNumber8: ImageView = findViewById(R.id.focusedNumber8)
        val focusedNumber9: ImageView = findViewById(R.id.focusedNumber9)
        for (i in 0..9) {
            val resId = monsterMap?.get(i)
            when (i) {
                0 -> focusedNumber0.setImageResource(resId?:R.drawable.monster_morning_egg_clock_0)
                1 -> focusedNumber1.setImageResource(resId?:R.drawable.monster_morning_egg_clock_1)
                2 -> focusedNumber2.setImageResource(resId?:R.drawable.monster_morning_egg_clock_2)
                3 -> focusedNumber3.setImageResource(resId?:R.drawable.monster_morning_egg_clock_3)
                4 -> focusedNumber4.setImageResource(resId?:R.drawable.monster_morning_egg_clock_4)
                5 -> focusedNumber5.setImageResource(resId?:R.drawable.monster_morning_egg_clock_5)
                6 -> focusedNumber6.setImageResource(resId?:R.drawable.monster_morning_egg_clock_6)
                7 -> focusedNumber7.setImageResource(resId?:R.drawable.monster_morning_egg_clock_7)
                8 -> focusedNumber8.setImageResource(resId?:R.drawable.monster_morning_egg_clock_8)
                9 -> focusedNumber9.setImageResource(resId?:R.drawable.monster_morning_egg_clock_9)
            }
        }


        // monster story
        lifecycleScope.launch(Dispatchers.IO) {
            val db = DatabaseProvider.getDatabase(this@GifDetailActivity)
            val monster = db.monsterDao().getMonsterById(monsterId)
            focusedGifImageTextView.text = monster.story
            runOnUiThread {
                focusedGifImageTextView.text = "Monster Story: ${monster.story}"
            }
        }

        // 使用 Glide 加载 GIF
        Glide.with(this)
            .asGif()
            .load(gifResourceId)
            .into(focusedGifView)

        // 设置返回按钮的点击事件
        focusedGifImageBackButton.setOnClickListener {
            finish() // 结束当前 Activity，返回上一个 Activity
        }

        // 设置设置闹钟按钮的点击事件
        focusedGifSetClockButton.setOnClickListener {
            MainActivity.sendChangeMonsterBroadcast(monsterId, this)
            // pop warning
            Toast.makeText(this, "Monster clock has set to this monster's style.", Toast.LENGTH_SHORT).show()
            if (MainActivity.setClockButtonSpotlight != null) {
                MainActivity.setClockButtonSpotlight?.finish()
                MainActivity.setClockButtonSpotlight = null
            }
        }
    }
}