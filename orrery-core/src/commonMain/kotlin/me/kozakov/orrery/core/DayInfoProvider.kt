package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate

/**
 * Secondary per-day information (lunar date, festival, holiday flag…).
 * calendar-lunar ships an implementation; consumers can provide their own.
 */
public interface DayInfoProvider {
    public fun info(date: LocalDate): DayInfo?

    /**
     * Combines this provider with [other]. When both return non-null [DayInfo]
     * for a date:
     * - The entry with the higher [DayInfo.priority] supplies the primary
     *   [DayInfo.label], [DayInfo.colorArgb], and [DayInfo.payload] (ties favor `this`).
     * - [DayInfo.isHoliday] is `this.isHoliday || other.isHoliday`.
     * - [DayInfo.tags] is the deduplicated union of both tag lists in order.
     */
    public operator fun plus(other: DayInfoProvider): DayInfoProvider =
        CompositeDayInfoProvider(this, other)
}

/** Standard priority tiers for [DayInfo.priority] when composing providers. */
public object DayInfoPriority {
    public const val DEFAULT: Int = 0
    public const val SECONDARY: Int = 10
    public const val SOLAR_TERM: Int = 20
    public const val FESTIVAL: Int = 30
    public const val PUBLIC_HOLIDAY: Int = 40
}

public data class DayInfo(
    public val label: String?,
    public val isHoliday: Boolean = false,
    public val tags: List<String> = emptyList(),
    public val priority: Int = DayInfoPriority.DEFAULT,
    public val colorArgb: Long? = null,
    public val payload: Any? = null,
) {
    /** Binary-compatible 3-argument constructor for 0.5.x callers. */
    @Deprecated(
        message = "Binary compatibility bridge for 3-arg DayInfo constructor",
        level = DeprecationLevel.HIDDEN,
    )
    public constructor(
        label: String?,
        isHoliday: Boolean = false,
        tags: List<String> = emptyList(),
    ) : this(
        label = label,
        isHoliday = isHoliday,
        tags = tags,
        priority = DayInfoPriority.DEFAULT,
        colorArgb = null,
        payload = null,
    )

    /** Binary-compatible 3-argument copy bridge for callers compiled against 0.5.x. */
    @Deprecated(
        message = "Binary compatibility bridge for 3-arg DayInfo.copy",
        level = DeprecationLevel.HIDDEN,
    )
    public fun copy(
        label: String? = this.label,
        isHoliday: Boolean = this.isHoliday,
        tags: List<String> = this.tags,
    ): DayInfo =
        DayInfo(
            label = label,
            isHoliday = isHoliday,
            tags = tags,
            priority = this.priority,
            colorArgb = this.colorArgb,
            payload = this.payload,
        )
}

/** Type-safe extraction of [DayInfo.payload], returning null if absent or of a different type. */
public inline fun <reified T> DayInfo.payloadAs(): T? = payload as? T

internal class CompositeDayInfoProvider(
    private val first: DayInfoProvider,
    private val second: DayInfoProvider,
) : DayInfoProvider {
    override fun info(date: LocalDate): DayInfo? {
        val a = first.info(date) ?: return second.info(date)
        val b = second.info(date) ?: return a
        val primary = if (b.priority > a.priority) b else a
        return DayInfo(
            label = primary.label,
            isHoliday = a.isHoliday || b.isHoliday,
            tags = (a.tags + b.tags).distinct(),
            priority = primary.priority,
            colorArgb = primary.colorArgb,
            payload = primary.payload,
        )
    }
}
