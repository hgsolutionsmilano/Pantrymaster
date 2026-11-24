package com.example.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.PantryStatisticsSnapshot
import com.example.data.repository.StatisticsRepository
import com.example.domain.model.StatsPeriod
import com.example.domain.usecase.PantryDateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.MONTH,
    val fromMillis: Long = 0L,
    val toMillis: Long = System.currentTimeMillis(),
    val snapshot: PantryStatisticsSnapshot = PantryStatisticsSnapshot()
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) : ViewModel() {

    private val _period = MutableStateFlow(StatsPeriod.MONTH)
    private val _customRange = MutableStateFlow(
        PantryDateUtils.addDays(PantryDateUtils.todayStartMillis(), -30) to System.currentTimeMillis()
    )

    private val rangeFlow = combine(_period, _customRange) { period, custom ->
        val now = System.currentTimeMillis()
        val today = PantryDateUtils.todayStartMillis(now)
        when (period) {
            StatsPeriod.WEEK -> PantryDateUtils.addDays(today, -7) to now
            StatsPeriod.MONTH -> PantryDateUtils.addDays(today, -30) to now
            StatsPeriod.YEAR -> PantryDateUtils.addDays(today, -365) to now
            StatsPeriod.CUSTOM -> custom.first to (custom.second + 86_399_999L)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatsUiState> = combine(
        _period,
        rangeFlow
    ) { period, range ->
        period to range
    }.flatMapLatest { (period, range) ->
        statisticsRepository.observeStatistics(range.first, range.second).combine(_period) { snap, p ->
            StatsUiState(
                period = p,
                fromMillis = range.first,
                toMillis = range.second,
                snapshot = snap
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatsUiState()
    )

    fun selectPeriod(period: StatsPeriod) {
        _period.value = period
    }

    fun setCustomRange(fromMillis: Long, toMillis: Long) {
        val start = minOf(fromMillis, toMillis)
        val end = maxOf(fromMillis, toMillis)
        _customRange.value = start to end
        _period.value = StatsPeriod.CUSTOM
    }
}
