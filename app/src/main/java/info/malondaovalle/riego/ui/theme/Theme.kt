package info.malondaovalle.riego.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = DarkPlantGreen,
    onPrimary = Color.White,
    primaryContainer = LightPlantGreen,
    onPrimaryContainer = DarkPlantGreen,
    secondary = PlantGreen,
    onSecondary = Color.White,
    secondaryContainer = LightPlantGreen,
    onSecondaryContainer = DarkPlantGreen,
    tertiary = WaterBlue,
    onTertiary = Color.White,
    background = LightWaterBlue,
    onBackground = MudGrey,
    surface = Color.White,
    onSurface = MudGrey,
    surfaceVariant = LightWaterBlue.copy(alpha = 0.5f),
    onSurfaceVariant = MudGrey,
)

private val DarkColors = darkColorScheme(
    primary = PlantGreen,
    onPrimary = Color.Black,
    primaryContainer = DarkPlantGreen,
    onPrimaryContainer = LightPlantGreen,
    secondary = LightPlantGreen,
    onSecondary = DarkPlantGreen,
    secondaryContainer = DarkPlantGreen,
    onSecondaryContainer = LightPlantGreen,
    tertiary = LightWaterBlue,
    onTertiary = DarkWaterBlue,
    background = Color(0xFF1A1C19),
    onBackground = GardenWhite,
    surface = Color(0xFF1A1C19),
    onSurface = GardenWhite,
    surfaceVariant = MudGrey,
    onSurfaceVariant = GardenWhite.copy(alpha = 0.7f),
)

@Composable
fun RiegoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
