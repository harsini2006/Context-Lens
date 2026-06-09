package com.mpowernet.shesafe.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mpowernet.shesafe.data.dao.ConsentLogDao
import com.mpowernet.shesafe.data.dao.PermissionRuleDao
import com.mpowernet.shesafe.data.entity.ConsentLog
import com.mpowernet.shesafe.data.entity.PermissionRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [PermissionRule::class, ConsentLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun permissionRuleDao(): PermissionRuleDao
    abstract fun consentLogDao(): ConsentLogDao

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
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialRules(database.permissionRuleDao())
                }
            }
        }

        private suspend fun populateInitialRules(dao: PermissionRuleDao) {
            val initialRules = listOf(
                PermissionRule(
                    permissionId = "android.permission.ACCESS_FINE_LOCATION",
                    systemLabel = "Location",
                    riskLevel = "HIGH",
                    explanationEnglish = "Can see where you are right now.",
                    explanationHindi = "यह देख सकता है कि आप कहाँ हैं।",
                    allowedPackagesCSV = "com.google.android.apps.maps,com.ubercab,com.olaec.customer"
                ),
                PermissionRule(
                    permissionId = "android.permission.CAMERA",
                    systemLabel = "Camera",
                    riskLevel = "MEDIUM",
                    explanationEnglish = "Can take photos and record videos.",
                    explanationHindi = "तस्वीरें और वीडियो ले सकता है।",
                    allowedPackagesCSV = "com.whatsapp,com.instagram.android,com.google.android.camera"
                ),
                PermissionRule(
                    permissionId = "android.permission.RECORD_AUDIO",
                    systemLabel = "Microphone",
                    riskLevel = "HIGH",
                    explanationEnglish = "Can record your voice and sounds.",
                    explanationHindi = "आपकी आवाज़ रिकॉर्ड कर सकता है।",
                    allowedPackagesCSV = "com.whatsapp,com.google.android.googlequicksearchbox"
                ),
                PermissionRule(
                    permissionId = "android.permission.READ_CONTACTS",
                    systemLabel = "Contacts",
                    riskLevel = "HIGH",
                    explanationEnglish = "Can see names and phone numbers.",
                    explanationHindi = "नाम और फ़ोन नंबर देख सकता है।",
                    allowedPackagesCSV = "com.whatsapp,com.google.android.contacts,com.android.phone"
                ),
                PermissionRule(
                    permissionId = "android.permission.READ_MEDIA_IMAGES",
                    systemLabel = "Photos & Media",
                    riskLevel = "MEDIUM",
                    explanationEnglish = "Can view photos stored on device.",
                    explanationHindi = "डिवाइस में मौजूद फ़ोटो देख सकता है।",
                    allowedPackagesCSV = "com.whatsapp,com.instagram.android,com.google.android.apps.photos"
                ),
                PermissionRule(
                    permissionId = "android.permission.POST_NOTIFICATIONS",
                    systemLabel = "Notifications",
                    riskLevel = "LOW",
                    explanationEnglish = "Can show notifications and alerts.",
                    explanationHindi = "सूचनाएं और अलर्ट दिखा सकता है।",
                    allowedPackagesCSV = ""
                )
            )
            dao.insertRules(initialRules)
        }
    }
}
