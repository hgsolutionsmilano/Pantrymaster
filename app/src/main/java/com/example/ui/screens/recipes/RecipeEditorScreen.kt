package com.example.ui.screens.recipes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.RecipeIngredientEntity
import com.example.domain.model.RecipeDifficulty
import com.example.domain.model.UnitOfMeasure
import com.example.ui.components.UnitDropdownField
import kotlinx.coroutines.launch
import java.util.UUID

private data class EditableIngredient(
    val id: String = UUID.randomUUID().toString(),
    val productId: String? = null,
    val name: String = "",
    val qtyText: String = "100",
    val unit: UnitOfMeasure = UnitOfMeasure.GRAMS
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(
    recipeId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: RecipesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val existingFlow = remember(recipeId) { recipeId?.let { viewModel.observeRecipe(it) } }
    val existing = existingFlow?.collectAsStateWithLifecycle()?.value

    var initialized by remember(recipeId) { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var imagePath by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }
    var prepTimeText by remember { mutableStateOf("25") }
    var servingsText by remember { mutableStateOf("2") }
    var difficulty by remember { mutableStateOf(RecipeDifficulty.EASY) }
    var instructions by remember { mutableStateOf("") }
    val ingredients = remember { mutableStateListOf(EditableIngredient()) }

    LaunchedEffect(existing) {
        if (recipeId != null && existing != null && !initialized) {
            val r = existing.recipeWithIngredients.recipe
            name = r.name
            imagePath = r.imagePath
            description = r.description
            prepTimeText = r.prepTimeMinutes.toString()
            servingsText = r.servings.toString()
            difficulty = r.difficulty
            instructions = r.instructions
            ingredients.clear()
            existing.recipeWithIngredients.ingredients.forEach { ing ->
                ingredients.add(
                    EditableIngredient(
                        id = ing.id,
                        productId = ing.productId,
                        name = ing.ingredientName,
                        qtyText = if (ing.requiredQuantity % 1.0 == 0.0) {
                            ing.requiredQuantity.toInt().toString()
                        } else {
                            ing.requiredQuantity.toString()
                        },
                        unit = ing.unit
                    )
                )
            }
            if (ingredients.isEmpty()) ingredients.add(EditableIngredient())
            initialized = true
        }
    }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val copied = viewModel.copyPickedImage(uri)
                if (copied != null) imagePath = copied
            }
        }
    }

    fun submit() {
        val entities = ingredients.mapNotNull { row ->
            val trimmed = row.name.trim()
            val qty = row.qtyText.replace(",", ".").toDoubleOrNull() ?: 0.0
            if (trimmed.isEmpty() || qty <= 0.0) return@mapNotNull null
            RecipeIngredientEntity(
                id = row.id,
                recipeId = recipeId.orEmpty(),
                productId = row.productId,
                ingredientName = trimmed,
                requiredQuantity = qty,
                unit = row.unit
            )
        }
        viewModel.saveRecipe(
            recipeId = recipeId,
            name = name,
            imagePath = imagePath,
            description = description,
            prepTimeMinutes = prepTimeText.toIntOrNull() ?: 20,
            difficulty = difficulty,
            servings = servingsText.toIntOrNull() ?: 2,
            instructions = instructions,
            ingredients = entities,
            onSaved = onSaved
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (recipeId == null) "Nuova ricetta" else "Modifica ricetta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = ::submit) {
                        Icon(Icons.Default.Check, contentDescription = "Salva")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedButton(
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (imagePath != null) "Cambia foto ricetta" else "Aggiungi foto ricetta")
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome ricetta *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descrizione") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = prepTimeText,
                    onValueChange = { prepTimeText = it },
                    label = { Text("Minuti") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = servingsText,
                    onValueChange = { servingsText = it },
                    label = { Text("Porzioni") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Difficoltà", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecipeDifficulty.entries.forEach { d ->
                    FilterChip(
                        selected = difficulty == d,
                        onClick = { difficulty = d },
                        label = { Text(d.labelIt) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ingredienti", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { ingredients.add(EditableIngredient()) }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Aggiungi")
                }
            }

            ingredients.forEachIndexed { index, row ->
                var linkDropdownOpen by remember(row.id) { mutableStateOf(false) }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = row.name,
                                onValueChange = { newText ->
                                    ingredients[index] = row.copy(name = newText, productId = null)
                                },
                                label = { Text("Ingrediente") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (ingredients.size > 1) {
                                IconButton(onClick = { ingredients.removeAt(index) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Rimuovi")
                                }
                            }
                        }

                        if (uiState.pantryProducts.isNotEmpty()) {
                            ExposedDropdownMenuBox(
                                expanded = linkDropdownOpen,
                                onExpandedChange = { linkDropdownOpen = !linkDropdownOpen }
                            ) {
                                OutlinedTextField(
                                    value = uiState.pantryProducts.firstOrNull { it.product.id == row.productId }?.product?.name
                                        ?: "Collega a prodotto dispensa (opzionale)",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = linkDropdownOpen)
                                    },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = linkDropdownOpen,
                                    onDismissRequest = { linkDropdownOpen = false }
                                ) {
                                    uiState.pantryProducts.forEach { prod ->
                                        DropdownMenuItem(
                                            text = { Text(prod.product.name) },
                                            onClick = {
                                                ingredients[index] = row.copy(
                                                    productId = prod.product.id,
                                                    name = prod.product.name,
                                                    unit = prod.product.unit
                                                )
                                                linkDropdownOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = row.qtyText,
                                onValueChange = { ingredients[index] = row.copy(qtyText = it) },
                                label = { Text("Q.tà") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(0.9f)
                            )
                            UnitDropdownField(
                                selectedUnit = row.unit,
                                onUnitSelected = { ingredients[index] = row.copy(unit = it) },
                                modifier = Modifier.weight(1.4f)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it },
                label = { Text("Procedimento") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = ::submit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Salva ricetta")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
