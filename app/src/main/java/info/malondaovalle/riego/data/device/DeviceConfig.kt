package info.malondaovalle.riego.data.device

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// --- Wire models (GETCONFIG reply) -----------------------------------------------

@Serializable
data class DeviceConfigResponse(
    @SerialName("Configuracion") val configuracion: Configuracion? = null,
    @SerialName("Estado") val estado: Estado? = null,
)

@Serializable
data class Configuracion(
    @SerialName("Canales") val canales: List<CanalDto> = emptyList(),
    @SerialName("Programador") val programador: Programador? = null,
)

@Serializable
data class CanalDto(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Nombre") val nombre: String = "",
    @SerialName("Activo") val activo: Boolean = false,
)

/**
 * `Parametros` payload for the `SETCANAL` command: the device's `Canal` serialized,
 * with `Numero` sent as `Id`. The device upserts by id (modify if it exists, else
 * create).
 */
@Serializable
data class SetCanalPayload(
    @SerialName("Id") val id: Int,
    @SerialName("Nombre") val nombre: String,
    @SerialName("Activo") val activo: Boolean,
)

@Serializable
data class Programador(
    @SerialName("Activo") val activo: Boolean = false,
    @SerialName("Nombre") val nombre: String = "",
    @SerialName("Programas") val programas: List<ProgramaDto> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ProgramaDto(
    /** 0 (or absent) when sending a new program; the device assigns the real id. */
    @SerialName("Id") val id: Int = 0,
    @SerialName("Activo") val activo: Boolean = false,
    @SerialName("Comienzo") val comienzo: String = "",
    @SerialName("Repetir") val repetir: Int = 0,
    @SerialName("Semana") val semana: Int = 0,
    @SerialName("Horas") val horas: List<HoraDto> = emptyList(),
    @SerialName("Canales") val canales: List<CanalDuracionDto> = emptyList(),
    @SerialName("ProximoRiego") @JsonNames("proximoRiego", "Proximo")
    val proximoRiego: String? = null,
)

@Serializable
data class HoraDto(@SerialName("Hora") val hora: String = "")

@Serializable
data class CanalDuracionDto(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Duracion") val duracion: Int = 0,
)

@Serializable
data class Estado(
    @SerialName("ProximoRiego") val proximoRiego: String? = null,
    @SerialName("Regando") val regando: Boolean = false,
    @SerialName("PowerRiego") val powerRiego: Boolean = false,
    @SerialName("PlacaConectada") val placaConectada: Boolean = false,
    @SerialName("Lloviendo") val lloviendo: Boolean = false,
)

// --- UI domain -----------------------------------------------------------------

/** Max channels a device supports. */
const val MAX_DEVICE_CHANNELS = 16

data class DeviceControl(
    val name: String,
    val powerOn: Boolean,
    val watering: Boolean,
    val nextWatering: String?,
    val boardConnected: Boolean,
    val raining: Boolean,
    val channels: List<DeviceChannel>,
    val programs: List<WateringProgram>,
)

data class DeviceChannel(val id: Int, val name: String, val active: Boolean)

data class WateringProgram(
    /** Device-assigned id (0 only for a not-yet-saved program). */
    val id: Int,
    val index: Int,
    val active: Boolean,
    /** Normalized to `yyyy/MM/dd`. */
    val startDate: String,
    val repetition: ProgramRepetition,
    /** Raw `Semana` bitmask (bit 0 = Monday … bit 6 = Sunday). */
    val weekdaysMask: Int,
    /** [weekdaysMask] as short labels — only meaningful when repetition is BY_WEEKDAY. */
    val weekdays: List<String>,
    val times: List<String>,
    val nextWatering: String?,
    val channels: List<ProgramChannel>,
)

data class ProgramChannel(val id: Int, val name: String, val minutes: Int)

/** Editable form model for adding/modifying a program. */
data class ProgramDraft(
    val id: Int,
    val active: Boolean,
    /** `yyyy/MM/dd`. */
    val startDate: String,
    val repetition: ProgramRepetition,
    /** Selected weekday indices (0 = Monday … 6 = Sunday). */
    val weekdays: Set<Int>,
    val times: List<String>,
    val channels: List<DraftChannel>,
) {
    val isNew: Boolean get() = id == 0
}

data class DraftChannel(val channelId: Int, val minutes: Int)

/** `Repetir` is the ordinal of the device enum `ProgramaRepeticion`. */
enum class ProgramRepetition(val label: String) {
    NONE("Una vez"),
    DAILY("Diario"),
    EVERY_2_DAYS("Cada 2 días"),
    EVERY_3_DAYS("Cada 3 días"),
    WEEKLY("Semanal"),
    BY_WEEKDAY("Días concretos");

    companion object {
        fun fromOrdinal(value: Int): ProgramRepetition = entries.getOrElse(value) { NONE }
    }
}

val WEEKDAY_LABELS = listOf("L", "M", "X", "J", "V", "S", "D")

private val PROGRAM_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

/** `Semana` is a bitmask, bit 0 = Monday … bit 6 = Sunday. */
fun weekdaysFromMask(mask: Int): List<String> =
    WEEKDAY_LABELS.filterIndexed { i, _ -> ((mask shr i) and 1) == 1 }

fun maskFromWeekdays(days: Set<Int>): Int = days.fold(0) { acc, d -> acc or (1 shl d) }

/** Accepts `2026/09/01`, `2026-09-01`, `2026-08-26T00:00:00` → `yyyy/MM/dd`. */
fun normalizeProgramDate(raw: String?): String {
    val today = LocalDate.now().format(PROGRAM_DATE_FORMAT)
    if (raw.isNullOrBlank()) return today
    val datePart = raw.substringBefore('T').replace('-', '/').trim()
    return runCatching { LocalDate.parse(datePart, PROGRAM_DATE_FORMAT).format(PROGRAM_DATE_FORMAT) }
        .getOrDefault(today)
}

fun isValidProgramDate(text: String): Boolean =
    runCatching { LocalDate.parse(text.trim(), PROGRAM_DATE_FORMAT) }.isSuccess

private val TIME_REGEX = Regex("^([01]?\\d|2[0-3]):[0-5]\\d$")
fun isValidTime(text: String): Boolean = TIME_REGEX.matches(text.trim())

fun WateringProgram.toDraft(): ProgramDraft = ProgramDraft(
    id = id,
    active = active,
    startDate = startDate,
    repetition = repetition,
    weekdays = (0..6).filter { ((weekdaysMask shr it) and 1) == 1 }.toSet(),
    times = times.ifEmpty { listOf("08:00") },
    channels = channels.sortedBy { it.id }.map { DraftChannel(it.id, it.minutes) },
)

fun ProgramDraft.toProgramaDto(): ProgramaDto = ProgramaDto(
    id = id,
    activo = active,
    comienzo = startDate.trim(),
    repetir = repetition.ordinal,
    semana = if (repetition == ProgramRepetition.BY_WEEKDAY) maskFromWeekdays(weekdays) else 0,
    horas = times.map { it.trim() }.filter { it.isNotBlank() }.map { HoraDto(it) },
    // Always sent sorted by channel id so the insertion order never affects the
    // real watering order on the device.
    canales = channels.sortedBy { it.channelId }.map { CanalDuracionDto(it.channelId, it.minutes) },
    proximoRiego = null,
)

fun DeviceConfigResponse.toDeviceControl(fallbackName: String): DeviceControl {
    val channelNames = configuracion?.canales.orEmpty().associate { it.id to it.nombre }
    val programador = configuracion?.programador
    return DeviceControl(
        name = programador?.nombre?.takeIf { it.isNotBlank() } ?: fallbackName,
        powerOn = programador?.activo ?: false,
        watering = estado?.regando ?: false,
        nextWatering = estado?.proximoRiego?.takeIf { it.isNotBlank() },
        boardConnected = estado?.placaConectada ?: false,
        raining = estado?.lloviendo ?: false,
        channels = configuracion?.canales.orEmpty()
            .sortedBy { it.id }
            .map { DeviceChannel(it.id, it.nombre, it.activo) },
        programs = programador?.programas.orEmpty().mapIndexed { index, p ->
            WateringProgram(
                id = p.id,
                index = index,
                active = p.activo,
                startDate = normalizeProgramDate(p.comienzo),
                repetition = ProgramRepetition.fromOrdinal(p.repetir),
                weekdaysMask = p.semana,
                weekdays = weekdaysFromMask(p.semana),
                times = p.horas.map { it.hora }.filter { it.isNotBlank() },
                nextWatering = p.proximoRiego?.takeIf { it.isNotBlank() },
                channels = p.canales
                    .sortedBy { it.id }
                    .map { c ->
                        ProgramChannel(
                            id = c.id,
                            name = channelNames[c.id] ?: "Canal ${c.id}",
                            minutes = c.duracion,
                        )
                    },
            )
        },
    )
}
