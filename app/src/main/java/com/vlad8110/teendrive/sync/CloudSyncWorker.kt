package com.vlad8110.teendrive.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vlad8110.teendrive.data.AccountPreferences
import com.vlad8110.teendrive.data.TeenDriveDatabase
import com.vlad8110.teendrive.data.TripRepository
import com.vlad8110.teendrive.firebase.FirebaseAccountRepository
import com.vlad8110.teendrive.firebase.FirebaseTripSyncRepository
import com.vlad8110.teendrive.model.AccountRole
import kotlinx.coroutines.flow.first

class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val preferences = AccountPreferences(applicationContext)
        val currentState = preferences.state.first()
        if (!currentState.hasSelectedRole) return Result.success()

        return runCatching {
            val accountRepository = FirebaseAccountRepository()
            val accountResult = if (currentState.selectedRole == AccountRole.TEEN) {
                accountRepository.syncTeenProfile(currentState)
            } else {
                accountRepository.syncParentProfile(currentState)
            }
            preferences.saveAccountState(accountResult.state)

            if (accountResult.state.selectedRole == AccountRole.TEEN) {
                val tripRepository = TripRepository(
                    TeenDriveDatabase.getInstance(applicationContext).tripDao(),
                )
                FirebaseTripSyncRepository().syncLocalTrips(
                    accountState = accountResult.state,
                    tripRepository = tripRepository,
                )
            }

            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
