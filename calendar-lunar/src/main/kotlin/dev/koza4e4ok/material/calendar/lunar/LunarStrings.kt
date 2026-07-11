package dev.koza4e4ok.material.calendar.lunar

/** Which Chinese string set [LunarDayInfoProvider] renders. */
public enum class ChineseVariant { SIMPLIFIED, TRADITIONAL_TW, TRADITIONAL_HK }

// Transcribed from the original CalendarView resources:
// values/strings.xml (zh-CN), values-zh-rTW/, values-zh-rHK/.

// Identical across variants.
private val DAY_NAMES =
    listOf(
        "初一",
        "初二",
        "初三",
        "初四",
        "初五",
        "初六",
        "初七",
        "初八",
        "初九",
        "初十",
        "十一",
        "十二",
        "十三",
        "十四",
        "十五",
        "十六",
        "十七",
        "十八",
        "十九",
        "二十",
        "廿一",
        "廿二",
        "廿三",
        "廿四",
        "廿五",
        "廿六",
        "廿七",
        "廿八",
        "廿九",
        "三十",
    )

private val MONTH_NAMES_SIMPLIFIED =
    listOf("春节", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "冬月", "腊月")
private val MONTH_NAMES_TRADITIONAL =
    listOf("春節", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "冬月", "臘月")

private val TRADITIONAL_FESTIVALS_SIMPLIFIED =
    mapOf(
        (1 to 1) to "春节",
        (1 to 15) to "元宵",
        (5 to 5) to "端午",
        (7 to 7) to "七夕",
        (8 to 15) to "中秋",
        (9 to 9) to "重阳",
    )
private val TRADITIONAL_FESTIVALS_TRADITIONAL =
    mapOf(
        (1 to 1) to "春節",
        (1 to 15) to "元宵",
        (5 to 5) to "端午",
        (7 to 7) to "七夕",
        (8 to 15) to "中秋",
        (9 to 9) to "重陽",
    )

// Order matches the original solar_term array: index 0 = 春分 (= SolarTerm ordinal).
private val SOLAR_TERM_NAMES_SIMPLIFIED =
    listOf(
        "春分",
        "清明",
        "谷雨",
        "立夏",
        "小满",
        "芒种",
        "夏至",
        "小暑",
        "大暑",
        "立秋",
        "处暑",
        "白露",
        "秋分",
        "寒露",
        "霜降",
        "立冬",
        "小雪",
        "大雪",
        "冬至",
        "小寒",
        "大寒",
        "立春",
        "雨水",
        "惊蛰",
    )
private val SOLAR_TERM_NAMES_TRADITIONAL =
    listOf(
        "春分",
        "清明",
        "穀雨",
        "立夏",
        "小滿",
        "芒種",
        "夏至",
        "小暑",
        "大暑",
        "立秋",
        "處暑",
        "白露",
        "秋分",
        "寒露",
        "霜降",
        "立冬",
        "小雪",
        "大雪",
        "冬至",
        "小寒",
        "大寒",
        "立春",
        "雨水",
        "驚蟄",
    )

private val GREGORIAN_FESTIVALS_SIMPLIFIED =
    mapOf(
        (1 to 1) to "元旦",
        (2 to 14) to "情人节",
        (3 to 8) to "妇女节",
        (3 to 12) to "植树节",
        (3 to 15) to "消权日",
        (4 to 1) to "愚人节",
        (4 to 22) to "地球日",
        (5 to 1) to "劳动节",
        (5 to 4) to "青年节",
        (6 to 1) to "儿童节",
        (7 to 1) to "建党节",
        (8 to 1) to "建军节",
        (9 to 10) to "教师节",
        (10 to 1) to "国庆节",
        (10 to 31) to "万圣节",
        (11 to 11) to "光棍节",
        (12 to 24) to "平安夜",
        (12 to 25) to "圣诞节",
    )
