package info.malondaovalle.riego.data.auth

/** Result of a user-triggered auth submit (register / login). */
sealed interface SubmitResult {
    data class Success(val message: String?) : SubmitResult
    data class Error(val message: String) : SubmitResult
}

/** Where the app should land on startup. */
enum class StartDestination { HOME, LOGIN }
