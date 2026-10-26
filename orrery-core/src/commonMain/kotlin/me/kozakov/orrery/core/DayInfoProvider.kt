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
     * - If that entry's [DayInfo.label] is null, the other entry's label is used,
     *   so a label-less holiday entry does not erase e.g. a lunar label.
     * - [DayInfo.isHoliday] is `this.isHoliday || other.isHoliday`.
     * - [DayInfo.tags] is the deduplicated union of both tag lists in order.
     */
    public operator fun plus(other: DayInfoProvider): DayInfoProvider = CompositeDayInfoProvider(this, other)
}

/** Standard priority tiers for [DayInfo.priority] when composing providers. */
public object DayInfoPriority {
    public const val DEFAULT: Int = 0
    public const val SECONDARY: Int = 10
    public const val SOLAR_TERM: Int = 20
    public const val FESTIVAL: Int = 30
    public const val PUBLIC_HOLIDAY: Int = 40
}

/**
 * Secondary information for one day, returned by [DayInfoProvider.info].
 *
 * @property label Short text shown under the day number, or null for none.
 * @property isHoliday Whether the day is a holiday (rendered with the holiday label color).
 * @property tags Free-form tags for filtering or styling.
 * @property priority Precedence when providers are combined with [DayInfoProvider.plus]: the
 *   entry with the higher value supplies [label], [colorArgb] and [payload]. Use the tiers in
 *   [DayInfoPriority]; defaults to [DayInfoPriority.DEFAULT].
 * @property colorArgb Optional label color as a packed `0xAARRGGBB` ARGB value (for example
 *   `0xFFC62828`), or null to use the theme color. The alpha byte is required: a 24-bit
 *   `0xRRGGBB` value has alpha `0x00` and is fully transparent.
 * @property payload Arbitrary data attached by the provider (for example `LunarDayMetadata` from
 *   orrery-lunar), passed through unchanged. Read it with [payloadAs].
 */
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
        val secondary = if (primary === a) b else a
        return DayInfo(
            label = primary.label ?: secondary.label,
            isHoliday = a.isHoliday || b.isHoliday,
            tags = (a.tags + b.tags).distinct(),
            priority = primary.priority,
            colorArgb = primary.colorArgb,
            payload = primary.payload,
        )
    }
}
