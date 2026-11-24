package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.pantry.MovementHistoryScreen
import com.example.ui.screens.pantry.PantryScreen
import com.example.ui.screens.pantry.ProductDetailScreen
import com.example.ui.screens.pantry.ProductEditorScreen
import com.example.ui.screens.recipes.RecipeDetailScreen
import com.example.ui.screens.recipes.RecipeEditorScreen
import com.example.ui.screens.recipes.RecipesScreen
import com.example.ui.screens.scanner.ScannerScreen
import com.example.ui.screens.settings.AppUpdateAvailableDialog
import com.example.ui.screens.settings.AppUpdateViewModel
import com.example.ui.screens.settings.CategoryManagerScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.openExternalUpdateUrl
import com.example.ui.screens.shopping.ShoppingScreen
import com.example.ui.screens.stats.StatsScreen

private data class TopNavItem(
    val route: PantryDestination,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topNavItems = listOf(
    TopNavItem(HomeRoute, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    TopNavItem(PantryRoute, "Dispensa", Icons.Filled.Inventory2, Icons.Outlined.Inventory2, "nav_pantry"),
    TopNavItem(ShoppingRoute, "Spesa", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart, "nav_shopping"),
    TopNavItem(RecipesRoute, "Ricette", Icons.Filled.RestaurantMenu, Icons.Outlined.RestaurantMenu, "nav_recipes"),
    TopNavItem(StatsRoute, "Statistiche", Icons.Filled.BarChart, Icons.Outlined.BarChart, "nav_stats")
)

@Composable
fun PantryMasterNavHost(
    widthSizeClass: WindowWidthSizeClass,
    updateViewModel: AppUpdateViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val availableUpdate by updateViewModel.availableUpdate.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        updateViewModel.checkOnStartup()
    }

    availableUpdate?.let { info ->
        AppUpdateAvailableDialog(
            updateInfo = info,
            onDownloadClick = {
                openExternalUpdateUrl(context, info.preferredOpenUrl)
                updateViewModel.remindLater()
            },
            onRemindLater = updateViewModel::remindLater,
            onIgnoreVersion = { updateViewModel.ignoreVersion(info.tagName) }
        )
    }

    val isExpanded = widthSizeClass != WindowWidthSizeClass.Compact
    val showTopLevelChrome = currentDestination?.let { dest ->
        topNavItems.any { item -> dest.hasRoute(item.route::class) }
    } ?: true

    fun navigateTopLevel(route: PantryDestination) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!isExpanded && showTopLevelChrome) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    topNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(item.route::class)
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTopLevel(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label, maxLines = 1) },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isExpanded && showTopLevelChrome) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    topNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(item.route::class)
                        } == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopLevel(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            isExpanded = isExpanded,
                            onOpenScanner = { navController.navigate(ScannerRoute) },
                            onAddProduct = { navController.navigate(ProductEditorRoute()) },
                            onOpenProduct = { id -> navController.navigate(ProductDetailRoute(id)) },
                            onOpenPantry = { navigateTopLevel(PantryRoute) },
                            onOpenShopping = { navigateTopLevel(ShoppingRoute) },
                            onOpenRecipe = { id -> navController.navigate(RecipeDetailRoute(id)) },
                            onOpenHistory = { navController.navigate(MovementHistoryRoute) },
                            onOpenSettings = { navController.navigate(SettingsRoute) }
                        )
                    }

                    composable<PantryRoute> {
                        PantryScreen(
                            isExpanded = isExpanded,
                            onOpenProduct = { id -> navController.navigate(ProductDetailRoute(id)) },
                            onAddProduct = { navController.navigate(ProductEditorRoute()) },
                            onOpenScanner = { navController.navigate(ScannerRoute) }
                        )
                    }

                    composable<ShoppingRoute> {
                        ShoppingScreen()
                    }

                    composable<RecipesRoute> {
                        RecipesScreen(
                            isExpanded = isExpanded,
                            onOpenRecipe = { id -> navController.navigate(RecipeDetailRoute(id)) },
                            onCreateRecipe = { navController.navigate(RecipeEditorRoute()) }
                        )
                    }

                    composable<StatsRoute> {
                        StatsScreen(isExpanded = isExpanded)
                    }

                    composable<ProductDetailRoute> { entry ->
                        val route = entry.toRoute<ProductDetailRoute>()
                        ProductDetailScreen(
                            productId = route.productId,
                            onBack = { navController.popBackStack() },
                            onEditProduct = { id ->
                                navController.navigate(ProductEditorRoute(productId = id))
                            }
                        )
                    }

                    composable<ProductEditorRoute> { entry ->
                        val route = entry.toRoute<ProductEditorRoute>()
                        ProductEditorScreen(
                            productId = route.productId,
                            prefillBarcode = route.prefillBarcode,
                            prefillName = route.prefillName,
                            prefillBrand = route.prefillBrand,
                            prefillCategoryId = route.prefillCategoryId,
                            prefillDescription = route.prefillDescription,
                            prefillUnitName = route.prefillUnitName,
                            prefillLocalImagePath = route.prefillLocalImagePath,
                            onBack = { navController.popBackStack() },
                            onSaved = { savedId ->
                                if (navController.currentBackStackEntry?.destination?.hasRoute<ProductEditorRoute>() == true) {
                                    navController.popBackStack()
                                    if (route.productId == null) {
                                        navController.navigate(ProductDetailRoute(savedId))
                                    }
                                }
                            }
                        )
                    }

                    composable<ScannerRoute> {
                        ScannerScreen(
                            onBack = { navController.popBackStack() },
                            onManualCreate = { code ->
                                navController.popBackStack()
                                navController.navigate(ProductEditorRoute(prefillBarcode = code))
                            },
                            onOpenRemotePrefill = { remote ->
                                navController.popBackStack()
                                navController.navigate(
                                    ProductEditorRoute(
                                        prefillBarcode = remote.barcode,
                                        prefillName = remote.name,
                                        prefillBrand = remote.brand,
                                        prefillCategoryId = remote.suggestedCategoryId,
                                        prefillDescription = remote.description,
                                        prefillUnitName = remote.suggestedUnit.name,
                                        prefillLocalImagePath = remote.localImagePath
                                    )
                                )
                            },
                            onOpenProductDetail = { id ->
                                navController.popBackStack()
                                navController.navigate(ProductDetailRoute(id))
                            }
                        )
                    }

                    composable<RecipeDetailRoute> { entry ->
                        val route = entry.toRoute<RecipeDetailRoute>()
                        RecipeDetailScreen(
                            recipeId = route.recipeId,
                            onBack = { navController.popBackStack() },
                            onEditRecipe = { id ->
                                navController.navigate(RecipeEditorRoute(recipeId = id))
                            }
                        )
                    }

                    composable<RecipeEditorRoute> { entry ->
                        val route = entry.toRoute<RecipeEditorRoute>()
                        RecipeEditorScreen(
                            recipeId = route.recipeId,
                            onBack = { navController.popBackStack() },
                            onSaved = { savedId ->
                                navController.popBackStack()
                                if (route.recipeId == null) {
                                    navController.navigate(RecipeDetailRoute(savedId))
                                }
                            }
                        )
                    }

                    composable<MovementHistoryRoute> {
                        MovementHistoryScreen(onBack = { navController.popBackStack() })
                    }

                    composable<SettingsRoute> {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenCategories = { navController.navigate(CategoryManagerRoute) }
                        )
                    }

                    composable<CategoryManagerRoute> {
                        CategoryManagerScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
