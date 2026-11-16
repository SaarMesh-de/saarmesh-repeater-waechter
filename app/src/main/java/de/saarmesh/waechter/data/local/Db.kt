package de.saarmesh.waechter.data.local

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import de.saarmesh.waechter.domain.BatteryInfo
import de.saarmesh.waechter.domain.BatteryLevel
import de.saarmesh.waechter.domain.BatterySample
import de.saarmesh.waechter.domain.Favorite
import de.saarmesh.waechter.domain.Repeater
import de.saarmesh.waechter.domain.ServerStatus
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "repeater")
data class RepeaterEntity(
    @PrimaryKey val publicKey: String,
    val name: String,
    val serverStatus: String,
    val lastSeen: Long,
    val via: String?,
    val art: String?,
    val sightings: Int,
    val lat: Double?,
    val lon: Double?,
    val firstSeen: Long?,
    val quietAfter: Long?,
    val downAfter: Long?,
    val typicalGap: Long?,
    val rareGap: Long?,
    val replacedByKey: String?,
    val replacedByName: String?,
    val daily: List<Int>,
)

@Entity(tableName = "favorite")
data class FavoriteEntity(
    @PrimaryKey val publicKey: String,
    val offlineAfterHours: Int?,
    val notify: Boolean,
    val wasDown: Boolean?,
    /** Name zum Zeitpunkt des Favorisierens, falls der Repeater (noch) nicht in der Liste ist. */
    val label: String?,
    /** Zuletzt bewertete Akku-Stufe (OK/LOW/CRITICAL); null = noch nie bewertet. */
    @ColumnInfo(defaultValue = "NULL") val batteryLevel: String? = null,
)

@Entity(tableName = "battery")
data class BatteryEntity(
    @PrimaryKey val publicKey: String,
    val latestMv: Int?,
    val latestTs: Long?,
    val lowMv: Int,
    val criticalMv: Int,
    /** "ts:mv;ts:mv;..." */
    val samples: String,
    val fetchedAt: Long,
)

@Entity(tableName = "verlauf")
data class VerlaufEntity(
    @PrimaryKey val publicKey: String,
    val fromTs: Long,
    val step: Long,
    val values: List<Int>,
    val fetchedAt: Long,
)

class Converters {
    @TypeConverter
    fun intsToString(v: List<Int>): String = v.joinToString(",")

    @TypeConverter
    fun stringToInts(s: String): List<Int> = if (s.isEmpty()) emptyList() else s.split(',').map { it.toIntOrNull() ?: 0 }
}

@Dao
interface RepeaterDao {
    @Query("SELECT * FROM repeater")
    fun observeAll(): Flow<List<RepeaterEntity>>

    @Query("SELECT * FROM repeater")
    suspend fun all(): List<RepeaterEntity>

    @Query("SELECT * FROM repeater WHERE publicKey = :pk")
    fun observe(pk: String): Flow<RepeaterEntity?>

    @Upsert
    suspend fun upsertAll(items: List<RepeaterEntity>)

    @Query("DELETE FROM repeater WHERE publicKey NOT IN (:keep)")
    suspend fun deleteOthers(keep: List<String>)

    @Transaction
    suspend fun replaceAll(items: List<RepeaterEntity>) {
        upsertAll(items)
        deleteOthers(items.map { it.publicKey })
    }
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorite")
    suspend fun all(): List<FavoriteEntity>

    @Query("SELECT * FROM favorite WHERE publicKey = :pk")
    fun observe(pk: String): Flow<FavoriteEntity?>

    @Query("SELECT * FROM favorite WHERE publicKey = :pk")
    suspend fun get(pk: String): FavoriteEntity?

    @Upsert
    suspend fun upsert(f: FavoriteEntity)

    @Upsert
    suspend fun upsertAll(f: List<FavoriteEntity>)

    @Query("DELETE FROM favorite WHERE publicKey = :pk")
    suspend fun delete(pk: String)

    @Query("UPDATE favorite SET wasDown = :wasDown WHERE publicKey = :pk")
    suspend fun setWasDown(pk: String, wasDown: Boolean?)

    @Query("UPDATE favorite SET batteryLevel = :level WHERE publicKey = :pk")
    suspend fun setBatteryLevel(pk: String, level: String?)
}

@Dao
interface BatteryDao {
    @Query("SELECT * FROM battery WHERE publicKey = :pk")
    fun observe(pk: String): Flow<BatteryEntity?>

    @Query("SELECT * FROM battery WHERE publicKey = :pk")
    suspend fun get(pk: String): BatteryEntity?

    @Query("SELECT * FROM battery")
    fun observeAll(): Flow<List<BatteryEntity>>

    @Upsert
    suspend fun upsert(b: BatteryEntity)
}

@Dao
interface VerlaufDao {
    @Query("SELECT * FROM verlauf WHERE publicKey = :pk")
    fun observe(pk: String): Flow<VerlaufEntity?>

    @Upsert
    suspend fun upsert(v: VerlaufEntity)
}

@Database(
    entities = [RepeaterEntity::class, FavoriteEntity::class, VerlaufEntity::class, BatteryEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun repeaters(): RepeaterDao
    abstract fun favorites(): FavoriteDao
    abstract fun verlauf(): VerlaufDao
    abstract fun battery(): BatteryDao
}

fun Repeater.toEntity() = RepeaterEntity(
    publicKey, name, serverStatus.wire, lastSeen, via, art, sightings, lat, lon, firstSeen,
    quietAfter, downAfter, typicalGap, rareGap, replacedByKey, replacedByName, daily,
)

fun RepeaterEntity.toDomain() = Repeater(
    publicKey, name, ServerStatus.parse(serverStatus), lastSeen, via, art, sightings, lat, lon, firstSeen,
    quietAfter, downAfter, typicalGap, rareGap, replacedByKey, replacedByName, daily,
)

fun FavoriteEntity.toDomain() = Favorite(
    publicKey, offlineAfterHours, notify, wasDown,
    batteryLevel?.let { v -> BatteryLevel.entries.firstOrNull { it.name == v } },
)

fun BatteryInfo.toEntity(fetchedAt: Long) = BatteryEntity(
    publicKey, latestMv, latestTs, lowMv, criticalMv,
    samples.joinToString(";") { "${it.ts}:${it.mv}" }, fetchedAt,
)

fun BatteryEntity.toDomain() = BatteryInfo(
    publicKey, latestMv, latestTs, lowMv, criticalMv,
    if (samples.isEmpty()) emptyList() else samples.split(';').mapNotNull { p ->
        val i = p.indexOf(':')
        if (i <= 0) null else p.substring(0, i).toLongOrNull()?.let { ts -> p.substring(i + 1).toIntOrNull()?.let { BatterySample(ts, it) } }
    },
)
