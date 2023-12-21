package com.example.monsterclockv1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.monsterclockv1.database.DatabaseProvider
import com.example.monsterclockv1.database.Monster
import com.example.monsterclockv1.database.Evolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PayToUnlockHatcherDialogFragment: DialogFragment() {
    var listener: ChoosePayListener? = null

    interface ChoosePayListener {
        fun onPaySelected()
        fun onWatchAdSelected()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.pay_to_unlock_hatcher, container, false)
        val unknownEggImageView = view.findViewById<ImageView>(R.id.unknownEggImageView)
        Glide.with(view.context)
            .asGif()
            .load(R.drawable.unknown_egg)
            .into(unknownEggImageView)
        // watch ad
        view.findViewById<Button>(R.id.watchAdButton).setOnClickListener {
            listener?.onWatchAdSelected()
            dismiss()
        }
        // pay to unlock
        view.findViewById<Button>(R.id.payToUnlockButton).setOnClickListener {
            listener?.onPaySelected()
            dismiss()
        }
        // no thanks
        view.findViewById<Button>(R.id.cancelPayToUnlockButton).setOnClickListener {
            dismiss()
        }
        return view
    }
    
}