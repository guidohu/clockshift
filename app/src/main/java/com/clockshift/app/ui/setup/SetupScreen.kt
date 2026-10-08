package com.clockshift.app.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clockshift.app.R
import com.clockshift.app.ui.ClockActions
import com.clockshift.app.ui.components.NotificationPermission
import com.clockshift.app.ui.components.rememberNotificationPermission
import com.clockshift.app.ui.settings.ReminderSection
import com.clockshift.app.ui.settings.ScheduleEditor
import com.clockshift.app.ui.settings.TrackingFields

@Composable
fun SetupScreen(viewModel: SetupViewModel, actions: ClockActions) {
    val step = viewModel.currentStep
    BackHandler(enabled = viewModel.step > 0) { viewModel.back() }

    fun finish() = actions.completeSetup(viewModel.week, viewModel.toSettings())
    // The system dialog is shown once, when the user confirms the whole setup; the outcome never blocks finishing.
    val permission = rememberNotificationPermission(onResult = { finish() })

    Scaffold(
        topBar = { if (step != SetupStep.Welcome) SetupProgress(viewModel.step, SetupStep.entries.size - 1) },
        bottomBar = {
            SetupButtons(
                step = step,
                isLast = viewModel.isLastStep,
                canContinue = viewModel.canContinue,
                onBack = viewModel::back,
                onNext = {
                    when {
                        !viewModel.isLastStep -> viewModel.next()
                        viewModel.reminderEnabled && permission.needsRuntimePrompt -> permission.request()
                        else -> finish()
                    }
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = step,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut()) using SizeTransform(clip = false)
            },
            label = "setup-step",
        ) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                when (current) {
                    SetupStep.Welcome -> WelcomeStep()
                    SetupStep.Schedule -> ScheduleStep(viewModel)
                    SetupStep.Start -> StartStep(viewModel)
                    SetupStep.Reminder -> ReminderStep(viewModel, permission)
                }
            }
        }
    }
}

@Composable
private fun SetupProgress(stepIndex: Int, totalSteps: Int) {
    Column(Modifier.padding(horizontal = 24.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.setup_step_of, stepIndex, totalSteps),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { stepIndex / totalSteps.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SetupButtons(step: SetupStep, isLast: Boolean, canContinue: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step != SetupStep.Welcome) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.setup_back)) }
            }
            Button(
                onClick = onNext,
                enabled = canContinue,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Text(
                    stringResource(
                        when {
                            step == SetupStep.Welcome -> R.string.setup_get_started
                            isLast -> R.string.setup_finish
                            else -> R.string.setup_next
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun WelcomeStep() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            WelcomePoint(Icons.Filled.TouchApp, R.string.welcome_point_1_title, R.string.welcome_point_1_text)
            WelcomePoint(Icons.Filled.EventAvailable, R.string.welcome_point_2_title, R.string.welcome_point_2_text)
            WelcomePoint(Icons.AutoMirrored.Filled.TrendingUp, R.string.welcome_point_3_title, R.string.welcome_point_3_text)
        }
    }
}

@Composable
private fun WelcomePoint(icon: ImageVector, title: Int, text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScheduleStep(viewModel: SetupViewModel) {
    StepHeader(stringResource(R.string.setup_schedule_title), stringResource(R.string.setup_schedule_subtitle))
    ScheduleEditor(week = viewModel.week, onChange = viewModel::updateWeek)
}

@Composable
private fun StartStep(viewModel: SetupViewModel) {
    StepHeader(stringResource(R.string.setup_start_title), stringResource(R.string.setup_start_subtitle))
    TrackingFields(
        startDate = viewModel.startDate,
        openingBalanceMinutes = viewModel.openingBalanceMinutes,
        onStartDateChange = viewModel::updateStartDate,
        onOpeningBalanceChange = viewModel::updateOpeningBalance,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.setup_start_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReminderStep(viewModel: SetupViewModel, permission: NotificationPermission) {
    StepHeader(stringResource(R.string.setup_reminder_title), stringResource(R.string.setup_reminder_subtitle))
    ReminderSection(
        enabled = viewModel.reminderEnabled,
        minuteOfDay = viewModel.reminderMinute,
        permission = permission,
        onEnabledChange = viewModel::updateReminderEnabled,
        onMinuteChange = viewModel::updateReminderMinute,
        showPermissionNotice = false,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.setup_reminder_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
