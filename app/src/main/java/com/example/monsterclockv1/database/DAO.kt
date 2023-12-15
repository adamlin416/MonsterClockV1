package com.example.monsterclockv1.database
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MonsterDao {
    @Insert
    fun insertMonsters(monsters: List<Monster>)

    @Query("SELECT * FROM monster")
    fun getAllMonsters(): List<Monster>

    @Query("SELECT * FROM monster WHERE monsterId = :monsterId")
    fun getMonsterById(monsterId: Int): Monster

    @Query("SELECT * FROM monster WHERE monsterName = :monsterName")
    fun getMonsterByName(monsterName: String): Monster

    @Query("UPDATE monster SET isDiscovered = 1 WHERE monsterId = :id")
    fun setMonsterDiscoveredById(id: Int)

    @Delete
    fun deleteMonster(monster: Monster)
}

@Dao
interface MonsterServingDao {
    @Insert
    fun insertMonsterServing(monsterServing: MonsterServing)

    @Query("SELECT * FROM monster_serving WHERE monsterId = :monsterId")
    fun getMonsterServingByMonsterId(monsterId: Int): MonsterServing

    @Query("SELECT * FROM monster_serving")
    fun getAllServingsMonster(): List<MonsterServing>

    @Query("SELECT * FROM monster_serving ORDER BY position ASC")
    fun getAllServingsMonsterSorted(): List<MonsterServing>

    @Query("SELECT * FROM monster_serving WHERE position = :position")
    fun getMonsterServingByPosition(position: Int): MonsterServing

    @Update
    fun updateMonsterServing(monsterServing: MonsterServing)

    @Delete
    fun deleteMonsterServing(monsterServing: MonsterServing)

    @Query("DELETE FROM monster_serving")
    fun deleteAll()

    @Query("DELETE FROM monster_serving WHERE position = :position")
    fun deleteMonsterServingByPosition(position: Int)
}

@Dao
interface EvolutionDao {
    @Insert
    fun insertEvolutions(evolutions: List<Evolution>)

    @Query("SELECT * FROM evolution")
    fun getAllEvolutions(): List<Evolution>

    @Query("SELECT * FROM evolution WHERE monsterId = :monsterId")
    fun getEvolutionByMonsterId(monsterId: Int): Evolution

    @Delete
    fun deleteEvolution(evolution: Evolution)
}