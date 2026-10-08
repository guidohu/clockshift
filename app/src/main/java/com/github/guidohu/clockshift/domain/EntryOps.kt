package com.clockshift.app.domain

import java.time.LocalDateTime

/** Entries to remove and entries to write (id 0 = new) to carry out one edit atomically. */
data class EntryEdit(val delete: List<Long>, val save: List<Entry>) {
    val isEmpty: Boolean get() = delete.isEmpty() && save.isEmpty()
}

sealed interface AddOutcome {
    data class Ok(val edit: EntryEdit) : AddOutcome

    /** A break needs work to take a break from. */
    data object NoWork : AddOutcome

    /** A break inside a running session cannot end in the future. */
    data object InFuture : AddOutcome
}

/**
 * Merges new blocks into the blocks of a day. The rules:
 *  - a **work** block overwrites any break it covers and merges with the work blocks it overlaps or touches;
 *  - a **break** splits the work blocks it covers and merges with the breaks it overlaps or touches;
 *  - removing a break turns its time back into work.
 * A block without an end is running and extends to infinity.
 */
object EntryOps {
    private val Forever: LocalDateTime = LocalDateTime.MAX

    /** Hands out temporary ids (negative) for blocks that do not exist in the database yet. */
    private class TempIds {
        private var next = 0L
        fun take(): Long = --next
    }

    /**
     * Adds a block of [kind]. When [replacing] is given, that block is being edited: it is taken out
     * first (a replaced break turns back into work before the new block is applied).
     */
    fun add(
        existing: List<Entry>,
        kind: EntryKind,
        start: LocalDateTime,
        end: LocalDateTime?,
        now: LocalDateTime,
        replacing: Entry? = null,
    ): AddOutcome {
        val ids = TempIds()
        var list = existing
        if (replacing != null) list = withoutBlock(list, replacing, ids)

        val until = end ?: Forever
        val opposite = if (kind == EntryKind.Work) EntryKind.Break else EntryKind.Work
        val covered = list.filter { it.kind == opposite && overlaps(it, start, until) }
        if (kind == EntryKind.Break) {
            if (covered.isEmpty()) return AddOutcome.NoWork
            if (end != null && end > now && covered.any { it.isActive }) return AddOutcome.InFuture
        }

        list = carve(list, opposite, start, until, ids)
        list = union(list, kind, start, until, ids)
        return AddOutcome.Ok(diff(existing, list))
    }

    /** Deletes a block. Deleting a break fills its time with work. */
    fun remove(existing: List<Entry>, entry: Entry): EntryEdit = diff(existing, withoutBlock(existing, entry, TempIds()))

    private fun withoutBlock(list: List<Entry>, entry: Entry, ids: TempIds): List<Entry> {
        val rest = list.filter { it.id != entry.id }
        return if (entry.isBreak) union(rest, EntryKind.Work, entry.start, entry.end ?: Forever, ids) else rest
    }

    private fun overlaps(entry: Entry, start: LocalDateTime, until: LocalDateTime) =
        entry.start < until && (entry.end ?: Forever) > start

    /** Removes [start, until] from all blocks of [kind], splitting where needed. */
    private fun carve(
        list: List<Entry>,
        kind: EntryKind,
        start: LocalDateTime,
        until: LocalDateTime,
        ids: TempIds,
    ): List<Entry> {
        val result = mutableListOf<Entry>()
        for (entry in list) {
            if (entry.kind != kind || !overlaps(entry, start, until)) {
                result += entry
                continue
            }
            val entryEnd = entry.end ?: Forever
            val hasLeft = entry.start < start
            val hasRight = entryEnd > until
            if (hasLeft) result += entry.copy(end = start)
            if (hasRight) {
                val rightEnd = entry.end
                result += if (hasLeft) {
                    Entry(id = ids.take(), start = until, end = rightEnd, kind = kind)
                } else {
                    entry.copy(start = until)
                }
            }
        }
        return result
    }

    /** Merges [start, until] into the blocks of [kind] it overlaps or touches. */
    private fun union(
        list: List<Entry>,
        kind: EntryKind,
        start: LocalDateTime,
        until: LocalDateTime,
        ids: TempIds,
    ): List<Entry> {
        val touching = list
            .filter { it.kind == kind && it.start <= until && (it.end ?: Forever) >= start }
            .sortedBy { it.start }
        val mergedStart = minOf(start, touching.firstOrNull()?.start ?: start)
        val running = until == Forever || touching.any { it.isActive }
        val mergedEnd = if (running) null else maxOf(until, touching.maxOfOrNull { checkNotNull(it.end) } ?: until)
        val keeperId = touching.firstOrNull()?.id ?: ids.take()
        val rest = list.filter { candidate -> touching.none { it.id == candidate.id } }
        return rest + Entry(id = keeperId, start = mergedStart, end = mergedEnd, kind = kind)
    }

    /** Net change from [before] to [after]; temporary (non-positive) ids become new rows. */
    private fun diff(before: List<Entry>, after: List<Entry>): EntryEdit {
        val originals = before.associateBy { it.id }
        val keptIds = after.filter { it.id > 0 }.mapTo(HashSet()) { it.id }
        val delete = before.map { it.id }.filter { it !in keptIds }
        val save = after
            .filter { it.id <= 0 || originals[it.id] != it }
            .map { if (it.id <= 0) it.copy(id = 0) else it }
        return EntryEdit(delete, save)
    }
}
