package com.bolohisab.ui.format

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bolohisab.data.settings.AppLanguage
import com.bolohisab.data.settings.DigitStyle
import com.bolohisab.nlu.BanglaText
import com.bolohisab.nlu.Poisha
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** Bangla display formatting: Bengali digits by default (switchable to Latin), ৳ prefix. */
object Bn {
    private const val DIGITS = "০১২৩৪৫৬৭৮৯"
    private val months = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর",
    )
    private val weekdays = listOf("সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার", "রবিবার")
    private val monthsEn = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    private val weekdaysEn = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    /** Backed by Compose state so a Settings change redraws every screen already showing a number. */
    var digitStyle: DigitStyle by mutableStateOf(DigitStyle.BENGALI)

    /** Display language for month/weekday/time-of-day/today-yesterday labels — independent of [digitStyle]. */
    var language: AppLanguage by mutableStateOf(AppLanguage.BANGLA)

    fun digits(s: String): String {
        if (digitStyle == DigitStyle.LATIN) return s
        return buildString(s.length) { for (c in s) append(if (c in '0'..'9') DIGITS[c - '0'] else c) }
    }

    fun number(n: Long): String = digits(group(n))

    /** 1250000 -> "12,50,000" (lakh grouping, as written in Bangladesh and India). */
    private fun group(n: Long): String {
        val s = abs(n).toString()
        if (s.length <= 3) return (if (n < 0) "-" else "") + s
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return (if (n < 0) "-" else "") + "$rest,$last3"
    }

    /** "৳১,২৫০" or "৳৩৫.৫০"; negative values use a minus sign. */
    fun taka(p: Poisha): String {
        val v = abs(p.value)
        val whole = group(v / 100)
        val frac = v % 100
        val text = if (frac == 0L) whole else "$whole.${frac.toString().padStart(2, '0')}"
        return (if (p.value < 0) "−" else "") + "৳" + digits(text)
    }

    /** Plain number for an editable field: "1250" or "35.5", in ASCII so the keyboard can edit it. */
    fun editable(p: Poisha): String {
        val v = p.value
        return if (v % 100 == 0L) (v / 100).toString() else String.format(Locale.ROOT, "%.2f", v / 100.0).trimEnd('0').trimEnd('.')
    }

    fun qty(d: Double): String {
        val s = if (d % 1.0 == 0.0) d.toLong().toString() else String.format(Locale.ROOT, "%.2f", d).trimEnd('0').trimEnd('.')
        return digits(s)
    }

    /** Parses user input in Bengali or ASCII digits; commas ignored. */
    fun parseAmount(text: String): Double? =
        BanglaText.normalize(text).replace(",", "").replace("৳", "").trim().toDoubleOrNull()

    fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = instant.atZone(zone).toLocalTime()
        val part = if (language == AppLanguage.ENGLISH) {
            when (t.hour) {
                in 4..11 -> "Morning"
                in 12..14 -> "Noon"
                in 15..17 -> "Afternoon"
                in 18..19 -> "Evening"
                else -> "Night"
            }
        } else {
            when (t.hour) {
                in 4..11 -> "সকাল"
                in 12..14 -> "দুপুর"
                in 15..17 -> "বিকাল"
                in 18..19 -> "সন্ধ্যা"
                else -> "রাত"
            }
        }
        val h12 = (t.hour % 12).let { if (it == 0) 12 else it }
        return "$part ${digits("$h12:${t.minute.toString().padStart(2, '0')}")}"
    }

    fun date(date: LocalDate): String {
        val month = if (language == AppLanguage.ENGLISH) monthsEn[date.monthValue - 1] else months[date.monthValue - 1]
        return "${digits(date.dayOfMonth.toString())} $month"
    }

    fun longDate(date: LocalDate): String {
        val weekday = if (language == AppLanguage.ENGLISH) weekdaysEn[date.dayOfWeek.value - 1] else weekdays[date.dayOfWeek.value - 1]
        return "$weekday, ${date(date)}"
    }

    /** "আজ", "গতকাল", or "২৬ সেপ্টেম্বর" (or the English equivalents). */
    fun day(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = instant.atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val english = language == AppLanguage.ENGLISH
        return when (ChronoUnit.DAYS.between(d, today)) {
            0L -> if (english) "Today" else "আজ"
            1L -> if (english) "Yesterday" else "গতকাল"
            else -> date(d)
        }
    }
}
