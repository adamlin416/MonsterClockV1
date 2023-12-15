package com.example.monsterclockv1

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class GifDetailActivity : AppCompatActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_focused_gif)

        // 获取传递的 GIF 资源 ID
        val gifResourceId = intent.getIntExtra("GIF_RESOURCE_ID", 0)
        val focusedGifView: ImageView = findViewById(R.id.focusedGifImageView)
        val focusedGifImageBackButton: Button = findViewById(R.id.focusedGifImageBackButton)

        // 使用 Glide 加载 GIF
        Glide.with(this)
            .asGif()
            .load(gifResourceId)
            .into(focusedGifView)

        // 设置返回按钮的点击事件
        focusedGifImageBackButton.setOnClickListener {
            finish() // 结束当前 Activity，返回上一个 Activity
        }
    }
}