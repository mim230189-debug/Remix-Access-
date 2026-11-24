package com.example.data.local

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ThemePreference
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.winkDataStore: DataStore<Preferences> by preferencesDataStore(name = "wink_device_prefs")

class DevicePreferencesStore(private val context: Context) {

  private val deviceIdKey = stringPreferencesKey("device_id")
  private val deviceNameKey = stringPreferencesKey("device_name")
  private val themePrefKey = stringPreferencesKey("theme_preference")
  private val fcmTokenKey = stringPreferencesKey("fcm_token")

  val deviceIdFlow: Flow<String?> = context.winkDataStore.data.map { it[deviceIdKey] }

  val deviceNameFlow: Flow<String> =
    context.winkDataStore.data.map { prefs ->
      prefs[deviceNameKey]?.takeIf { it.isNotBlank() } ?: defaultDeviceName()
    }

  val themePreferenceFlow: Flow<ThemePreference> =
    context.winkDataStore.data.map { prefs ->
      ThemePreference.fromRaw(prefs[themePrefKey])
    }

  suspend fun getOrCreateDeviceId(): String {
    val existing = context.winkDataStore.data.first()[deviceIdKey]
    if (!existing.isNullOrBlank()) return existing
    val raw = UUID.randomUUID().toString().replace("-", "").uppercase()
    val generated = "WINK-${raw.substring(0, 4)}-${raw.substring(4, 8)}"
    context.winkDataStore.edit { prefs -> prefs[deviceIdKey] = generated }
    return generated
  }

  suspend fun regenerateDeviceId(): String {
    val raw = UUID.randomUUID().toString().replace("-", "").uppercase()
    val generated = "WINK-${raw.substring(0, 4)}-${raw.substring(4, 8)}"
    context.winkDataStore.edit { prefs -> prefs[deviceIdKey] = generated }
    return generated
  }

  suspend fun setDeviceName(name: String) {
    val clean = name.trim().take(80)
    if (clean.isNotEmpty()) {
      context.winkDataStore.edit { prefs -> prefs[deviceNameKey] = clean }
    }
  }

  suspend fun setThemePreference(preference: ThemePreference) {
    context.winkDataStore.edit { prefs -> prefs[themePrefKey] = preference.name }
  }

  suspend fun setFcmToken(token: String) {
    context.winkDataStore.edit { prefs -> prefs[fcmTokenKey] = token }
  }

  suspend fun getFcmToken(): String? = context.winkDataStore.data.first()[fcmTokenKey]

  private fun defaultDeviceName(): String {
    val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
    val model = Build.MODEL.orEmpty()
    val combined = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    return combined.trim().ifBlank { "Android Device" }.take(80)
  }
}
