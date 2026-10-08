package com.github.guidohu.clockshift.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.guidohu.clockshift.data.AppState
import com.github.guidohu.clockshift.data.ClockRepository
import com.github.guidohu.clockshift.domain.Entry
import com.github.guidohu.clockshift.domain.EntryEdit
import com.github.guidohu.clockshift.domain.Settings
import com.github.guidohu.clockshift.domain.WeekSchedule
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class MainViewModel(private val repository: ClockRepository) : ViewModel(), ClockActions {
    /** Null until the first database read has finished. */
    val state: StateFlow<AppState?> = repository.state.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Runs [block] in the view model scope so a configuration change cannot cancel a write half way. */
    private suspend fun <T> inScope(block: suspend () -> T): T = viewModelScope.async { block() }.await()

    override fun checkIn() {
        viewModelScope.launch { repository.checkIn(LocalDateTime.now()) }
    }

    override fun startBreak() {
        viewModelScope.launch { repository.startBreak(LocalDateTime.now()) }
    }

    override fun endBreak() {
        viewModelScope.launch { repository.endBreak(LocalDateTime.now()) }
    }

    override suspend fun checkOut(): Entry? = inScope { repository.checkOut(LocalDateTime.now()) }

    override suspend fun saveEntry(entry: Entry): Long = inScope { repository.saveEntry(entry) }

    override suspend fun deleteEntry(id: Long) = inScope { repository.deleteEntry(id) }

    override suspend fun applyEdit(edit: EntryEdit): List<Long> = inScope { repository.applyEdit(edit) }

    override fun setDayOff(date: LocalDate, off: Boolean) {
        viewModelScope.launch { repository.setDayOff(date, off) }
    }

    override suspend fun logStandardDay(date: LocalDate): List<Entry> = inScope { repository.logStandardDay(date) }

    override fun saveSchedule(week: WeekSchedule) {
        viewModelScope.launch { repository.saveSchedule(week, LocalDate.now()) }
    }

    override fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }

    override fun completeSetup(week: WeekSchedule, settings: Settings) {
        viewModelScope.launch { repository.completeSetup(week, settings) }
    }
}
