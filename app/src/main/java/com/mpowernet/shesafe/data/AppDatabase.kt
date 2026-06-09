package com.mpowernet.shesafe.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mpowernet.shesafe.data.dao.ConsentLogDao
import com.mpowernet.shesafe.data.dao.PermissionRuleDao
import com.mpowernet.shesafe.data.dao.VaultItemDao
import com.mpowernet.shesafe.data.entity.ConsentLog
import com.mpowernet.shesafe.data.entity.PermissionRule
import com.mpowernet.shesafe.data.entity.VaultItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [PermissionRule::class, ConsentLog::class, VaultItem::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun permissionRuleDao(): PermissionRuleDao
    abstract fun consentLogDao(): ConsentLogDao
    abstract fun vaultItemDao(): VaultItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Initialize SQLCipher native libraries
                net.sqlcipher.database.SQLiteDatabase.loadLibs(context.applicationContext)
                
                // Fetch Keystore passphrase
                val passphrase = com.mpowernet.shesafe.security.KeyStoreManager
                    .getOrCreateDatabasePassphrase(context.applicationContext)
                val factory = net.sqlcipher.database.SupportFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shesafe_database"
                )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            scope.launch(Dispatchers.IO) {
                // Insert initial permission rules using raw SQL statement inside Database Callback
                // to avoid INSTANCE null references during DB build phase.
                db.execSQL("INSERT OR IGNORE INTO permission_rules (permissionId, systemLabel, riskLevel, explanationEnglish, explanationHindi, allowedPackagesCSV) VALUES ('android.permission.ACCESS_FINE_LOCATION', 'Location', 'HIGH', 'Can see where you are right now.', 'यह देख सकता है कि आप कहाँ हैं।', 'com.google.android.apps.maps,com.ubercab,com.olaec.customer')")
                db.execSQL("INSERT OR IGNORE INTO permission_rules (permissionId, systemLabel, riskLevel, explanationEnglish, explanationHindi, allowedPackagesCSV) VALUES ('android.permission.CAMERA', 'Camera', 'MEDIUM', 'Can take photos and record videos.', 'तस्वीरें और वीडियो ले सकता है।', 'com.whatsapp,com.instagram.android,com.google.android.camera')")
                db.execSQL("INSERT OR IGNORE INTO permission_rules (permissionId, systemLabel, riskLevel, explanationEnglish, explanationHindi, allowedPackagesCSV) VALUES ('android.permission.RECORD_AUDIO', 'Microphone', 'HIGH', 'Can record your voice and sounds.', 'आपकी आवाज़ रिकॉर्ड कर सकता है।', 'com.whatsapp,com.google.android.googlequicksearchbox')")
                db.execSQL("INSERT OR IGNORE INTO permission_rules (permissionId, systemLabel, riskLevel, explanationEnglish, explanationHindi, allowedPackagesCSV) VALUES ('android.permission.READ_CONTACTS', 'Contacts', 'HIGH', 'Can see names and phone numbers.', 'नाम और फ़ोन नंबर देख सकता है।', 'com.whatsapp,com.google.android.contacts,com.android.phone')")
                db.execSQL("INSERT OR IGNORE INTO permission_rules (permissionId, systemLabel, riskLevel, explanationEnglish, explanationHindi, allowedPackagesCSV) VALUES ('android.permission.READ_MEDIA_IMAGES', 'Photos & Media', 'MEDIUM', 'Can view photos stored on device.', 'डिवाइस में मौजूद फ़ोटो देख सकता है।', 'com.whatsapp,com.instagram.android,com.google.android.apps.photos')")
                db.execSQL("INSERT OR IGNORE INTO permission_rules (permissionId, systemLabel, riskLevel, explanationEnglish, explanationHindi, allowedPackagesCSV) VALUES ('android.permission.POST_NOTIFICATIONS', 'Notifications', 'LOW', 'Can show notifications and alerts.', 'सूचनाएं और अलर्ट दिखा सकता है।', '')")
            }
        }
    }
}
