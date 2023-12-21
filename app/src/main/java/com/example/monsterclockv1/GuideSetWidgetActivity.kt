package com.example.monsterclockv1

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class GuideSetWidgetActivity : AppCompatActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide_set_widget)

        // 获取传递的 GIF 资源 ID
        val images = listOf(R.drawable.set_widget_step1, R.drawable.set_widget_step2)
        val viewPager: ViewPager2 = findViewById(R.id.viewPager)
        val adapter = MyAdapter(images)
        viewPager.adapter = adapter
    }

    class MyAdapter(private val images: List<Int>) : RecyclerView.Adapter<MyAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.guidSetWidgetimageView)
            val backButton: Button = view.findViewById(R.id.guidSetWidgetbackButton)
            val pageText: TextView = view.findViewById(R.id.guidSetWidgetPage)
            val titleText: TextView = view.findViewById(R.id.guidSetWidgetTitle)
            val descriptionText: TextView = view.findViewById(R.id.guidSetWidgetDescription)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.guide_set_widget_items, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.imageView.setImageResource(images[position])
            holder.pageText.text = "${position + 1}/${images.size}"
            if (position == 0) {
                holder.titleText.text = "Set MonsterClock Widget \n Step ${position + 1}:"
                holder.descriptionText.text = "Long press the home screen and select \"Widgets\", Then search \"MonsterClock\" in the widget list and long tag the clock, pull to home screen.\""
            } else {
                holder.titleText.text = "Step ${position + 1}:"
                holder.descriptionText.text = "Done. You can modify the clock size once it's on your screen."
            }
            holder.backButton.visibility = if (position == images.size - 1) View.VISIBLE else View.GONE
            holder.backButton.setOnClickListener {
                (holder.itemView.context as Activity).finish()
            }
        }

        override fun getItemCount(): Int = images.size
    }
}