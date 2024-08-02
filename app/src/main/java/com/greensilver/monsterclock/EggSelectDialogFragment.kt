package com.greensilver.monsterclock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.greensilver.monsterclock.database.AppDatabase
import com.greensilver.monsterclock.database.DatabaseProvider
import com.greensilver.monsterclock.database.Monster
import com.greensilver.monsterclock.database.Evolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EggSelectDialogFragment: DialogFragment() {

    var listener: GifSelectionListener? = null

    interface GifSelectionListener {
        fun onEggSelected(monster: Monster, evolution: Evolution)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.egg_select, container, false)
        val db = DatabaseProvider.getDatabase(requireContext())

        val gifView1: ImageView = view.findViewById(R.id.eggSelectImageView1)
        val gifView2: ImageView = view.findViewById(R.id.eggSelectImageView2)
        val gifView3: ImageView = view.findViewById(R.id.eggSelectImageView3)

        // find monster and update GIF
        lifecycleScope.launch(Dispatchers.IO) {
            val monster1 = setGifViews(db, gifView1)
            val monster2 = setGifViews(db, gifView2)
            val monster3 = setGifViews(db, gifView3)

            // set listener
            setupImageViewClickListener(gifView1, monster1.first, monster1.second)
            setupImageViewClickListener(gifView2, monster2.first, monster2.second)
            setupImageViewClickListener(gifView3, monster3.first, monster3.second)
        }


        //setupImageViewClickListener(view.findViewById(R.id.eggSelectImageView1))
        //setupImageViewClickListener(view.findViewById(R.id.eggSelectImageView2))
        //setupImageViewClickListener(view.findViewById(R.id.eggSelectImageView3))

        return view
    }

    private fun setGifViews(db:AppDatabase, gifView:ImageView): Pair<Monster, Evolution> {
        val monster = db.monsterDao().getMonsterByName(gifView.tag.toString())
        val evolution = db.evolutionDao().getEvolutionByMonsterId(monster.monsterId)
        val resourceId = requireContext().resources.getIdentifier(monster.pixelFocusedGifName, "drawable", requireContext().packageName)
        lifecycleScope.launch(Dispatchers.Main) {
            Glide.with(requireContext())
                .asGif()
                .load(resourceId)
                .into(gifView)
        }
        return Pair(monster, evolution)
    }

    private fun setupImageViewClickListener(imageView:ImageView, monster: Monster, evolution: Evolution) {
        imageView.setOnClickListener {
            listener?.onEggSelected(monster, evolution)
            dismiss()  // 注意：dismiss 应该在主线程上调用
        }
    }
}