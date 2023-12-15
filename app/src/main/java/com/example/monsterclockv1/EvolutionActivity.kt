package com.example.monsterclockv1

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class EvolutionActivity: AppCompatActivity()  {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_evolution)

        val evolutionList: List<Int?>? = intent.getSerializableExtra("EVOLUTION_LIST") as List<Int?>?
        val position: Int = intent.getIntExtra("POSITION", 0)
        if (evolutionList != null) {
            val container = findViewById<LinearLayout>(R.id.evolution_container)
            container.removeAllViews()
            // index of evolutionList: 0: morning, 1: balance, 3: night
            for (i in evolutionList.indices) {
                when (i) {
                    0 -> addImage(container, R.drawable.type_morning_gif, "MORNING", position)
                    1 -> addImage(container, R.drawable.type_balance_gif, "BALANCE", position)
                    2 -> addImage(container, R.drawable.type_night_gif, "NIGHT", position)
                }
            }
        }
    }

    private fun addImage(container: LinearLayout, imageResource: Int, imageType: String, position:Int) {
        val imageView = ImageView(this)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.weight = 1f
        layoutParams.setMargins(10, 10, 15, 15)
        imageView.layoutParams = layoutParams
        when (imageType) {
            "MORNING" -> imageView.setBackgroundResource(R.drawable.border_style_show_morning_type)
            "BALANCE" -> imageView.setBackgroundResource(R.drawable.border_style_show_balance_type)
            "NIGHT" -> imageView.setBackgroundResource(R.drawable.border_style_show_night_type)
        }

        imageView.setPadding(15, 15, 15, 15)
        //imageView.setImageResource(imageResource) // 设置图片资源
        Glide.with(this)
            .asGif()
            .load(imageResource)
            .into(imageView)
        imageView.setOnClickListener {
            onImageClicked(imageType, position)
        }
        container.addView(imageView) // 添加到容器
    }

    private fun onImageClicked(nextEnv: String, position: Int) {
        val returnIntent = Intent()
        returnIntent.putExtra("NEXT_ENVIRONMENT", nextEnv)
        returnIntent.putExtra("POSITION", position)
        setResult(RESULT_OK, returnIntent)
        finish() // 结束当前Activity并返回到MainActivity
    }
}