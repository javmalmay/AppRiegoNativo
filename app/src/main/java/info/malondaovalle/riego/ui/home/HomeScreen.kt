package info.malondaovalle.riego.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import info.malondaovalle.riego.R
import info.malondaovalle.riego.data.devices.Device
import info.malondaovalle.riego.data.discovery.DiscoveredDevice
import info.malondaovalle.riego.data.discovery.normalizeMac
import info.malondaovalle.riego.ui.RiegoViewModelFactory
import info.malondaovalle.riego.ui.theme.RiegoTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val lastSeenFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

@Composable
fun HomeScreen(
    onLoggedOut: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDevice: (deviceId: Int, name: String?, ip: String?, port: Int?) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = RiegoViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionMessage()
        }
    }

    BackHandler(enabled = state.selectionMode) { viewModel.exitSelection() }

    HomeScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onRetry = viewModel::loadDevices,
        onOpenSettings = onOpenSettings,
        onLogout = viewModel::logout,
        onAddDiscovered = viewModel::addDiscoveredDevice,
        onDeviceClick = { device ->
            if (state.selectionMode) {
                viewModel.onDeviceClick(device.id)
            } else {
                val local = state.discovered.firstOrNull {
                    normalizeMac(it.mac) == normalizeMac(device.macAddress)
                }
                onOpenDevice(device.id, device.name, local?.ip, local?.port)
            }
        },
        onDeviceLongPress = { device -> viewModel.onDeviceLongPress(device.id) },
        onExitSelection = viewModel::exitSelection,
        onDeleteSelected = viewModel::deleteSelected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    onAddDiscovered: (DiscoveredDevice) -> Unit,
    onDeviceClick: (Device) -> Unit,
    onDeviceLongPress: (Device) -> Unit,
    onExitSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar dispositivos") },
            text = {
                Text("Se quitarán ${state.selectedIds.size} dispositivo(s) de tu cuenta.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteSelected()
                    },
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.selectionMode) {
                            "${state.selectedIds.size} seleccionados"
                        } else {
                            "Dispositivos"
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    if (state.selectionMode) {
                        IconButton(onClick = onExitSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar selección")
                        }
                    }
                },
                actions = {
                    if (state.selectionMode) {
                        if (state.deleting) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(
                                onClick = { showDeleteDialog = true },
                                enabled = state.selectedIds.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                            }
                        }
                    } else {
                        IconButton(
                            onClick = onRetry,
                            enabled = !state.loading && !state.discovering,
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                        }
                        AvatarMenu(
                            username = state.username,
                            onOpenSettings = onOpenSettings,
                            onLogout = onLogout,
                        )
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
                    .padding(innerPadding),
            ) {
                if (state.discovering) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (state.newDevices.isNotEmpty() && !state.selectionMode) {
                    NewDevicesBanner(
                        devices = state.newDevices,
                        addingMacs = state.addingMacs,
                        onAdd = onAddDiscovered,
                    )
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        state.loading && state.devices.isEmpty() -> CircularProgressIndicator()

                        state.error != null && state.devices.isEmpty() -> ErrorState(
                            message = state.error,
                            onRetry = onRetry,
                        )

                        state.devices.isEmpty() && state.newDevices.isEmpty() -> Text(
                            text = "No tienes dispositivos asociados",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )

                        else -> DeviceGrid(
                            devices = state.devices,
                            localMacs = state.localMacs,
                            selectionMode = state.selectionMode,
                            selectedIds = state.selectedIds,
                            onClick = onDeviceClick,
                            onLongPress = onDeviceLongPress,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceGrid(
    devices: List<Device>,
    localMacs: Set<String>,
    selectionMode: Boolean,
    selectedIds: Set<Int>,
    onClick: (Device) -> Unit,
    onLongPress: (Device) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(devices, key = { it.id }) { device ->
            DeviceCard(
                device = device,
                isLocal = normalizeMac(device.macAddress) in localMacs,
                selectionMode = selectionMode,
                selected = device.id in selectedIds,
                onClick = { onClick(device) },
                onLongClick = { onLongPress(device) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceCard(
    device: Device,
    isLocal: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        val cardShape = RoundedCornerShape(12.dp)
        ElevatedCard(
            shape = cardShape,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, cardShape)
                    } else {
                        Modifier
                    }
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (device.isOnline) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                },
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = if (device.isOnline) 6.dp else 2.dp,
            ),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 24.dp),
                )

                Text(
                    text = device.macAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Tag(
                    text = if (isLocal) "Local" else "Remoto",
                    color = if (isLocal) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                if (!device.isOnline) {
                    Text(
                        text = device.lastSeenAt
                            ?.let { "Últ. vez: ${it.format(lastSeenFormatter)}" }
                            ?: "Sin conexión",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Activo ahora",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        when {
            selectionMode -> Icon(
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
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(24.dp),
            )

            device.isOnline -> Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Conectado",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(24.dp),
            )
        }
    }
}

@Composable
private fun NewDevicesBanner(
    devices: List<DiscoveredDevice>,
    addingMacs: Set<String>,
    onAdd: (DiscoveredDevice) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${devices.size} dispositivo(s) nuevo(s) en tu red",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Contraer" else "Expandir",
                )
            }

            if (expanded) {
                Text(
                    text = "Detectados en la red local pero no asociados a tu cuenta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                devices.forEach { device ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = buildString {
                                    append(device.mac)
                                    if (device.ip.isNotBlank()) append(" · ${device.ip}")
                                    device.port?.let { append(":$it") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (normalizeMac(device.mac) in addingMacs) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            TextButton(onClick = { onAdd(device) }) { Text("Agregar") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun AvatarMenu(
    username: String,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (username.isBlank()) "Cuenta" else "Cuenta de $username"

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Avatar(username = username)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Ajustes") },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
            )
            DropdownMenuItem(
                text = { Text("Cerrar sesión") },
                onClick = {
                    expanded = false
                    onLogout()
                },
            )
        }
    }
}

@Composable
private fun Avatar(username: String) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsFrom(username),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
        )
    }
}

/** 1–2 letter initials from a username ("javier.malonda" -> "JM", "ana" -> "AN"). */
internal fun initialsFrom(username: String): String {
    val parts = username.trim().split(Regex("[\\s._@\\-]+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    RiegoTheme {
        HomeScreenContent(
            state = HomeUiState(
                username = "javier.malonda",
                devices = listOf(
                    Device(
                        id = 1,
                        macAddress = "F5:28:D2:31:1B:CF",
                        name = "Dispositivo de pruebas inexistente",
                        isOnline = true,
                        lastSeenAt = LocalDateTime.of(2026, 8, 30, 17, 50),
                    ),
                    Device(
                        id = 2,
                        macAddress = "AA:BB:CC:DD:EE:01",
                        name = "Valvula Jardin",
                        isOnline = false,
                        lastSeenAt = null,
                    ),
                ),
                localMacs = setOf("F528D2311BCF"),
                newDevices = listOf(
                    DiscoveredDevice(
                        name = "Aspersor terraza",
                        ip = "192.168.1.42",
                        mac = "AA:BB:CC:DD:EE:99",
                        port = 9123,
                    ),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onRetry = {},
            onOpenSettings = {},
            onLogout = {},
            onAddDiscovered = {},
            onDeviceClick = {},
            onDeviceLongPress = {},
            onExitSelection = {},
            onDeleteSelected = {},
        )
    }
}
