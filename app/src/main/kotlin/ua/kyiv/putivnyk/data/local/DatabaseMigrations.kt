package ua.kyiv.putivnyk.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE places ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE routes ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE places SET updatedAt = createdAt WHERE updatedAt = 0")
            db.execSQL("UPDATE routes SET updatedAt = createdAt WHERE updatedAt = 0")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS map_bookmarks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    note TEXT,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    zoomLevel INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS localized_strings (
                    key TEXT NOT NULL,
                    locale TEXT NOT NULL,
                    value TEXT NOT NULL,
                    source TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(key, locale)
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS user_preferences (
                    key TEXT NOT NULL,
                    value TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(key)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_state (
                    entityName TEXT NOT NULL,
                    lastSyncAt INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    lastError TEXT,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(entityName)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE places ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE places ADD COLUMN popularity INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE places ADD COLUMN riverBank TEXT NOT NULL DEFAULT 'unknown'")
            db.execSQL(
                "UPDATE places SET riverBank = CASE WHEN longitude >= 30.58 THEN 'left' ELSE 'right' END WHERE riverBank = 'unknown'"
            )
        }
    }

    val ALL = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6
    )
}
