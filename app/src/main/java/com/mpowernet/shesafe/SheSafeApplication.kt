package com.mpowernet.shesafe

import android.app.Application
import com.mpowernet.shesafe.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class SheSafeApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob())
    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }

    override fun onCreate() {
        super.onCreate()
    }
}
