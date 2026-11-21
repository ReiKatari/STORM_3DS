package com.storm3ds.emulator.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.storm3ds.emulator.database.converters.InstantConverter
import com.storm3ds.emulator.database.daos.CheatDao
import com.storm3ds.emulator.database.daos.CheatDatabaseDao
import com.storm3ds.emulator.database.daos.CheatFolderDao
import com.storm3ds.emulator.database.daos.GameDao
import com.storm3ds.emulator.database.daos.RetroAchievementsDao
import com.storm3ds.emulator.database.daos.PlayStatsDao
import com.storm3ds.emulator.database.entities.PlayStatsEntity
import com.storm3ds.emulator.database.entities.PlaySessionEntity
import com.storm3ds.emulator.database.entities.CheatDatabaseEntity
import com.storm3ds.emulator.database.entities.CheatEntity
import com.storm3ds.emulator.database.entities.CheatFolderEntity
import com.storm3ds.emulator.database.entities.GameEntity
import com.storm3ds.emulator.database.entities.retroachievements.RAAchievementEntity
import com.storm3ds.emulator.database.entities.retroachievements.RAAchievementSetEntity
import com.storm3ds.emulator.database.entities.retroachievements.RAGameEntity
import com.storm3ds.emulator.database.entities.retroachievements.RAGameHashEntity
import com.storm3ds.emulator.database.entities.retroachievements.RAGameSetMetadata
import com.storm3ds.emulator.database.entities.retroachievements.RALeaderboardEntity
import com.storm3ds.emulator.database.entities.retroachievements.RAPendingAchievementSubmissionEntity
import com.storm3ds.emulator.database.entities.retroachievements.RAUserAchievementEntity

@Database(
    version = 10,
    exportSchema = true,
    entities = [
        CheatDatabaseEntity::class,
        GameEntity::class,
        CheatFolderEntity::class,
        CheatEntity::class,
        RAGameEntity::class,
        RAAchievementSetEntity::class,
        RAAchievementEntity::class,
        RAUserAchievementEntity::class,
        RALeaderboardEntity::class,
        RAGameSetMetadata::class,
        RAGameHashEntity::class,
        RAPendingAchievementSubmissionEntity::class,
        PlayStatsEntity::class,
        PlaySessionEntity::class,
    ],
    autoMigrations = [
        AutoMigration(from = 2, to = 3, spec = MelonDatabase.Migration2to3Spec::class),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 6, to = 7),
    ]
)
@TypeConverters(InstantConverter::class)
abstract class MelonDatabase : RoomDatabase() {
    abstract fun cheatDatabaseDao(): CheatDatabaseDao
    abstract fun gameDao(): GameDao
    abstract fun cheatFolderDao(): CheatFolderDao
    abstract fun cheatDao(): CheatDao
    abstract fun achievementsDao(): RetroAchievementsDao
    abstract fun playStatsDao(): PlayStatsDao

    class Migration2to3Spec : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            val hasGames = db.query("SELECT COUNT(*) FROM game").use { result ->
                result.moveToFirst() && result.getInt(0) > 0
            }
            if (hasGames) {
                // If there are games, insert a cheat database. Most likely the cheat database that was used was the one
                // from DeadSkullzJr, which has the name "DeadSkullzJr's NDS Cheat Database"
                val contentValues = ContentValues().apply {
                    put("name", "DeadSkullzJr's NDS Cheat Database")
                }
                val databaseId = db.insert("cheat_database", SQLiteDatabase.CONFLICT_IGNORE, contentValues)
                db.execSQL("UPDATE game SET database_id = ?", arrayOf(databaseId))
            }
        }
    }
}