private val GREGORIAN_FESTIVALS_TW =
    mapOf(
        (1 to 1) to "元旦",
        (2 to 14) to "情人節",
        (3 to 8) to "婦女節",
        (3 to 12) to "植樹節",
        (3 to 15) to "消權日",
        (4 to 1) to "愚人節",
        (4 to 4) to "兒童節",
        (4 to 22) to "地球日",
        (5 to 1) to "勞動節",
        (9 to 3) to "軍人節",
        (9 to 28) to "教師節",
        (5 to 4) to "青年節",
        (6 to 1) to "兒童節",
        (7 to 1) to "建黨節",
        (8 to 1) to "建軍節",
        (10 to 1) to "國慶節",
        (10 to 31) to "萬聖節",
        (11 to 11) to "光棍節",
        (12 to 24) to "平安夜",
        (12 to 25) to "聖誕節",
    )
private val GREGORIAN_FESTIVALS_HK =
    mapOf(
        (1 to 1) to "元旦",
        (2 to 14) to "情人節",
        (3 to 8) to "婦女節",
        (3 to 12) to "植樹節",
        (3 to 15) to "消權日",
        (4 to 1) to "愚人節",
        (4 to 4) to "兒童節",
        (4 to 22) to "地球日",
        (5 to 1) to "勞動節",
        (5 to 4) to "青年節",
        (7 to 1) to "香港回歸",
        (8 to 1) to "建军节",
        (9 to 10) to "教師節",
        (10 to 1) to "國慶日",
        (10 to 31) to "萬聖節",
        (11 to 11) to "光棍節",
        (12 to 24) to "平安夜",
        (12 to 25) to "聖誕節",
    )

internal class LunarStrings(
    val monthNames: List<String>,
    val dayNames: List<String>,
    val leapPrefix: String,
    val eve: String,
    val traditionalFestivals: Map<Pair<Int, Int>, String>,
    val gregorianFestivals: Map<Pair<Int, Int>, String>,
    val specialFestivals: List<String>,
    val solarTermNames: List<String>,
)

private val SIMPLIFIED_STRINGS =
    LunarStrings(
        monthNames = MONTH_NAMES_SIMPLIFIED,
        dayNames = DAY_NAMES,
        leapPrefix = "闰",
        eve = "除夕",
        traditionalFestivals = TRADITIONAL_FESTIVALS_SIMPLIFIED,
        gregorianFestivals = GREGORIAN_FESTIVALS_SIMPLIFIED,
        specialFestivals = listOf("母亲节", "父亲节", "感恩节"),
        solarTermNames = SOLAR_TERM_NAMES_SIMPLIFIED,
    )
private val TW_STRINGS =
    LunarStrings(
        monthNames = MONTH_NAMES_TRADITIONAL,
        dayNames = DAY_NAMES,
        leapPrefix = "閏",
        eve = "除夕",
        traditionalFestivals = TRADITIONAL_FESTIVALS_TRADITIONAL,
        gregorianFestivals = GREGORIAN_FESTIVALS_TW,
        specialFestivals = listOf("母親節", "父親節", "感恩節"),
        solarTermNames = SOLAR_TERM_NAMES_TRADITIONAL,
    )
private val HK_STRINGS =
    LunarStrings(
        monthNames = MONTH_NAMES_TRADITIONAL,
        dayNames = DAY_NAMES,
        leapPrefix = "閏",
        eve = "除夕",
        traditionalFestivals = TRADITIONAL_FESTIVALS_TRADITIONAL,
        gregorianFestivals = GREGORIAN_FESTIVALS_HK,
        specialFestivals = listOf("母親節", "父親節", "感恩節"),
        solarTermNames = SOLAR_TERM_NAMES_TRADITIONAL,
    )

internal fun strings(variant: ChineseVariant): LunarStrings =
    when (variant) {
        ChineseVariant.SIMPLIFIED -> SIMPLIFIED_STRINGS
        ChineseVariant.TRADITIONAL_TW -> TW_STRINGS
        ChineseVariant.TRADITIONAL_HK -> HK_STRINGS
    }

internal fun lunarLabel(
    lunar: LunarDate,
    variant: ChineseVariant,
): String {
    val s = strings(variant)
    return if (lunar.day == 1) {
        (if (lunar.isLeapMonth) s.leapPrefix else "") + s.monthNames[lunar.month - 1]
    } else {
        s.dayNames[lunar.day - 1]
    }
}
