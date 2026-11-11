package com.github.guidohu.clockshift.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.github.guidohu.clockshift.domain.AutoTrackMode
import com.github.guidohu.clockshift.domain.AutoTrackSettings
import com.github.guidohu.clockshift.domain.Settings
import com.github.guidohu.clockshift.domain.SmartReminderSettings
import com.github.guidohu.clockshift.domain.WeekSchedule
import java.time.LocalDate

enum class SetupStep { Welcome, Schedule, Start, Reminder, AutoTrack }

/** Holds the wizard's answers until the user finishes, so rotating the screen loses nothing. */
class SetupViewModel : ViewModel() {
    var step by mutableIntStateOf(0)
        private set
    var week by mutableStateOf(WeekSchedule.standard())
        private set
    var startDate by mutableStateOf(LocalDate.now())
        private set
    var openingBalanceMinutes by mutableIntStateOf(0)
        private set
    var reminderEnabled by mutableStateOf(true)
        private set
    var reminderMinute by mutableIntStateOf(Settings.DEFAULT_REMINDER_MINUTE)
        private set
    var workReminders by mutableStateOf(SmartReminderSettings())
        private set
    var autoTrack by mutableStateOf(AutoTrackSettings())
        private set

    /** Whether finishing should ask for the notification permission. */
    val wantsNotifications: Boolean
        get() = reminderEnabled || workReminders.checkIn || workReminders.checkOut || autoTrack.mode != AutoTrackMode.Off

    private var reminderEdited = false

    val currentStep: SetupStep get() = SetupStep.entries[step]
    val isLastStep: Boolean get() = step == SetupStep.entries.lastIndex

    /** The schedule step needs at least one working day with a positive daily target. */
    val canContinue: Boolean
        get() = currentStep != SetupStep.Schedule || week.days.values.any { it.working && it.targetMinutes > 0 }

    fun next() {
        if (!isLastStep && canContinue) step++
    }

    fun back() {
        if (step > 0) step--
    }

    fun updateWeek(value: WeekSchedule) {
        week = value
        if (!reminderEdited) reminderMinute = suggestedReminderMinute(value)
    }

    fun updateStartDate(value: LocalDate) {
        startDate = value
    }

    fun updateOpeningBalance(value: Int) {
        openingBalanceMinutes = value
    }

    fun updateReminderEnabled(value: Boolean) {
        reminderEnabled = value
    }

    fun updateReminderMinute(value: Int) {
        reminderEdited = true
        reminderMinute = value
    }

    fun updateWorkReminders(value: SmartReminderSettings) {
        workReminders = value
    }

    fun updateAutoTrack(value: AutoTrackSettings) {
        autoTrack = value
    }

    fun toSettings() = Settings(
        setupDone = true,
        startDate = startDate,
        openingBalanceMinutes = openingBalanceMinutes,
        reminderEnabled = reminderEnabled,
        reminderMinute = reminderMinute,
        autoTrack = autoTrack,
        smartReminders = workReminders,
    )

    private fun suggestedReminderMinute(week: WeekSchedule): Int {
        val end = week.latestEndMinute ?: return Settings.DEFAULT_REMINDER_MINUTE
        return ((end + REMINDER_DELAY_MINUTES) / 5 * 5).coerceAtMost(MAX_REMINDER_MINUTE)
    }

    private companion object {
        const val REMINDER_DELAY_MINUTES = 30
        const val MAX_REMINDER_MINUTE = 23 * 60 + 30
    }
}
