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

    // Migration from 1 to 2, add evolution table
    //private val MIGRATION_1_2 = object : Migration(1, 2) {
    //    override fun migrate(database: SupportSQLiteDatabase) {
    //        database.execSQL(
    //            """
    //                CREATE TABLE evolution (
    //                    evolutionId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    //                    monsterId INTEGER NOT NULL,
    //                    morningEvolutionId INTEGER,
    //                    balanceEvolutionId INTEGER,
    //                    nightEvolutionId INTEGER,
    //                    FOREIGN KEY(monsterId) REFERENCES monster(monsterId) ON DELETE CASCADE,
    //                    FOREIGN KEY(morningEvolutionId) REFERENCES monster(monsterId) ON DELETE SET NULL,
    //                    FOREIGN KEY(balanceEvolutionId) REFERENCES monster(monsterId) ON DELETE SET NULL,
    //                    FOREIGN KEY(nightEvolutionId) REFERENCES monster(monsterId) ON DELETE SET NULL,
    //                )
    //            """.trimIndent()
    //        )
    //        database.execSQL("CREATE INDEX IF NOT EXISTS index_evolution_monsterId ON evolution (monsterId)")
    //        database.execSQL("CREATE INDEX IF NOT EXISTS index_evolution_morningEvolutionId ON evolution (morningEvolutionId)")
    //        database.execSQL("CREATE INDEX IF NOT EXISTS index_evolution_balanceEvolutionId ON evolution (balanceEvolutionId)")
    //        database.execSQL("CREATE INDEX IF NOT EXISTS index_evolution_nightEvolutionId ON evolution (nightEvolutionId)")
    //    }
    //}
    //
    //private val MIGRATION_2_3 = object : Migration(2, 3) {
    //    override fun migrate(database: SupportSQLiteDatabase) {
    //        // add column monsterLevel to monster table
    //        database.execSQL("ALTER TABLE monster ADD COLUMN monsterLevel INTEGER")
    //        // add column pixelFocusedGifName to monster table
    //        database.execSQL("ALTER TABLE monster ADD COLUMN pixelFocusedGifName TEXT")
    //        // add column clockName to monster table
    //        database.execSQL("ALTER TABLE monster ADD COLUMN monsterType TEXT")
    //        //database.execSQL("ALTER TABLE monster ADD COLUMN clockName TEXT NOT NULL DEFAULT 'clock_morning_egg'")
    //        // evolveSeconds=Column{name='evolveSeconds', type='INTEGER', affinity='3', notNull=true, primaryKeyPosition=0, defaultValue='undefined'},
    //        database.execSQL("ALTER TABLE monster ADD COLUMN evolveSeconds INTEGER NOT NULL DEFAULT undefined")
    //        database.execSQL("ALTER TABLE monster ADD COLUMN isDiscovered INTEGER NOT NULL DEFAULT 0")
    //        database.execSQL("ALTER TABLE monster_serving ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
    //        database.execSQL("ALTER TABLE monster_serving ADD COLUMN environment TEXT NOT NULL DEFAULT undefined")
    //
    //
    //    }
    //}

    fun getDatabase(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "monster_clock_database_redo4"
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
                            evolveSeconds = currentMonster.evolveSeconds
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
            context.deleteDatabase("monster_clock_database_redo4")
        }
    }
}