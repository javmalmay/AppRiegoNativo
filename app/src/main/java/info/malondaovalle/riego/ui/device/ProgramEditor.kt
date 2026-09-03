package info.malondaovalle.riego.ui.device

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import info.malondaovalle.riego.R
import info.malondaovalle.riego.data.device.DeviceChannel
import info.malondaovalle.riego.data.device.DraftChannel
import info.malondaovalle.riego.data.device.ProgramDraft
import info.malondaovalle.riego.data.device.ProgramRepetition
import info.malondaovalle.riego.data.device.WEEKDAY_LABELS
import info.malondaovalle.riego.data.device.isValidProgramDate
import info.malondaovalle.riego.data.device.isValidTime
import info.malondaovalle.riego.ui.theme.RiegoTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramEditor(
    draft: ProgramDraft,
    deviceChannels: List<DeviceChannel>,
    busy: Boolean,
    snackbarHostState: SnackbarHostState,
    onChange: (ProgramDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val timesValid = draft.times.isNotEmpty() && draft.times.all { isValidTime(it) }
    val daysOk = draft.repetition != ProgramRepetition.BY_WEEKDAY || draft.weekdays.isNotEmpty()
    val canSave = !busy &&
        isValidProgramDate(draft.startDate) &&
        timesValid &&
        draft.channels.isNotEmpty() &&
        daysOk

    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (draft.isNew) "Nuevo programa" else "Editar programa") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar")
                    }
                },
                actions = {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(
                            onClick = onSave,
                            enabled = canSave,
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = Color(0xFF2E7D32).copy(alpha = 0.1f),
                                contentColor = Color(0xFF2E7D32)
                            )
                        ) {
                            Text("Guardar", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.login_irrigation_hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.4f,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Estado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                StatusPill(
                                    text = if (draft.active) "Habilitado" else "Pausado",
                                    color = if (draft.active) Color(0xFF2E7D32) else Color.Gray
                                )
                            }
                            Switch(
                                checked = draft.active,
                                onCheckedChange = { onChange(draft.copy(active = it)) },
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        RepetitionField(
                            value = draft.repetition,
                            onChange = { onChange(draft.copy(repetition = it)) },
                        )

                        if (draft.repetition == ProgramRepetition.BY_WEEKDAY) {
                            WeekdayPicker(
                                selected = draft.weekdays,
                                onToggle = { day ->
                                    val next = if (day in draft.weekdays) draft.weekdays - day else draft.weekdays + day
                                    onChange(draft.copy(weekdays = next))
                                },
                            )
                        }

                        OutlinedTextField(
                            value = draft.startDate,
                            onValueChange = {},
                            label = { Text("Fecha de inicio") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = "Seleccionar fecha")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )
                    }
                }

                HoursSection(
                    times = draft.times,
                    onChange = { onChange(draft.copy(times = it)) },
                )

                ChannelsSection(
                    selected = draft.channels,
                    deviceChannels = deviceChannels,
                    onChange = { onChange(draft.copy(channels = it)) },
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = draft.startDate.toEpochMillis() ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onChange(draft.copy(startDate = it.toDateString()))
                    }
                    showDatePicker = false
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

private fun String.toEpochMillis(): Long? = runCatching {
    LocalDate.parse(this.trim(), dateFormatter)
        .atStartOfDay(ZoneId.of("UTC"))
        .toInstant()
        .toEpochMilli()
}.getOrNull()

