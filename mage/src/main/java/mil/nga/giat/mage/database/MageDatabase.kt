package mil.nga.giat.mage.database

import android.util.Log
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.j256.ormlite.dao.Dao
import mil.nga.giat.mage.database.dao.feed.FeedDao
import mil.nga.giat.mage.database.dao.feed.FeedItemDao
import mil.nga.giat.mage.database.dao.feed.FeedLocalDao
import mil.nga.giat.mage.database.dao.observationLocation.ObservationLocationDao
import mil.nga.giat.mage.database.model.feed.Feed
import mil.nga.giat.mage.database.model.feed.FeedItem
import mil.nga.giat.mage.database.model.feed.FeedLocal
import mil.nga.giat.mage.database.dao.settings.SettingsDao
import mil.nga.giat.mage.database.model.observation.ObservationLocation
import mil.nga.giat.mage.database.model.observation.UriTypeConverter
import mil.nga.giat.mage.database.model.settings.Settings

@Database(
    version = MageDatabase.VERSION,
    entities = [
        Settings::class,
        Feed::class,
        FeedLocal::class,
        FeedItem::class,
        ObservationLocation::class
    ],
    autoMigrations = [
        AutoMigration (
            from = 1,
            to = 2
        ),
        AutoMigration (
            from = 2,
            to = 3
        )
    ]
)
@TypeConverters(
    DateTypeConverter::class,
    GeometryTypeConverter::class,
    JsonTypeConverter::class,
    UriTypeConverter::class
)
abstract class MageDatabase : RoomDatabase() {

    companion object {
        const val VERSION = 3
    }

    abstract fun settingsDao(): SettingsDao
    abstract fun feedDao(): FeedDao
    abstract fun feedLocalDao(): FeedLocalDao
    abstract fun feedItemDao(): FeedItemDao
    abstract fun observationLocationDao(): ObservationLocationDao

    fun destroy() {
        settingsDao().destroy()
        feedDao().destroy()
        feedLocalDao().destroy()
        feedItemDao().destroy()
        observationLocationDao().destroy()
    }
}