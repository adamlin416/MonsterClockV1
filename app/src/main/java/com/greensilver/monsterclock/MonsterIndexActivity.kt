package com.greensilver.monsterclock
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.greensilver.monsterclock.database.DatabaseProvider
import com.greensilver.monsterclock.database.Monster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GridSpacingItemDecoration(private val spanCount: Int, private val spacing: Int, private val includeEdge: Boolean) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view) // item position
        val column = position % spanCount // item column

        if (includeEdge) {
            outRect.left = spacing - column * spacing / spanCount
            outRect.right = (column + 1) * spacing / spanCount

            if (position < spanCount) { // top edge
                outRect.top = spacing
            }
            outRect.bottom = spacing // item bottom
        } else {
            outRect.left = column * spacing / spanCount
            outRect.right = spacing - (column + 1) * spacing / spanCount
            if (position >= spanCount) {
                outRect.top = spacing // item top
            }
        }
    }
}

class MonsterIndexActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monster_index)

        val recyclerView = findViewById<RecyclerView>(R.id.monsterIndexRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this@MonsterIndexActivity,3)
        val spacingInPixels = resources.getDimensionPixelSize(R.dimen.recycler_view_spacing)
        recyclerView.addItemDecoration(GridSpacingItemDecoration(3, spacingInPixels, true))


        val db = DatabaseProvider.getDatabase(this)
        // io search db
        lifecycleScope.launch(Dispatchers.IO) {
            val monsterList = db.monsterDao().getAllMonsters()
            println("monsterList: $monsterList")
            recyclerView.adapter = MonsterAdapter(this@MonsterIndexActivity, monsterList)
            runOnUiThread {
                recyclerView.adapter?.notifyDataSetChanged()
            }
        }
    }


    class MonsterAdapter(private val context: Context, private val monsterList: List<Monster>) : RecyclerView.Adapter<MonsterAdapter.MonsterViewHolder>() {
        class MonsterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.indexImageView)
            val indexNumber:TextView = view.findViewById(R.id.indexNumber)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonsterViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.monster_index_item, parent, false)
            return MonsterViewHolder(view)
        }
        override fun onBindViewHolder(holder: MonsterViewHolder, position: Int) {
            println("monsterList.size: ${monsterList.size}")
            val monster = monsterList[position]
            println("binding monster: $monster")
            // 假設您有一個方法來獲取圖片資源ID，比如根據 monster 的屬性
            val imageResourceId = context.resources.getIdentifier(monster.pixelFocusedGifName, "drawable", context.packageName)
            holder.indexNumber.text = "No.${monster.monsterId}"
            if (!monster.isDiscovered) {
                holder.imageView.setImageResource(R.drawable.unknown_egg_q)
            } else {
                holder.imageView.setImageResource(imageResourceId)
                holder.imageView.setOnClickListener(){
                    val intent = Intent(context, GifDetailActivity::class.java)
                    intent.putExtra("GIF_RESOURCE_ID", imageResourceId)
                    intent.putExtra("MONSTER_ID", monster.monsterId)
                    context.startActivity(intent)
                }
            }
            //holder.imageView.setImageResource(R.drawable.monster_morning_egg_focused)
        }
        override fun getItemCount() = monsterList.size
    }
}