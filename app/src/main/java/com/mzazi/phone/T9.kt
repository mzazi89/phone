package com.mzazi.phone

/**
 * Pure helpers for smart dialling and number presentation.
 *
 * Kept free of Android dependencies so the matching rules can be unit tested
 * on the JVM.
 */
object T9 {

    private val letterToDigit = mapOf(
        'a' to '2', 'b' to '2', 'c' to '2',
        'd' to '3', 'e' to '3', 'f' to '3',
        'g' to '4', 'h' to '4', 'i' to '4',
        'j' to '5', 'k' to '5', 'l' to '5',
        'm' to '6', 'n' to '6', 'o' to '6',
        'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
        't' to '8', 'u' to '8', 'v' to '8',
        'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9'
    )

    /** "Alice" -> "25423": the digit sequence a T9 keypad would produce. */
    fun digitsFor(text: String): String {
        val out = StringBuilder(text.length)
        for (ch in text.lowercase()) {
            val digit = letterToDigit[ch]
            when {
                digit != null -> out.append(digit)
                ch.isDigit() -> out.append(ch)
            }
        }
        return out.toString()
    }

    /** Digits only, keeping a single leading plus sign for international numbers. */
    fun normalizeNumber(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    /**
     * True when [query] should show [name] / [number] in the smart-dial list.
     * Name matches use T9 digits, number matches use a plain substring test.
     */
    fun matches(query: String, name: String, number: String): Boolean {
        val digitsQuery = normalizeNumber(query).trimStart('+')
        if (digitsQuery.isEmpty()) return true

        val numberDigits = normalizeNumber(number).trimStart('+')
        if (numberDigits.contains(digitsQuery)) return true

        return digitsFor(name).contains(digitsQuery)
    }

    /** Groups digits the way a phone app does: 0712 345 678, +254 712 345 678. */
    fun formatNumber(raw: String): String {
        val normalized = normalizeNumber(raw)
        if (normalized.isEmpty()) return raw

        val international = normalized.startsWith("+")
        val digits = normalized.trimStart('+')
        val prefix = if (international) "+" else ""

        if (digits.length <= 3) return prefix + digits

        return when {
            international && digits.length > 9 ->
                prefix + digits.substring(0, 3) + " " + digits.substring(3).chunked(3).joinToString(" ")

            !international && digits.length == 10 ->
                prefix + digits.substring(0, 4) + " " + digits.substring(4, 7) + " " + digits.substring(7)

            !international && digits.length == 9 ->
                prefix + digits.substring(0, 3) + " " + digits.substring(3, 6) + " " + digits.substring(6)

            else -> prefix + digits.chunked(4).joinToString(" ")
        }
    }

    /** Initials for the fallback avatar: "Mary Wanjiku" -> "MW". */
    fun initials(name: String): String {
        val words = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> "#"
            words.size == 1 -> words[0].take(1).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
    }
}