private fun Long.toDateString(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.of("UTC"))
        .toLocalDate()
        .format(dateFormatter)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepetitionField(value: ProgramRepetition, onChange: (ProgramRepetition) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Repetición") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ProgramRepetition.entries.forEach { rep ->
                DropdownMenuItem(
                    text = { Text(rep.label) },
                    onClick = {
                        expanded = false
                        onChange(rep)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekdayPicker(selected: Set<Int>, onToggle: (Int) -> Unit) {
    Column {
        Text("Días de la semana", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WEEKDAY_LABELS.forEachIndexed { index, label ->
                FilterChip(
                    selected = index in selected,
                    onClick = { onToggle(index) },
                    label = { Text(label) },
                    shape = MaterialTheme.shapes.small
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HoursSection(times: List<String>, onChange: (List<String>) -> Unit) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDeleteIndex by remember { mutableStateOf<Int?>(null) }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Horas de riego",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        editingIndex = null
                        showTimePicker = true
                    },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Añadir", modifier = Modifier.padding(start = 4.dp), fontWeight = FontWeight.Bold)
                }
            }

            if (times.isEmpty()) {
                Text(
                    "No hay horas programadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            times.forEachIndexed { index, time ->
                ListItem(
                    headlineContent = { Text(time, style = MaterialTheme.typography.bodyLarge) },
                    leadingContent = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { confirmDeleteIndex = index }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        editingIndex = index
                        showTimePicker = true
                    }
                )
                if (index < times.size - 1) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }

    if (showTimePicker) {
        val initialTime = (if (editingIndex != null) times[editingIndex!!] else "08:00").split(":")
        val timePickerState = rememberTimePickerState(
            initialHour = initialTime[0].toInt(),
            initialMinute = initialTime[1].toInt(),
            is24Hour = true
        )
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            onConfirm = {
                val newTime = String.format(Locale.US, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                val newList = times.toMutableList()
                if (editingIndex != null) {
                    newList[editingIndex!!] = newTime
                } else {
                    newList.add(newTime)
                }
                onChange(newList.sorted())
                showTimePicker = false
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }

    confirmDeleteIndex?.let { idx ->
        AlertDialog(
            onDismissRequest = { confirmDeleteIndex = null },
            title = { Text("Quitar hora") },
            text = { Text("¿Quitar la hora ${times.getOrNull(idx).orEmpty()}?") },
            confirmButton = {
                TextButton(onClick = {
                    onChange(times.toMutableList().also { if (idx in it.indices) it.removeAt(idx) })
                    confirmDeleteIndex = null
                }) { Text("Quitar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteIndex = null }) { Text("Cancelar") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .width(IntrinsicSize.Min),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Seleccionar hora",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                )
                content()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) { Text("Cancelar") }
                    TextButton(onClick = onConfirm) { Text("OK") }
                }
            }
        }
    }
}

/** Reused by [ProgramEditor] and the manual-watering screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelsSection(
    selected: List<DraftChannel>,
    deviceChannels: List<DeviceChannel>,
    onChange: (List<DraftChannel>) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var selectionIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val names = deviceChannels.associate { it.id to it.name }
    // Always show/keep channels ordered by id — insertion order must not matter.
    val rows = selected.sortedBy { it.channelId }
    val available = deviceChannels
        .filter { ch -> selected.none { it.channelId == ch.id } }
        .sortedBy { it.id }
    val selectionMode = selectionIds.isNotEmpty()

    fun toggle(channelId: Int) {
        selectionIds = if (channelId in selectionIds) selectionIds - channelId else selectionIds + channelId
    }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selectionMode) "${selectionIds.size} seleccionado(s)" else "Canales y duración",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (selectionMode) {
                    TextButton(
                        onClick = { selectionIds = emptySet() },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Quitar", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box {
                        TextButton(
                            onClick = { pickerOpen = true },
                            enabled = available.isNotEmpty(),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Añadir", modifier = Modifier.padding(start = 4.dp), fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                            available.forEach { ch ->
                                DropdownMenuItem(
                                    text = { Text("${ch.name} (ID ${ch.id})") },
                                    onClick = {
                                        pickerOpen = false
                                        onChange((selected + DraftChannel(ch.id, 5)).sortedBy { it.channelId })
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (rows.isEmpty()) {
                Text(
                    "Ningún canal asignado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Text(
                    "Mantén pulsado un canal para seleccionarlo y quitarlo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            rows.forEachIndexed { index, channel ->
                val isSel = channel.channelId in selectionIds
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { if (selectionMode) toggle(channel.channelId) },
                            onLongClick = { toggle(channel.channelId) },
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = names[channel.channelId] ?: "Canal ${channel.channelId}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (selectionMode) {
                        Icon(
                            imageVector = if (isSel) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        MinutesStepper(
                            minutes = channel.minutes,
                            onChange = { m ->
                                onChange(
                                    rows.map {
                                        if (it.channelId == channel.channelId) it.copy(minutes = m) else it
                                    },
                                )
                            },
                        )
                    }
                }
                if (index < rows.size - 1) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Quitar canales") },
            text = { Text("Se quitarán ${selectionIds.size} canal(es) del programa.") },
            confirmButton = {
                TextButton(onClick = {
                    onChange(rows.filterNot { it.channelId in selectionIds })
                    selectionIds = emptySet()
                    confirmDelete = false
                }) { Text("Quitar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
internal fun MinutesStepper(minutes: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { if (minutes > STEPPER_MIN) onChange(minutes - 1) },
            enabled = minutes > STEPPER_MIN,
        ) { Icon(Icons.Default.Remove, contentDescription = "Menos") }

        Text(
            text = minutes.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp),
        )

        IconButton(
            onClick = { if (minutes < STEPPER_MAX) onChange(minutes + 1) },
            enabled = minutes < STEPPER_MAX,
        ) { Icon(Icons.Default.Add, contentDescription = "Más") }

        Text(
            text = "min",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val STEPPER_MIN = 1
private const val STEPPER_MAX = 240

@Preview(showBackground = true)
@Composable
private fun ProgramEditorPreview() {
    val sampleDraft = ProgramDraft(
        id = 1,
        active = true,
        startDate = "2024/05/20",
        repetition = ProgramRepetition.DAILY,
        weekdays = emptySet(),
        times = listOf("08:00", "20:00"),
        channels = listOf(
            DraftChannel(channelId = 1, minutes = 10),
            DraftChannel(channelId = 2, minutes = 5)
        )
    )
    val sampleChannels = listOf(
        DeviceChannel(id = 1, name = "Césped Frontal", active = true),
        DeviceChannel(id = 2, name = "Huerto", active = true),
        DeviceChannel(id = 3, name = "Goteo Macetas", active = true)
    )
    RiegoTheme {
        ProgramEditor(
            draft = sampleDraft,
            deviceChannels = sampleChannels,
            busy = false,
            snackbarHostState = remember { SnackbarHostState() },
            onChange = {},
            onSave = {},
            onCancel = {}
        )
    }
}
