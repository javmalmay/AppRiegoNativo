package info.malondaovalle.riego.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import info.malondaovalle.riego.RiegoApplication
import info.malondaovalle.riego.ui.bootstrap.BootstrapViewModel
import info.malondaovalle.riego.ui.home.HomeViewModel
import info.malondaovalle.riego.ui.login.LoginViewModel
import info.malondaovalle.riego.ui.register.RegisterViewModel

private fun application(extras: androidx.lifecycle.viewmodel.CreationExtras): RiegoApplication =
    (extras[APPLICATION_KEY] as RiegoApplication)

val RiegoViewModelFactory = viewModelFactory {
    initializer { BootstrapViewModel(application(this).authRepository) }
    initializer { LoginViewModel(application(this).authRepository) }
    initializer { RegisterViewModel(application(this).authRepository) }
    initializer {
        HomeViewModel(
            authRepository = application(this).authRepository,
            devicesRepository = application(this).devicesRepository,
        )
    }
}
