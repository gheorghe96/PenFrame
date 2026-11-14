package com.gheorghe.penframe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.gheorghe.penframe.ui.EditorScreen
import com.gheorghe.penframe.ui.ProjectsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PenFrameTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    var openProject by rememberSaveable { mutableStateOf<String?>(null) }
                    val id = openProject
                    if (id == null) ProjectsScreen(onOpen = { openProject = it })
                    else EditorScreen(projectId = id, onClose = { openProject = null })
                }
            }
        }
    }
}

@Composable
fun PenFrameTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        android.os.Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFCFBCFF))
        else -> lightColorScheme(primary = Color(0xFF5B3FD6))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
