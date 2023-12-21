package com.example.monsterclockv1.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.monsterclockv1.enums.MonsterType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.Queue

class MonsterTreeProvider(private val context: Context) {
    data class MonsterTree(
        val monsterId: Int,
        val monsterName: String,
        val monsterType: String,
        val monsterLevel: Int,
        val pixelGifName: String,
        val pixelFocusedGifName: String,
        val clockName: String,
        val evolveSeconds: Int,
        val story: String,
        val evolutions: List<MonsterTree>?
    )

    fun getMonsterTreeList(): List<MonsterTree> {
        val jsonString = readMonsterTreeFromAssets()
        return parseMonstersJson(jsonString)
    }

    private fun readMonsterTreeFromAssets(): String {
        return context.assets.open("monster_tree.json").bufferedReader().use { it.readText() }
    }

    private fun parseMonstersJson(jsonString: String): List<MonsterTree> {
        val gson = Gson()
        val listType = object : TypeToken<List<MonsterTree>>() {}.type
        return gson.fromJson(jsonString, listType)
    }

}


object DatabaseProvider {
    @Volatile
    private var instance: AppDatabase? = null
    private val dbName: String = "monster_clock_database_redo6"

    fun getDatabase(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                dbName
            ).addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // initiate database
                    CoroutineScope(Dispatchers.IO).launch {
                        initDatabase(instance!!, context)
                    }
                }
            }).build()
            this.instance = instance
            // 返回实例
            instance
        }
    }

    fun initDatabase(instance: AppDatabase, context: Context) {
        // 初始化数据库 List<Monster>
        val monsterTreeList = MonsterTreeProvider(context).getMonsterTreeList()

        fun insertMonstersAndEvolutions(monsterTreeList: List<MonsterTreeProvider.MonsterTree>) {
            val monsterQueue: Queue<MonsterTreeProvider.MonsterTree> = LinkedList()
            val evolutionQueue: Queue<MonsterTreeProvider.MonsterTree> = LinkedList()
            monsterQueue.addAll(monsterTreeList)
            evolutionQueue.addAll(monsterTreeList)

            // insert monsters first, so that foreign key constraint can be satisfied
            while (monsterQueue.isNotEmpty()) {
                val currentMonster = monsterQueue.remove()

                // 插入当前 Monster 到数据库
                instance.monsterDao().insertMonsters(
                    listOf(
                        Monster(
                            monsterId = currentMonster.monsterId,
                            monsterName = currentMonster.monsterName,
                            monsterType = MonsterType.valueOf(currentMonster.monsterType.toUpperCase()),
                            monsterLevel = currentMonster.monsterLevel,
                            pixelGifName = currentMonster.pixelGifName,
                            pixelFocusedGifName = currentMonster.pixelFocusedGifName,
                            clockName = currentMonster.clockName,
                            evolveSeconds = currentMonster.evolveSeconds,
                            story = currentMonster.story
                        )
                    )
                )

                currentMonster.evolutions?.forEach { evolution ->
                    evolutionQueue.add(evolution)
                    monsterQueue.add(evolution)
                }
            }

            // insert evolutions
            for (monster in evolutionQueue) {
                var morningEvolutionId: Int? = null
                var balanceEvolutionId: Int? = null
                var nightEvolutionId: Int? = null
                if (monster.evolutions != null) {
                    if (monster.evolutions.size == 1) {
                        morningEvolutionId = if (monster.monsterType == "morning") monster.evolutions[0].monsterId else null
                        balanceEvolutionId = if (monster.monsterType == "balance") monster.evolutions[0].monsterId else null
                        nightEvolutionId = if (monster.monsterType == "night") monster.evolutions[0].monsterId else null
                    } else {
                        morningEvolutionId = monster.evolutions[0].monsterId
                        balanceEvolutionId = monster.evolutions[1].monsterId
                        nightEvolutionId = monster.evolutions[2].monsterId
                    }
                }
                instance.evolutionDao().insertEvolutions(
                    listOf(
                        Evolution(
                            monsterId = monster.monsterId,
                            morningEvolutionId = morningEvolutionId,
                            balanceEvolutionId = balanceEvolutionId,
                            nightEvolutionId = nightEvolutionId
                        )
                    )
                )
            }
        }

        insertMonstersAndEvolutions(monsterTreeList)
    }

    fun clearDatabase(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            context.deleteDatabase(dbName)
        }
    }
}