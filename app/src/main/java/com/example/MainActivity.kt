package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.SettingsRepository
import com.example.domain.model.ThemePreference
import com.example.ui.navigation.PantryMasterNavHost
import com.example.ui.theme.PantryMasterTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themePreference by settingsRepository.observeThemePreference()
                .collectAsStateWithLifecycle(initialValue = ThemePreference.SYSTEM)
            val windowSizeClass = calculateWindowSizeClass(this)

            PantryMasterTheme(themePreference = themePreference) {
                PantryMasterNavHost(widthSizeClass = windowSizeClass.widthSizeClass)
            }
        }
    }
}
