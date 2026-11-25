package com.example.ui.screens.info

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.relation.HouseInfoWithPhotos
import com.example.data.repository.HouseInfoPhotoDraft
import com.example.data.repository.HouseInfoRepository
import com.example.domain.model.HouseInfoCategory
import com.example.domain.model.MeasurementUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HouseInfoListUiState(
    val isLoading: Boolean = true,
    val allEntries: List<HouseInfoWithPhotos> = emptyList(),
    val filteredEntries: List<HouseInfoWithPhotos> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: HouseInfoCategory? = null
)

@HiltViewModel
class HouseInfoViewModel @Inject constructor(
    private val repository: HouseInfoRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<HouseInfoCategory?>(null)

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    val uiState: StateFlow<HouseInfoListUiState> = combine(
        repository.observeAll(),
        searchQuery,
        selectedCategory
    ) { entries, query, category ->
        val q = query.trim().lowercase()
        val filtered = entries.filter { item ->
            val e = item.entry
            val matchesCategory = category == null || e.category == category
            val matchesQuery = q.isEmpty() ||
                e.title.lowercase().contains(q) ||
                e.subtitle.lowercase().contains(q) ||
                e.primaryValue.lowercase().contains(q) ||
                e.secondaryValue.lowercase().contains(q) ||
                e.notes.lowercase().contains(q) ||
                item.photos.any { it.caption.lowercase().contains(q) }
            matchesCategory && matchesQuery
        }
        HouseInfoListUiState(
            isLoading = false,
            allEntries = entries,
            filteredEntries = filtered,
            searchQuery = query,
            selectedCategory = category
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HouseInfoListUiState()
    )

    fun setSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun selectCategory(category: HouseInfoCategory?) {
        selectedCategory.value = category
    }

    fun observeDetail(entryId: String): StateFlow<HouseInfoWithPhotos?> {
        return repository.observeById(entryId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    }

    fun togglePinned(entryId: String, currentlyPinned: Boolean) {
        viewModelScope.launch {
            repository.togglePinned(entryId, currentlyPinned)
            _feedbackMessage.value = if (currentlyPinned) {
                "Rimossa dalla Home"
            } else {
                "Fissata nella Home"
            }
        }
    }

    fun saveEntry(
        entryId: String?,
        category: HouseInfoCategory,
        title: String,
        subtitle: String,
        primaryValue: String,
        secondaryValue: String,
        secretValue: String,
        widthValue: String,
        heightValue: String,
        depthValue: String,
        measurementUnit: MeasurementUnit,
        notes: String,
        isPinned: Boolean,
        photos: List<HouseInfoPhotoDraft>,
        onSaved: (String) -> Unit
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _feedbackMessage.value = "Inserisci un titolo per la scheda."
            return
        }
        if (category == HouseInfoCategory.PHONE_NUMBER && primaryValue.trim().isEmpty()) {
            _feedbackMessage.value = "Inserisci almeno il numero di telefono."
            return
        }
        if (category == HouseInfoCategory.MEASUREMENT &&
            widthValue.trim().isEmpty() &&
            heightValue.trim().isEmpty() &&
            depthValue.trim().isEmpty() &&
            notes.trim().isEmpty()
        ) {
            _feedbackMessage.value = "Inserisci almeno una misura o una nota."
            return
        }
        if (category == HouseInfoCategory.PHOTO_DETAIL && photos.isEmpty() && notes.trim().isEmpty()) {
            _feedbackMessage.value = "Aggiungi almeno una foto o una nota."
            return
        }
        if (category == HouseInfoCategory.PASSWORD_NOTE &&
            secretValue.trim().isEmpty() &&
            notes.trim().isEmpty() &&
            primaryValue.trim().isEmpty()
        ) {
            _feedbackMessage.value = "Inserisci un codice, un riferimento o una nota."
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            runCatching {
                repository.saveEntry(
                    entryId = entryId,
                    category = category,
                    title = cleanTitle,
                    subtitle = subtitle,
                    primaryValue = primaryValue,
                    secondaryValue = secondaryValue,
                    secretValue = secretValue,
                    widthValue = widthValue,
                    heightValue = heightValue,
                    depthValue = depthValue,
                    measurementUnit = measurementUnit,
                    notes = notes,
                    isPinned = isPinned,
                    photos = photos
                )
            }.onSuccess { savedId ->
                _isSaving.value = false
                onSaved(savedId)
            }.onFailure { err ->
                _isSaving.value = false
                _feedbackMessage.value = "Errore durante il salvataggio: ${err.localizedMessage ?: "riprova"}"
            }
        }
    }

    fun deleteEntry(entryId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteEntry(entryId)
            _feedbackMessage.value = "Scheda eliminata"
            onDeleted()
        }
    }

    suspend fun copyPickedImage(uri: Uri): String? = repository.copyPickedImage(uri)

    fun showMessage(message: String) {
        _feedbackMessage.value = message
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }
}
