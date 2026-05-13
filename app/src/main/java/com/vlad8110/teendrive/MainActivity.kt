package com.vlad8110.teendrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vlad8110.teendrive.data.AccountPreferences
import com.vlad8110.teendrive.firebase.FirebaseAccountRepository
import com.vlad8110.teendrive.sync.TeenDriveSyncScheduler
import com.vlad8110.teendrive.ui.TeenDriveApp
import com.vlad8110.teendrive.ui.theme.TeenDriveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TeenDriveSyncScheduler.schedulePeriodic(applicationContext)
        TeenDriveSyncScheduler.requestNow(applicationContext)
        enableEdgeToEdge()
        setContent {
            TeenDriveTheme(dynamicColor = false) {
                TeenDriveApp(
                    accountPreferences = AccountPreferences(applicationContext),
                    firebaseAccountRepository = FirebaseAccountRepository(),
                )
            }
        }
    }
}
