package info.malondaovalle.riego.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import info.malondaovalle.riego.RiegoApplication
import info.malondaovalle.riego.ui.bootstrap.BootstrapViewModel
import info.malondaovalle.riego.ui.device.DeviceViewModel
import info.malondaovalle.riego.ui.home.HomeViewModel
import info.malondaovalle.riego.ui.login.LoginViewModel
import info.malondaovalle.riego.ui.register.RegisterViewModel
import info.malondaovalle.riego.ui.settings.SettingsViewModel

private fun application(extras: CreationExtras): RiegoApplication =
    (extras[APPLICATION_KEY] as RiegoApplication)

val RiegoViewModelFactory = viewModelFactory {
    initializer { BootstrapViewModel(application(this).authRepository) }
    initializer { LoginViewModel(application(this).authRepository) }
    initializer { RegisterViewModel(application(this).authRepository) }
    initializer {
        HomeViewModel(
            authRepository = application(this).authRepository,
            devicesRepository = application(this).devicesRepository,
            discoveryService = application(this).deviceDiscoveryService,
            deviceTcpClient = application(this).deviceTcpClient,
            commander = application(this).deviceCommander,
            userSocket = application(this).userSocket,
        )
    }
    initializer {
        DeviceViewModel(
            savedStateHandle = createSavedStateHandle(),
            commander = application(this).deviceCommander,
            userSocket = application(this).userSocket,
        )
    }
    initializer { SettingsViewModel(application(this).themePreferences) }
}
