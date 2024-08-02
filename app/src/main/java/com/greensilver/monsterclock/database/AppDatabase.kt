package com.greensilver.monsterclock.database
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters


@Database(entities = [Monster::class, MonsterServing::class, Evolution::class], version = 1)
@TypeConverters(InstantConverter::class, MonsterTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monsterDao(): MonsterDao
    abstract fun monsterServingDao(): MonsterServingDao
    abstract fun evolutionDao(): EvolutionDao
}