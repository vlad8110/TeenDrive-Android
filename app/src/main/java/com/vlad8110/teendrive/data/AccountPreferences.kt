package com.vlad8110.teendrive.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vlad8110.teendrive.model.AccountRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.accountDataStore by preferencesDataStore(name = "account_state")

data class AccountState(
    val hasSelectedRole: Boolean = false,
    val selectedRole: AccountRole = AccountRole.TEEN,
    val displayName: String = "",
    val pairingCode: String = "",
    val pairingToken: String = "",
    val teenProfileId: String = "",
    val parentProfileId: String = "",
    val familyGroupId: String = "",
    val connectedParents: Set<String> = emptySet(),
    val connectedTeens: Set<String> = emptySet(),
    val safetySettings: Set<String> = emptySet(),
)

class AccountPreferences(private val context: Context) {
    val state: Flow<AccountState> = context.accountDataStore.data.map { preferences ->
        AccountState(
            hasSelectedRole = preferences[Keys.hasSelectedRole] ?: false,
            selectedRole = AccountRole.fromStorageValue(preferences[Keys.selectedRole]),
            displayName = preferences[Keys.displayName].orEmpty(),
            pairingCode = preferences[Keys.pairingCode].orEmpty(),
            pairingToken = preferences[Keys.pairingToken].orEmpty(),
            teenProfileId = preferences[Keys.teenProfileId].orEmpty(),
            parentProfileId = preferences[Keys.parentProfileId].orEmpty(),
            familyGroupId = preferences[Keys.familyGroupId].orEmpty(),
            connectedParents = preferences[Keys.connectedParents] ?: emptySet(),
            connectedTeens = preferences[Keys.connectedTeens] ?: emptySet(),
            safetySettings = preferences[Keys.safetySettings] ?: emptySet(),
        )
    }

    suspend fun saveAccountState(state: AccountState) {
        context.accountDataStore.edit { preferences ->
            val savedRoleIsLocked = preferences[Keys.hasSelectedRole] ?: false
            val selectedRole = if (savedRoleIsLocked) {
                AccountRole.fromStorageValue(preferences[Keys.selectedRole])
            } else {
                state.selectedRole
            }

            preferences[Keys.hasSelectedRole] = savedRoleIsLocked || state.hasSelectedRole
            preferences[Keys.selectedRole] = selectedRole.storageValue
            preferences[Keys.displayName] = state.displayName
            preferences[Keys.pairingCode] = state.pairingCode
            preferences[Keys.pairingToken] = state.pairingToken
            preferences[Keys.teenProfileId] = state.teenProfileId
            preferences[Keys.parentProfileId] = state.parentProfileId
            preferences[Keys.familyGroupId] = state.familyGroupId
            preferences[Keys.connectedParents] = state.connectedParents
            preferences[Keys.connectedTeens] = state.connectedTeens
            preferences[Keys.safetySettings] = state.safetySettings
        }
    }

    suspend fun clear() {
        context.accountDataStore.edit { it.clear() }
    }

    private object Keys {
        val hasSelectedRole = booleanPreferencesKey("has_selected_role")
        val selectedRole = stringPreferencesKey("selected_role")
        val displayName = stringPreferencesKey("display_name")
        val pairingCode = stringPreferencesKey("pairing_code")
        val pairingToken = stringPreferencesKey("pairing_token")
        val teenProfileId = stringPreferencesKey("teen_profile_id")
        val parentProfileId = stringPreferencesKey("parent_profile_id")
        val familyGroupId = stringPreferencesKey("family_group_id")
        val connectedParents = stringSetPreferencesKey("connected_parents")
        val connectedTeens = stringSetPreferencesKey("connected_teens")
        val safetySettings = stringSetPreferencesKey("safety_settings")
    }
}
