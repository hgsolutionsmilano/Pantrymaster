package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ThemePreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pantrymaster_prefs")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PANTRY_GRID_VIEW = booleanPreferencesKey("pantry_grid_view")
        val LAST_NOTIFICATION_CHECK = longPreferencesKey("last_notification_check")
        val SELECTED_SHOPPING_LIST_ID = stringPreferencesKey("selected_shopping_list_id")
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        val CUSTOM_GITHUB_REPO = stringPreferencesKey("custom_github_repo")
        val IGNORED_UPDATE_TAG = stringPreferencesKey("ignored_update_tag")
        val LAST_NOTIFIED_UPDATE_TAG = stringPreferencesKey("last_notified_update_tag")
        val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
    }

    val themePreference: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: ThemePreference.SYSTEM.name
        runCatching { ThemePreference.valueOf(raw) }.getOrDefault(ThemePreference.SYSTEM)
    }

    val isPantryGridView: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.PANTRY_GRID_VIEW] ?: false
    }

    val selectedShoppingListId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_SHOPPING_LIST_ID]
    }

    val autoUpdateEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_UPDATE_ENABLED] ?: true
    }

    val customGithubRepo: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.CUSTOM_GITHUB_REPO] ?: ""
    }

    val ignoredUpdateTag: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.IGNORED_UPDATE_TAG]
    }

    val lastNotifiedUpdateTag: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_NOTIFIED_UPDATE_TAG]
    }

    val lastUpdateCheckAt: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_UPDATE_CHECK_AT] ?: 0L
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME_MODE] = theme.name }
    }

    suspend fun setPantryGridView(isGrid: Boolean) {
        context.dataStore.edit { it[Keys.PANTRY_GRID_VIEW] = isGrid }
    }

    suspend fun setSelectedShoppingListId(listId: String?) {
        context.dataStore.edit { prefs ->
            if (listId == null) prefs.remove(Keys.SELECTED_SHOPPING_LIST_ID)
            else prefs[Keys.SELECTED_SHOPPING_LIST_ID] = listId
        }
    }

    suspend fun setLastNotificationCheck(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_NOTIFICATION_CHECK] = timestamp }
    }

    suspend fun setAutoUpdateEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_UPDATE_ENABLED] = enabled }
    }

    suspend fun setCustomGithubRepo(repo: String) {
        context.dataStore.edit { it[Keys.CUSTOM_GITHUB_REPO] = repo.trim() }
    }

    suspend fun setIgnoredUpdateTag(tag: String?) {
        context.dataStore.edit { prefs ->
            if (tag.isNullOrBlank()) prefs.remove(Keys.IGNORED_UPDATE_TAG)
            else prefs[Keys.IGNORED_UPDATE_TAG] = tag
        }
    }

    suspend fun setLastNotifiedUpdateTag(tag: String?) {
        context.dataStore.edit { prefs ->
            if (tag.isNullOrBlank()) prefs.remove(Keys.LAST_NOTIFIED_UPDATE_TAG)
            else prefs[Keys.LAST_NOTIFIED_UPDATE_TAG] = tag
        }
    }

    suspend fun setLastUpdateCheckAt(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_UPDATE_CHECK_AT] = timestamp }
    }
}
