package info.malondaovalle.riego.data.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Parses the date-time strings the API returns. Some fields come with an offset
 * ("...Z"), others are bare wall-clock timestamps ("2026-08-30T17:50:28.536"),
 * so we try the tolerant options in order and fall back to null.
 */
fun parseApiDateTime(value: String?): LocalDateTime? {
    if (value.isNullOrBlank()) return null
    return runCatching { LocalDateTime.parse(value) }
        .recoverCatching { OffsetDateTime.parse(value).toLocalDateTime() }
        .recoverCatching { Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime() }
        .getOrNull()
}
