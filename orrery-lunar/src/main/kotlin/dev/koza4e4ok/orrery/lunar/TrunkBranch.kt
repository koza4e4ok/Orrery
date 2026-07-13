package dev.koza4e4ok.orrery.lunar

// Rotated so that index (year % 10) - 1 / (year % 12) - 1 works directly,
// matching the original TrunkBranchAnnals arrays. Characters are identical
// in simplified and traditional Chinese.
private val TRUNKS = listOf("辛", "壬", "癸", "甲", "乙", "丙", "丁", "戊", "己", "庚")
private val BRANCHES = listOf("酉", "戌", "亥", "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申")

/** Heavenly-stem / earthly-branch (干支) name of a Gregorian [year], e.g. 2024 -> 甲辰. */
public fun trunkBranchYear(year: Int): String {
    val trunk = year % 10
    val branch = year % 12
    return TRUNKS[if (trunk == 0) 9 else trunk - 1] + BRANCHES[if (branch == 0) 11 else branch - 1]
}
