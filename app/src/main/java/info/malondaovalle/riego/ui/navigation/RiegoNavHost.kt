package info.malondaovalle.riego.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import info.malondaovalle.riego.data.auth.StartDestination
import info.malondaovalle.riego.ui.RiegoViewModelFactory
import info.malondaovalle.riego.ui.bootstrap.BootstrapViewModel
import info.malondaovalle.riego.ui.home.HomeScreen
import info.malondaovalle.riego.ui.login.LoginScreen
import info.malondaovalle.riego.ui.register.RegisterScreen
import info.malondaovalle.riego.ui.settings.SettingsScreen

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val SETTINGS = "settings"
}

const val REGISTERED_MESSAGE_KEY = "registered_message"

@Composable
fun RiegoNavHost() {
    val bootstrapViewModel: BootstrapViewModel = viewModel(factory = RiegoViewModelFactory)
    val startDestination by bootstrapViewModel.startDestination.collectAsStateWithLifecycle()

    val target = startDestination
    if (target == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()
    val start = when (target) {
        StartDestination.HOME -> Routes.HOME
        StartDestination.LOGIN -> Routes.LOGIN
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.LOGIN) { entry ->
            val handle = entry.savedStateHandle
            val registeredMessage by handle
                .getStateFlow<String?>(REGISTERED_MESSAGE_KEY, null)
                .collectAsStateWithLifecycle()
            LoginScreen(
                registeredMessage = registeredMessage,
                onRegisteredMessageShown = { handle.remove<String>(REGISTERED_MESSAGE_KEY) },
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegistered = { message ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(REGISTERED_MESSAGE_KEY, message)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
