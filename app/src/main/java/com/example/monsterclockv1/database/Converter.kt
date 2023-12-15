package com.example.monsterclockv1.database

import androidx.room.TypeConverter
import com.example.monsterclockv1.enums.MonsterType
import org.threeten.bp.Instant

class InstantConverter {
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }

    @TypeConverter
    fun toInstant(millisSinceEpoch: Long?): Instant? {
        return millisSinceEpoch?.let { Instant.ofEpochMilli(it) }
    }
}

class MonsterTypeConverter {
    @TypeConverter
    fun fromMonsterType(value: MonsterType): String {
        return value.toString()
    }

    @TypeConverter
    fun toMonsterType(value: String): MonsterType {
        return MonsterType.fromString(value)
    }
}