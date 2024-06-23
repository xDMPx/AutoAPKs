package com.xdmpx.autoapks.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.xdmpx.autoapks.datastore.ThemeType
import com.xdmpx.autoapks.settings.Settings

data class ColorSchemeEx(
    val colorScheme: ColorScheme,
    val annotatedText: Color,
)


private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

private val PureDarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color.Black,
    surface = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun AutoAPKsTheme(
    theme: ThemeType = ThemeType.SYSTEM, pureDarkTheme: Boolean = false,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true, content: @Composable () -> Unit
) {
    val darkTheme = when (theme) {
        ThemeType.SYSTEM, ThemeType.UNRECOGNIZED -> isSystemInDarkTheme()
        ThemeType.DARK -> true
        ThemeType.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            var dynamicTheme =
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme && pureDarkTheme) dynamicTheme =
                dynamicTheme.copy(background = Color.Black, surface = Color.Black)
            dynamicTheme
        }

        darkTheme && !pureDarkTheme -> DarkColorScheme
        darkTheme && pureDarkTheme -> PureDarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme, typography = Typography, content = content
    )
}

@Composable
fun getColorSchemeEx(): ColorSchemeEx {
    val settingsInstance = Settings.getInstance()
    val settings = settingsInstance.settingsState.collectAsState()
    val pureDarkTheme = settings.value.usePureDark
    val dynamicColor = settings.value.useDynamicColor
    val theme = settings.value.theme

    val darkTheme = when (theme) {
        ThemeType.SYSTEM, ThemeType.UNRECOGNIZED -> isSystemInDarkTheme()
        ThemeType.DARK -> true
        ThemeType.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            var dynamicTheme =
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme && pureDarkTheme) dynamicTheme =
                dynamicTheme.copy(background = Color.Black, surface = Color.Black)
            dynamicTheme
        }

        darkTheme && !pureDarkTheme -> DarkColorScheme
        darkTheme && pureDarkTheme -> PureDarkColorScheme
        else -> LightColorScheme
    }

    return if (darkTheme) {
        ColorSchemeEx(colorScheme, Color.Cyan)

    } else {
        ColorSchemeEx(colorScheme, Color.Blue)
    }
}