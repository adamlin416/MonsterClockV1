package com.example.monsterclockv1.enums

enum class MonsterType {
    MORNING,
    BALANCE,
    NIGHT;

    override fun toString(): String {
        return name.lowercase()
    }

    companion object {
        fun fromString(value: String): MonsterType {
            return valueOf(value.uppercase())
        }
    }
}