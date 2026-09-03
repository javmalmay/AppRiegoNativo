package info.malondaovalle.riego.ui.device

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import info.malondaovalle.riego.R
import info.malondaovalle.riego.data.device.DeviceChannel
import info.malondaovalle.riego.data.device.DeviceControl
import info.malondaovalle.riego.data.device.MAX_DEVICE_CHANNELS
import info.malondaovalle.riego.data.device.PendingChannel
import info.malondaovalle.riego.data.device.ProgramChannel
import info.malondaovalle.riego.data.device.ProgramRepetition
import info.malondaovalle.riego.data.device.WateringProgram
import info.malondaovalle.riego.ui.RiegoViewModelFactory
import info.malondaovalle.riego.ui.theme.RiegoTheme

@Composable
fun DeviceScreen(
    onBack: () -> Unit,
    viewModel: DeviceViewModel = viewModel(factory = RiegoViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val draft = state.editingProgram
    val editorControl = state.control
    if (draft != null && editorControl != null) {
        BackHandler { viewModel.cancelProgramEdit() }
        ProgramEditor(
            draft = draft,
            deviceChannels = editorControl.channels,
            busy = state.programBusy,
            snackbarHostState = snackbarHostState,
            onChange = viewModel::updateDraft,
            onSave = viewModel::saveProgram,
            onCancel = viewModel::cancelProgramEdit,
        )
        return
    }

    val manual = state.manualWatering
    if (manual != null && editorControl != null) {
        BackHandler { viewModel.cancelManualWatering() }
        ManualWateringScreen(
            channels = manual,
            deviceChannels = editorControl.channels,
            busy = state.manualBusy,
            snackbarHostState = snackbarHostState,
            onChange = viewModel::updateManualWatering,
            onConfirm = viewModel::sendManualWatering,
            onCancel = viewModel::cancelManualWatering,
        )
        return
    }

    DeviceScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::load,
        onSetPower = viewModel::setPower,
        onRenameDevice = viewModel::setDeviceName,
        onStopWatering = viewModel::stopWatering,
        onCancelWateringChannel = viewModel::cancelWateringChannel,
        onAddChannel = viewModel::addChannel,
        onUpdateChannel = viewModel::updateChannel,
        onDeleteChannel = viewModel::deleteChannel,
        onAddProgram = viewModel::startAddProgram,
        onEditProgram = viewModel::startEditProgram,
        onRunProgram = viewModel::runProgramNow,
        onToggleProgramSelection = viewModel::toggleProgramSelection,
        onClearProgramSelection = viewModel::clearProgramSelection,
        onDeleteSelectedPrograms = viewModel::deleteSelectedPrograms,
        onStartManualWatering = viewModel::startManualWatering,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceScreenContent(
    state: DeviceUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSetPower: (Boolean) -> Unit,
    onRenameDevice: (String) -> Unit,
    onStopWatering: () -> Unit,
    onCancelWateringChannel: (id: Int) -> Unit,
    onAddChannel: (id: Int, name: String, active: Boolean) -> Unit,
    onUpdateChannel: (oldId: Int, newId: Int, name: String, active: Boolean) -> Unit,
    onDeleteChannel: (id: Int) -> Unit,
    onAddProgram: () -> Unit,
    onEditProgram: (id: Int) -> Unit,
    onRunProgram: (id: Int) -> Unit,
    onToggleProgramSelection: (id: Int) -> Unit,
    onClearProgramSelection: () -> Unit,
    onDeleteSelectedPrograms: () -> Unit,
    onStartManualWatering: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    val control = state.control

    if (renaming && control != null) {
        RenameDeviceDialog(
            currentName = control.name,
            onDismiss = { renaming = false },
            onConfirm = { newName ->
                onRenameDevice(newName)
                renaming = false
            },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(control?.name ?: "Dispositivo") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (control != null) {
                        IconButton(onClick = { renaming = true }, enabled = !state.nameBusy) {
                            Icon(Icons.Default.Edit, contentDescription = "Cambiar nombre")
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.loading && control == null -> CircularProgressIndicator()

                    state.error != null && control == null -> Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = state.error,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = onRetry) { Text("Reintentar") }
                    }

                    control != null -> DeviceContent(
                        control = control,
                        powerBusy = state.powerBusy,
                        channelBusy = state.channelBusy,
                        programBusy = state.programBusy,
                        wateringBusy = state.wateringBusy,
                        programSelection = state.programSelection,
                        onSetPower = onSetPower,
                        onStopWatering = onStopWatering,
                        onCancelWateringChannel = onCancelWateringChannel,
                        onAddChannel = onAddChannel,
                        onUpdateChannel = onUpdateChannel,
                        onDeleteChannel = onDeleteChannel,
                        onAddProgram = onAddProgram,
                        onEditProgram = onEditProgram,
                        onRunProgram = onRunProgram,
                        onToggleProgramSelection = onToggleProgramSelection,
                        onClearProgramSelection = onClearProgramSelection,
                        onDeleteSelectedPrograms = onDeleteSelectedPrograms,
                        onStartManualWatering = onStartManualWatering,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceContent(
    control: DeviceControl,
    powerBusy: Boolean,
    channelBusy: Boolean,
    programBusy: Boolean,
    wateringBusy: Boolean,
    programSelection: Set<Int>,
    onSetPower: (Boolean) -> Unit,
    onStopWatering: () -> Unit,
    onCancelWateringChannel: (id: Int) -> Unit,
    onAddChannel: (id: Int, name: String, active: Boolean) -> Unit,
    onUpdateChannel: (oldId: Int, newId: Int, name: String, active: Boolean) -> Unit,
    onDeleteChannel: (id: Int) -> Unit,
    onAddProgram: () -> Unit,
    onEditProgram: (id: Int) -> Unit,
    onRunProgram: (id: Int) -> Unit,
    onToggleProgramSelection: (id: Int) -> Unit,
    onClearProgramSelection: () -> Unit,
    onDeleteSelectedPrograms: () -> Unit,
    onStartManualWatering: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Programas", "Canales")
    val programSelectionMode = programSelection.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        if (!programSelectionMode) {
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }
        }

        when {
            programSelectionMode || selectedTab == 0 -> ProgramsTab(
                control = control,
                powerBusy = powerBusy,
                programBusy = programBusy,
                wateringBusy = wateringBusy,
                selection = programSelection,
                onSetPower = onSetPower,
                onStopWatering = onStopWatering,
                onCancelWateringChannel = onCancelWateringChannel,
                onAddProgram = onAddProgram,
                onEditProgram = onEditProgram,
                onRunProgram = onRunProgram,
                onToggleSelection = onToggleProgramSelection,
                onClearSelection = onClearProgramSelection,
                onDeleteSelected = onDeleteSelectedPrograms,
                onStartManualWatering = onStartManualWatering,
            )
            else -> ChannelsTab(
                channels = control.channels,
                busy = channelBusy,
                onAddChannel = onAddChannel,
                onUpdateChannel = onUpdateChannel,
                onDeleteChannel = onDeleteChannel,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramsTab(
    control: DeviceControl,
    powerBusy: Boolean,
    programBusy: Boolean,
    wateringBusy: Boolean,
    selection: Set<Int>,
    onSetPower: (Boolean) -> Unit,
    onStopWatering: () -> Unit,
    onCancelWateringChannel: (id: Int) -> Unit,
    onAddProgram: () -> Unit,
    onEditProgram: (id: Int) -> Unit,
    onRunProgram: (id: Int) -> Unit,
    onToggleSelection: (id: Int) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onStartManualWatering: () -> Unit,
) {
    val selectionMode = selection.isNotEmpty()
    var confirmDelete by remember { mutableStateOf(false) }
    var runConfirm by remember { mutableStateOf<WateringProgram?>(null) }

    BackHandler(enabled = selectionMode) { onClearSelection() }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!selectionMode && !control.powerRiego) {
                item {
                    AlertBanner(
                        text = "Sin alimentación de riego: el dispositivo no puede regar.",
                        icon = Icons.Default.Warning,
                        container = MaterialTheme.colorScheme.error,
                        content = MaterialTheme.colorScheme.onError,
                    )
                }
            }
            if (!selectionMode && control.raining) {
                item {
                    AlertBanner(
                        text = "Está lloviendo",
                        icon = Icons.Default.Umbrella,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            if (!selectionMode && (control.watering || control.pending.isNotEmpty())) {
                item {
                    WateringCard(
                        pending = control.pending,
                        busy = wateringBusy,
                        onStopAll = onStopWatering,
                        onCancelChannel = onCancelWateringChannel,
                    )
                }
            }
            item {
                DeviceStatusCard(
                    control = control,
                    powerBusy = powerBusy,
                    onSetPower = onSetPower
                )
            }
            if (!selectionMode) {
                item { ManualWateringCard(onStart = onStartManualWatering) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (selectionMode) {
                            "${selection.size} seleccionado(s)"
                        } else {
                            "Programas de riego"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (!selectionMode) {
                        TextButton(onClick = onAddProgram) { Text("Añadir") }
                    }
                }
            }
            if (control.programs.isEmpty()) {
                item {
                    Text(
                        text = "Este dispositivo no tiene programas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(control.programs, key = { it.id }) { program ->
                ProgramCard(
                    program = program,
                    selected = program.id in selection,
                    selectionMode = selectionMode,
                    runEnabled = !programBusy,
                    onClick = {
                        if (selectionMode) onToggleSelection(program.id) else onEditProgram(program.id)
                    },
                    onLongClick = { onToggleSelection(program.id) },
                    onRunNow = { runConfirm = program },
                )
            }
        }

        if (selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onClearSelection,
                    modifier = Modifier.weight(1f),
                ) { Text("Cancelar") }
                Button(
                    onClick = { confirmDelete = true },
                    enabled = !programBusy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Eliminar (${selection.size})") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar programas") },
            text = { Text("Se borrarán ${selection.size} programa(s) del dispositivo.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteSelected()
                    },
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }

    runConfirm?.let { program ->
        AlertDialog(
            onDismissRequest = { runConfirm = null },
            title = { Text("Regar ahora") },
            text = { Text("¿Iniciar ahora el riego del programa ${program.index + 1}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        runConfirm = null
                        onRunProgram(program.id)
                    },
                ) { Text("Regar") }
            },
            dismissButton = {
                TextButton(onClick = { runConfirm = null }) { Text("Cancelar") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelsTab(
    channels: List<DeviceChannel>,
    busy: Boolean,
    onAddChannel: (id: Int, name: String, active: Boolean) -> Unit,
    onUpdateChannel: (oldId: Int, newId: Int, name: String, active: Boolean) -> Unit,
    onDeleteChannel: (id: Int) -> Unit,
) {
    var editing by remember { mutableStateOf<DeviceChannel?>(null) }
    var adding by remember { mutableStateOf(false) }
    var selectedForDelete by remember { mutableStateOf<DeviceChannel?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val usedIds = channels.mapTo(HashSet()) { it.id }

    // Keep the selection valid if the list changes underneath.
    val selected = selectedForDelete?.let { sel -> channels.firstOrNull { it.id == sel.id } }

    BackHandler(enabled = selected != null) { selectedForDelete = null }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (channels.isEmpty()) {
                item {
                    Text(
                        text = "Este dispositivo no tiene canales.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(channels, key = { it.id }) { channel ->
                ChannelRow(
                    channel = channel,
                    selected = selected?.id == channel.id,
                    onClick = { editing = channel },
                    onLongClick = {
                        selectedForDelete = if (selected?.id == channel.id) null else channel
                    },
                )
            }
        }

        if (selected != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = { selectedForDelete = null },
                    modifier = Modifier.weight(1f),
                ) { Text("Cancelar") }
                Button(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Eliminar canal") }
            }
        } else {
            val atLimit = channels.size >= MAX_DEVICE_CHANNELS
            Button(
                onClick = { adding = true },
                enabled = !atLimit && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(
                    if (atLimit) {
                        "Máximo $MAX_DEVICE_CHANNELS canales"
                    } else {
                        "Agregar canal (${channels.size}/$MAX_DEVICE_CHANNELS)"
                    }
                )
            }
        }
    }

    if (confirmDelete && selected != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar canal") },
            text = {
                Text("¿Eliminar el canal \"${selected.name}\" (ID ${selected.id})?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteChannel(selected.id)
                        confirmDelete = false
                        selectedForDelete = null
                    },
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }

    editing?.let { channel ->
        ChannelDialog(
            title = "Editar canal",
            initialId = channel.id,
            initialName = channel.name,
            initialActive = channel.active,
            takenIds = usedIds - channel.id,
            onDismiss = { editing = null },
            onConfirm = { id, name, active ->
                onUpdateChannel(channel.id, id, name, active)
                editing = null
            },
        )
    }
    if (adding) {
        ChannelDialog(
            title = "Nuevo canal",
            initialId = (1..MAX_DEVICE_CHANNELS).firstOrNull { it !in usedIds },
            initialName = "",
            initialActive = true,
            takenIds = usedIds,
            onDismiss = { adding = false },
            onConfirm = { id, name, active ->
                onAddChannel(id, name, active)
                adding = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: DeviceChannel,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    OutlinedCard(
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.error, shape)
                } else {
                    Modifier
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = channel.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "ID de canal: ${channel.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(
                text = if (channel.active) "Activo" else "Inactivo",
                color = if (channel.active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun ChannelDialog(
    title: String,
    initialId: Int?,
    initialName: String,
    initialActive: Boolean,
    takenIds: Set<Int>,
    onDismiss: () -> Unit,
    onConfirm: (id: Int, name: String, active: Boolean) -> Unit,
) {
    var id by remember { mutableIntStateOf(initialId ?: 1) }
    var name by remember { mutableStateOf(initialName) }
    var active by remember { mutableStateOf(initialActive) }

    val idError = when {
        id in takenIds -> "Ya hay un canal con el ID $id"
        else -> null
    }
    val canSave = idError == null && name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ID de canal",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (idError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(
                            onClick = { if (id > 1) id-- },
                            enabled = id > 1
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Disminuir")
                        }

                        Text(
                            text = id.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (idError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )

                        IconButton(
                            onClick = { if (id < MAX_DEVICE_CHANNELS) id++ },
                            enabled = id < MAX_DEVICE_CHANNELS
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Aumentar")
                        }
                    }
                    if (idError != null) {
                        Text(
                            text = idError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Activo", modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(id, name.trim(), active) },
                enabled = canSave,
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun RenameDeviceDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nombre del dispositivo") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank() && name.trim() != currentName,
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun DeviceStatusCard(
    control: DeviceControl,
    powerBusy: Boolean,
    onSetPower: (Boolean) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Activar",
                    style = MaterialTheme.typography.titleMedium
                )
                if (powerBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Switch(checked = control.powerOn, onCheckedChange = onSetPower)
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Column {
                Text(
                    text = "Próximo riego",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = control.nextWatering ?: "Sin programación",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            
        }
    }
}

@Composable
private fun ManualWateringCard(onStart: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.WaterDrop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text("Riego manual", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Riega los canales que elijas ahora mismo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onStart) { Text("Iniciar") }
        }
    }
}

@Composable
private fun AlertBanner(
    text: String,
    icon: ImageVector,
    container: Color,
    content: Color,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = container,
            contentColor = content,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WateringCard(
    pending: List<PendingChannel>,
    busy: Boolean,
    onStopAll: () -> Unit,
    onCancelChannel: (id: Int) -> Unit,
) {
    // Local countdown: seed from the device, then tick the active (first) channel
    // down every second until the next GETCONFIG refresh re-seeds us.
    var remaining by remember(pending) {
        mutableStateOf(pending.associate { it.channelId to it.secondsRemaining })
    }
    LaunchedEffect(pending) {
        val activeId = pending.firstOrNull()?.channelId ?: return@LaunchedEffect
        while (true) {
            delay(1_000)
            remaining = remaining.mapValues { (id, secs) ->
                if (id == activeId) (secs - 1).coerceAtLeast(0) else secs
            }
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Regando ahora",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
            }

            if (pending.isEmpty()) {
                Text(
                    text = "El dispositivo está regando.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                pending.forEachIndexed { index, channel ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = channel.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (index == 0) "En curso" else "En espera",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = formatSeconds(remaining[channel.channelId] ?: channel.secondsRemaining),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        IconButton(
                            onClick = { onCancelChannel(channel.channelId) },
                            enabled = !busy,
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar canal")
                        }
                    }
                }
            }

            Button(
                onClick = onStopAll,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text("Cancelar todo el riego")
            }
        }
    }
}

private fun formatSeconds(total: Int): String {
    val s = total.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ProgramCard(
    program: WateringProgram,
    selected: Boolean,
    selectionMode: Boolean,
    runEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRunNow: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    OutlinedCard(
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.error, shape)
                } else {
                    Modifier
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Programa ${program.index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (selectionMode) {
                    Icon(
                        imageVector = if (selected) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.RadioButtonUnchecked
                        },
                        contentDescription = if (selected) "Seleccionado" else "Sin seleccionar",
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                } else {
                    StatusPill(
                        text = if (program.active) "Activo" else "Inactivo",
                        color = if (program.active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            val next = program.nextWatering
                ?: program.times.takeIf { it.isNotEmpty() }?.joinToString(", ")
            if (next != null) {
                InfoLine("Próximo riego", next)
            }
            InfoLine("Repetición", program.repetition.label)
            if (program.repetition == ProgramRepetition.BY_WEEKDAY && program.weekdays.isNotEmpty()) {
                InfoLine("Días", program.weekdays.joinToString(" "))
            }

            Text(
                text = "Canales",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 2.dp),
            )
            program.channels.forEach { channel ->
                Text(
                    text = "• ${channel.name} — ${channel.minutes} min",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (!selectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onRunNow, enabled = runEnabled) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("Regar ahora", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge, // Increased size
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(percent = 50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun DeviceScreenPreview() {
    RiegoTheme {
        DeviceScreenContent(
            state = DeviceUiState(
                loading = false,
                control = DeviceControl(
                    name = "Riego-Casa",
                    powerOn = true,
                    powerRiego = true,
                    watering = true,
                    nextWatering = "01/09/2026 09:00",
                    boardConnected = true,
                    raining = false,
                    channels = listOf(
                        DeviceChannel(1, "Palmera", true),
                        DeviceChannel(2, "Goteo jardinera", true),
                        DeviceChannel(6, "Madroño", false),
                    ),
                    pending = listOf(
                        PendingChannel(3, "Seto", 95),
                        PendingChannel(4, "Césped", 360),
                        PendingChannel(5, "Rosales", 120),
                    ),
                    programs = listOf(
                        WateringProgram(
                            id = 1,
                            index = 0,
                            active = true,
                            startDate = "2026/09/01",
                            repetition = ProgramRepetition.BY_WEEKDAY,
                            weekdaysMask = 0b1001001,
                            weekdays = listOf("L", "J", "D"),
                            times = listOf("21:00"),
                            nextWatering = "01/09/2026 21:00",
                            channels = listOf(
                                ProgramChannel(1, "Palmera", 5),
                                ProgramChannel(3, "Olmo", 7),
                            ),
                        ),
                        WateringProgram(
                            id = 2,
                            index = 1,
                            active = false,
                            startDate = "2026/09/01",
                            repetition = ProgramRepetition.DAILY,
                            weekdaysMask = 0,
                            weekdays = emptyList(),
                            times = listOf("09:00"),
                            nextWatering = null,
                            channels = listOf(ProgramChannel(2, "Goteo jardinera", 3)),
                        ),
                    ),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onRetry = {},
            onSetPower = {},
            onRenameDevice = {},
            onStopWatering = {},
            onCancelWateringChannel = {},
            onAddChannel = { _, _, _ -> },
            onUpdateChannel = { _, _, _, _ -> },
            onDeleteChannel = {},
            onAddProgram = {},
            onEditProgram = {},
            onRunProgram = {},
            onToggleProgramSelection = {},
            onClearProgramSelection = {},
            onDeleteSelectedPrograms = {},
            onStartManualWatering = {},
        )
    }
}
