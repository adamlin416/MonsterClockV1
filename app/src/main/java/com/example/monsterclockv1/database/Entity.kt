package com.example.monsterclockv1.database
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.monsterclockv1.enums.MonsterType
import kotlinx.parcelize.Parcelize
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
    val story: String,
)

@Parcelize
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
): Parcelable {
    @Ignore
    var needNewTimer: Boolean = false
    @Ignore
    var needEvolve: Boolean = false
    @Ignore
    var evolveRemainingSeconds: Int = 0
    @Ignore
    lateinit var pixelGifName: String
    @Ignore
    lateinit var monsterType: MonsterType
    @Ignore
    var evolveSeconds: Int = 0
}

@Parcelize
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
): Parcelable