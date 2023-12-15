package com.example.monsterclockv1.database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.monsterclockv1.enums.MonsterType
import org.threeten.bp.Instant

@Entity(tableName = "monster")
data class Monster(
    @PrimaryKey(autoGenerate = true)
    val monsterId: Int = 0,
    val monsterName: String,
    val monsterType: MonsterType,
    val monsterLevel: Int,
    val pixelGifName: String, // find this name in res/drawable
    val pixelFocusedGifName: String, // find this name in res/drawable
    val clockName: String,
    val isDiscovered: Boolean = false,
    val evolveSeconds: Int,
    //@Ignore
    //var needNewTimer: Boolean = false,
    //@Ignore
    //var needEvolve: Boolean = false,
    //@Ignore
    //var servingType: MonsterType = MonsterType.MORNING,
    //@Ignore
    //var evolveRemainingSeconds: Int = 0
) {
    @Ignore
    var needNewTimer: Boolean = false
    @Ignore
    var needEvolve: Boolean = false
    @Ignore
    var servingType: MonsterType = MonsterType.MORNING
    @Ignore
    var evolveRemainingSeconds: Int = 0
}
//{
//    // 显式定义构造函数，仅包含数据库中的字段
//    constructor(
//        monsterId: Int,
//        monsterName: String,
//        monsterType: MonsterType,
//        monsterLevel: Int,
//        pixelGifName: String,
//        pixelFocusedGifName: String,
//        clockName: String,
//        isDiscovered: Boolean,
//        evolveSeconds: Int
//    ) : this(
//        monsterId,
//        monsterName,
//        monsterType,
//        monsterLevel,
//        pixelGifName,
//        pixelFocusedGifName,
//        clockName,
//        isDiscovered,
//        evolveSeconds,
//        false,
//        false,
//        MonsterType.MORNING,
//        0// 为 @Ignore 字段设置默认值
//    )
//}

@Entity(
    tableName = "monster_serving",
    foreignKeys = [
        ForeignKey(
            entity = Monster::class,
            parentColumns = ["monsterId"],
            childColumns = ["monsterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["monsterId"])]
)
data class MonsterServing(
    @PrimaryKey(autoGenerate = true)
    val servingId: Int = 0,
    val monsterId: Int,
    val position: Int = 0,
    val serveStartTime: Instant,
    val environment: MonsterType,
)

@Entity(
    tableName = "evolution",
    foreignKeys = [
        ForeignKey(
            entity = Monster::class,
            parentColumns = ["monsterId"],
            childColumns = ["monsterId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Monster::class,
            parentColumns = ["monsterId"],
            childColumns = ["morningEvolutionId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Monster::class,
            parentColumns = ["monsterId"],
            childColumns = ["balanceEvolutionId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Monster::class,
            parentColumns = ["monsterId"],
            childColumns = ["nightEvolutionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["monsterId"]), Index(value = ["morningEvolutionId"]), Index(value = ["balanceEvolutionId"]), Index(value = ["nightEvolutionId"])]
)
data class Evolution(
    @PrimaryKey(autoGenerate = true)
    val evolutionId: Int = 0,
    val monsterId: Int,
    val morningEvolutionId: Int?,
    val balanceEvolutionId: Int?,
    val nightEvolutionId: Int?
)