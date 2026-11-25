package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface PantryDestination

@Serializable
data object HomeRoute : PantryDestination

@Serializable
data object PantryRoute : PantryDestination

@Serializable
data object ShoppingRoute : PantryDestination

@Serializable
data object RecipesRoute : PantryDestination

@Serializable
data object StatsRoute : PantryDestination

@Serializable
data class ProductDetailRoute(val productId: String) : PantryDestination

@Serializable
data class ProductEditorRoute(
    val productId: String? = null,
    val prefillBarcode: String? = null,
    val prefillName: String? = null,
    val prefillBrand: String? = null,
    val prefillCategoryId: String? = null,
    val prefillDescription: String? = null,
    val prefillUnitName: String? = null,
    val prefillLocalImagePath: String? = null,
    val prefillExpiryDateMillis: Long? = null,
    val prefillExpiryTypeName: String? = null
) : PantryDestination

@Serializable
data object ScannerRoute : PantryDestination

@Serializable
data class RecipeDetailRoute(val recipeId: String) : PantryDestination

@Serializable
data class RecipeEditorRoute(val recipeId: String? = null) : PantryDestination

@Serializable
data object MovementHistoryRoute : PantryDestination

@Serializable
data object SettingsRoute : PantryDestination

@Serializable
data object CategoryManagerRoute : PantryDestination
