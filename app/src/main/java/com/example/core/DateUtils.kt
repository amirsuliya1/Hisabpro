package com.example.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DateRangeFilter(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    PREVIOUS_MONTH("Previous Month"),
    THIS_YEAR("This Year"),
    CUSTOM("Custom Range"),
    ALL_TIME("All Time")
}

object DateUtils {
    private val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val dateTimeFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val shortMonthFormatter = SimpleDateFormat("MMM", Locale.getDefault())
    private val isoDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun formatDate(millis: Long): String = dateFormatter.format(Date(millis))

    fun formatDateTime(millis: Long): String = dateTimeFormatter.format(Date(millis))

    fun formatIsoDate(millis: Long): String = isoDateFormatter.format(Date(millis))

    fun formatMonthShort(millis: Long): String = shortMonthFormatter.format(Date(millis))

    fun currentFinancialYearLabel(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) // 0-based: April is 3
        return if (month >= Calendar.APRIL) {
            "FY $year-${(year + 1).toString().takeLast(2)}"
        } else {
            "FY ${year - 1}-${year.toString().takeLast(2)}"
        }
    }

    fun resolveRange(
        filter: DateRangeFilter,
        customStartMillis: Long? = null,
        customEndMillis: Long? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (filter) {
            DateRangeFilter.TODAY -> {
                val start = startOfDay(cal)
                val end = endOfDay(cal)
                start to end
            }
            DateRangeFilter.YESTERDAY -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                startOfDay(cal) to endOfDay(cal)
            }
            DateRangeFilter.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val start = startOfDay(cal)
                cal.add(Calendar.DAY_OF_YEAR, 6)
                val end = endOfDay(cal)
                start to end
            }
            DateRangeFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(cal)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val end = endOfDay(cal)
                start to end
            }
            DateRangeFilter.PREVIOUS_MONTH -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(cal)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val end = endOfDay(cal)
                start to end
            }
            DateRangeFilter.THIS_YEAR -> {
                val fyStartYear = if (cal.get(Calendar.MONTH) >= Calendar.APRIL) {
                    cal.get(Calendar.YEAR)
                } else {
                    cal.get(Calendar.YEAR) - 1
                }
                val startCal = Calendar.getInstance().apply {
                    set(fyStartYear, Calendar.APRIL, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance().apply {
                    set(fyStartYear + 1, Calendar.MARCH, 31, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                startCal.timeInMillis to endCal.timeInMillis
            }
            DateRangeFilter.CUSTOM -> {
                val s = customStartMillis ?: startOfDay(cal)
                val e = customEndMillis ?: endOfDay(cal)
                minOf(s, e) to maxOf(s, e)
            }
            DateRangeFilter.ALL_TIME -> 0L to Long.MAX_VALUE
        }
    }

    private fun startOfDay(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun endOfDay(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.set(Calendar.MILLISECOND, 999)
        return c.timeInMillis
    }
}
