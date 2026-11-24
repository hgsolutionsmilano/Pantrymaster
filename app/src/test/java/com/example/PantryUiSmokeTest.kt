package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.data.local.entity.StorageLocationEntity
import com.example.domain.model.LocationType
import com.example.ui.components.LocationSelectorRow
import com.example.ui.components.PantryFlowRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PantryUiSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pantryFlowRow_and_locationSelectorRow_renderWithoutCrash() {
        val locations = listOf(
            StorageLocationEntity("loc1", "Dispensa", LocationType.PANTRY, "inventory", 1),
            StorageLocationEntity("loc2", "Frigorifero", LocationType.FRIDGE, "kitchen", 2),
            StorageLocationEntity("loc3", "Freezer", LocationType.FREEZER, "ac_unit", 3),
            StorageLocationEntity("loc4", "Altro", LocationType.OTHER, "more", 4)
        )

        composeRule.setContent {
            MaterialTheme {
                Column {
                    PantryFlowRow {
                        FilterChip(selected = true, onClick = {}, label = { Text("Sistema") })
                        FilterChip(selected = false, onClick = {}, label = { Text("Chiaro") })
                        FilterChip(selected = false, onClick = {}, label = { Text("Scuro") })
                    }
                    LocationSelectorRow(
                        locations = locations,
                        selectedLocationId = "loc1",
                        onLocationSelected = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Sistema").assertIsDisplayed()
        composeRule.onNodeWithText("Dispensa").assertIsDisplayed()
        composeRule.onNodeWithText("Frigorifero").assertIsDisplayed()
    }
}
