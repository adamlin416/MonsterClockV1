package com.example.monsterclockv1

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.monsterclockv1.database.AppDatabase
import com.example.monsterclockv1.database.DatabaseProvider
import com.example.monsterclockv1.database.Evolution
import com.example.monsterclockv1.database.MonsterServing
import com.example.monsterclockv1.enums.MonsterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EvolutionActivity: AppCompatActivity()  {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_evolution)

        val db = DatabaseProvider.getDatabase(this)
        val evolution: Evolution = intent.getParcelableExtra("EVOLUTION")!!
        val position: Int = intent.getIntExtra("POSITION", 0)
        val servingMonster: MonsterServing = intent.getParcelableExtra("SERVING_MONSTER")!!

        // update gif in evolution page top evolved_monster_intro & evolved_monster_gif
        when (servingMonster.environment) {
            MonsterType.MORNING -> {
                val evolutionId = evolution.morningEvolutionId
                setEvolvedGif(db, evolutionId!!)
            }
            MonsterType.BALANCE -> {
                val evolutionId = evolution.balanceEvolutionId
                setEvolvedGif(db, evolutionId!!)
            }
            MonsterType.NIGHT -> {
                val evolutionId = evolution.nightEvolutionId
                setEvolvedGif(db, evolutionId!!)
            }
        }

        val container = findViewById<LinearLayout>(R.id.evolution_container)
        container.removeAllViews()
        // index of evolutionList: 0: morning, 1: balance, 3: night
        addImage(container, R.drawable.type_morning_gif, "MORNING", position)
        addImage(container, R.drawable.type_balance_gif, "BALANCE", position)
        addImage(container, R.drawable.type_night_gif, "NIGHT", position)
    }

    private fun addImage(container: LinearLayout, imageResource: Int, imageType: String, position:Int) {
        val imageView = ImageView(this)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.weight = 1f
        layoutParams.setMargins(16, 10, 16, 15)
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

    private fun setEvolvedGif(db: AppDatabase, evolutionId: Int) {
        val evolvedMonsterIntro: TextView = findViewById(R.id.evolved_monster_intro)
        val evolvedMonsterGif: ImageView = findViewById(R.id.evolved_monster_gif)
        lifecycleScope.launch(Dispatchers.IO) {
            val monster = db.monsterDao().getMonsterById(evolutionId!!)
            runOnUiThread {
                evolvedMonsterIntro.text = "Your monster has evolve to ...\n ${monster.monsterName}"
                Glide.with(this@EvolutionActivity)
                    .asGif()
                    .load(resources.getIdentifier(monster.pixelGifName, "drawable", packageName))
                    .into(evolvedMonsterGif)
            }
        }
    }

    private fun onImageClicked(nextEnv: String, position: Int) {
        val returnIntent = Intent()
        returnIntent.putExtra("NEXT_ENVIRONMENT", nextEnv)
        returnIntent.putExtra("POSITION", position)
        setResult(RESULT_OK, returnIntent)
        finish() // 结束当前Activity并返回到MainActivity
    }
}