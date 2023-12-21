package com.example.monsterclockv1

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.monsterclockv1.database.DatabaseProvider
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