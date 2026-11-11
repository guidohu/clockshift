package com.github.guidohu.clockshift.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What the app believes about the user being at the workplace.
 *
 * [since] is null while the user is away. [lastSeen] is the last moment the phone was known to be at
 * work. [leftAt] marks a departure that is not confirmed yet: it only counts once the user has stayed
 * away for the configured delay, so a dropped Wi-Fi connection or GPS jitter does not end the day.
 */
data class Presence(
    val since: LocalDateTime? = null,
    val lastSeen: LocalDateTime? = null,
    val leftAt: LocalDateTime? = null,
) {
    val atWork: Boolean get() = since != null
}

/** Something the phone noticed. */
sealed interface PresenceSignal {
    /**
     * The phone is at the workplace. [initial] marks a report that only describes the current state
     * (right after the app registered for updates, or a periodic Wi-Fi check), not an actual arrival.
     */
    data class Arrived(val at: LocalDateTime, val initial: Boolean = false) : PresenceSignal

    /** The phone left the workplace at [at] (geofence exit). */
    data class Left(val at: LocalDateTime) : PresenceSignal

    /**
     * Periodic check at [now]. [atWork] is whether the phone is at the workplace right now, or null when
     * the source cannot tell on demand (geofence); then the check only confirms pending departures.
     */
    data class Check(val now: LocalDateTime, val atWork: Boolean?) : PresenceSignal
}

sealed interface AutoTrackDecision {
    data object None : AutoTrackDecision
    data class CheckIn(val at: LocalDateTime) : AutoTrackDecision
    data class CheckOut(val at: LocalDateTime) : AutoTrackDecision
}

/** The parts of the user's data the rules need. */
interface AutoTrackDay {
    /** The running block (work or break), if any. */
    val active: Entry?
    fun hasEntriesOn(date: LocalDate): Boolean
    fun isWorkingDay(date: LocalDate): Boolean
}

/** New presence, what to do about it, and when to check again (null: no check needed). */
data class AutoTrackStep(
    val presence: Presence,
    val decision: AutoTrackDecision,
    val nextCheckAt: LocalDateTime?,
)

object AutoTrackRules {
    /** How often the Wi-Fi connection is checked while the user is at work. */
    val WIFI_POLL: Duration = Duration.ofMinutes(5)

    /** Presence not confirmed for this long is forgotten, e.g. when a departure was never reported. */
    val STALE_AFTER: Duration = Duration.ofHours(16)

    fun step(
        presence: Presence,
        signal: PresenceSignal,
        settings: AutoTrackSettings,
        day: AutoTrackDay,
        now: LocalDateTime,
    ): AutoTrackStep {
        val current = presence.forgetIfStale(now)
        return when (signal) {
            is PresenceSignal.Arrived -> arrived(current, signal, settings, day, now)
            is PresenceSignal.Left -> left(current, signal.at, settings, now)
            is PresenceSignal.Check -> check(current, signal, settings, day)
        }
    }

    private fun arrived(
        presence: Presence,
        signal: PresenceSignal.Arrived,
        settings: AutoTrackSettings,
        day: AutoTrackDay,
        now: LocalDateTime,
    ): AutoTrackStep {
        val at = signal.at
        if (presence.atWork) {
            val stillThere = presence.copy(lastSeen = latest(presence.lastSeen, at), leftAt = null)
            // Back before the departure counted, a mere state report, or a Wi-Fi reconnect: nothing changes.
            val missedDeparture = presence.leftAt == null && !signal.initial && settings.mode == AutoTrackMode.Location
            if (!missedDeparture) return AutoTrackStep(stillThere, AutoTrackDecision.None, nextCheck(stillThere, settings, now))
        }
        val arrived = Presence(since = at, lastSeen = at)
        val date = at.toLocalDate()
        val checkIn = day.active == null &&
            (!settings.workingDaysOnly || day.isWorkingDay(date)) &&
            // A state report never re-opens a day the user has already logged time on (e.g. after checking out at the desk).
            (!signal.initial || !day.hasEntriesOn(date))
        val decision = if (checkIn) AutoTrackDecision.CheckIn(at) else AutoTrackDecision.None
        return AutoTrackStep(arrived, decision, nextCheck(arrived, settings, now))
    }

    private fun left(presence: Presence, at: LocalDateTime, settings: AutoTrackSettings, now: LocalDateTime): AutoTrackStep {
        if (!presence.atWork) return AutoTrackStep(presence, AutoTrackDecision.None, null)
        val leaving = presence.copy(
            lastSeen = latest(presence.lastSeen, at),
            leftAt = presence.leftAt ?: at,
        )
        return AutoTrackStep(leaving, AutoTrackDecision.None, nextCheck(leaving, settings, now))
    }

    private fun check(presence: Presence, signal: PresenceSignal.Check, settings: AutoTrackSettings, day: AutoTrackDay): AutoTrackStep {
        val now = signal.now
        if (!presence.atWork) {
            return if (signal.atWork == true) {
                arrived(presence, PresenceSignal.Arrived(now, initial = true), settings, day, now)
            } else {
                AutoTrackStep(presence, AutoTrackDecision.None, null)
            }
        }
        val updated = when (signal.atWork) {
            true -> presence.copy(lastSeen = now, leftAt = null)
            false -> if (presence.leftAt == null) presence.copy(leftAt = presence.lastSeen ?: now) else presence
            null -> presence
        }
        val leftAt = updated.leftAt
        if (leftAt == null || now < leftAt.plusMinutes(settings.leaveDelayMinutes.toLong())) {
            return AutoTrackStep(updated, AutoTrackDecision.None, nextCheck(updated, settings, now))
        }
        // Away long enough: the departure counts. Only a session that was running before the user left is ended.
        val active = day.active
        val decision = if (active != null && active.start < leftAt) AutoTrackDecision.CheckOut(leftAt) else AutoTrackDecision.None
        return AutoTrackStep(Presence(), decision, null)
    }

    private fun nextCheck(presence: Presence, settings: AutoTrackSettings, now: LocalDateTime): LocalDateTime? {
        if (!presence.atWork) return null
        val confirm = presence.leftAt?.plusMinutes(settings.leaveDelayMinutes.toLong())
        val poll = if (settings.mode == AutoTrackMode.Wifi) now.plus(WIFI_POLL) else null
        return listOfNotNull(confirm, poll).minOrNull()
    }

    private fun Presence.forgetIfStale(now: LocalDateTime): Presence {
        val seen = lastSeen ?: since ?: return this
        return if (Duration.between(seen, now) > STALE_AFTER) Presence() else this
    }

    private fun latest(a: LocalDateTime?, b: LocalDateTime): LocalDateTime = if (a == null || b > a) b else a
}
