package com.example.monsterclockv1

enum class MonsterToClockDrawables(val id: Int, val objects: Map<Int, Int>) {
    MONSTER_TYPE_1(1, mapOf(
        0 to R.drawable.monster_morning_egg_clock_0,
        1 to R.drawable.monster_morning_egg_clock_1,
        2 to R.drawable.monster_morning_egg_clock_2,
        3 to R.drawable.monster_morning_egg_clock_3,
        4 to R.drawable.monster_morning_egg_clock_4,
        5 to R.drawable.monster_morning_egg_clock_5,
        6 to R.drawable.monster_morning_egg_clock_6,
        7 to R.drawable.monster_morning_egg_clock_7,
        8 to R.drawable.monster_morning_egg_clock_8,
        9 to R.drawable.monster_morning_egg_clock_9,
        999 to R.drawable.monster_morning_egg_clock_colon
    )),
    MONSTER_TYPE_4(4, mapOf(
        0 to R.drawable.monster_seedy,
        1 to R.drawable.monster_seedy,
        2 to R.drawable.monster_seedy,
        3 to R.drawable.monster_seedy,
        4 to R.drawable.monster_seedy,
        5 to R.drawable.monster_seedy,
        6 to R.drawable.monster_seedy,
        7 to R.drawable.monster_seedy,
        8 to R.drawable.monster_seedy,
        9 to R.drawable.monster_seedy,
        999 to R.drawable.monster_seedy
    )),
    MONSTER_TYPE_8(8, mapOf(
        0 to R.drawable.monster_nepenthes,
        1 to R.drawable.monster_nepenthes,
        2 to R.drawable.monster_nepenthes,
        3 to R.drawable.monster_nepenthes,
        4 to R.drawable.monster_nepenthes,
        5 to R.drawable.monster_nepenthes,
        6 to R.drawable.monster_nepenthes,
        7 to R.drawable.monster_nepenthes,
        8 to R.drawable.monster_nepenthes,
        9 to R.drawable.monster_nepenthes,
        999 to R.drawable.monster_nepenthes
    ));
    // 如果有更多的怪物類型，可以繼續添加

    companion object {
        private val map = values().associateBy(MonsterToClockDrawables::id)
        fun getObjectsById(id: Int): Map<Int, Int>? {
            return map[id]?.objects
        }
    }
}