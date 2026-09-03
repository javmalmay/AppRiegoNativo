package info.malondaovalle.riego.ui.device

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import info.malondaovalle.riego.R
import info.malondaovalle.riego.data.device.DeviceChannel
import info.malondaovalle.riego.data.device.DraftChannel
import info.malondaovalle.riego.ui.theme.RiegoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualWateringScreen(
    channels: List<DraftChannel>,
    deviceChannels: List<DeviceChannel>,
    busy: Boolean,
    snackbarHostState: SnackbarHostState,
    onChange: (List<DraftChannel>) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val canStart = !busy && channels.isNotEmpty() && channels.all { it.minutes > 0 }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Riego manual") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
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
                            onClick = onConfirm,
                            enabled = canStart,
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            )
                        ) {
                            Text("Regar", fontWeight = FontWeight.Bold)
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
                Text(
                    text = "Elige los canales y su duración para regar ahora mismo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChannelsSection(
                    selected = channels,
                    deviceChannels = deviceChannels,
                    onChange = onChange,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ManualWateringScreenPreview() {
    RiegoTheme {
        ManualWateringScreen(
            channels = listOf(
                DraftChannel(1, 5),
                DraftChannel(2, 10)
            ),
            deviceChannels = listOf(
                DeviceChannel(1, "Palmera", true),
                DeviceChannel(2, "Goteo jardinera", true),
                DeviceChannel(3, "Césped", true)
            ),
            busy = false,
            snackbarHostState = remember { SnackbarHostState() },
            onChange = {},
            onConfirm = {},
            onCancel = {}
        )
    }
}
