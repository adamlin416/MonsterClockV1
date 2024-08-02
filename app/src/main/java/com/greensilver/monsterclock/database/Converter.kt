package com.greensilver.monsterclock.database

import androidx.room.TypeConverter
import com.greensilver.monsterclock.enums.MonsterType
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