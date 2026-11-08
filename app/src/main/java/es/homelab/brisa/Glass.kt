package es.homelab.brisa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Estilo «cristal líquido»: superficie translúcida con brillo en el borde superior y sombra suave.
 * La página se ve por debajo. (Android no permite desenfocar lo que dibuja Gecko debajo, así que
 * la translucidez está calibrada para que el texto siempre se lea.)
 */
@Composable
fun Modifier.glass(shape: Shape, dark: Boolean = isSystemInDarkTheme(), tint: Color? = null): Modifier {
    // Colores y degradados se calculan una vez, no en cada recomposición (#47)
    val p = androidx.compose.runtime.remember(dark, tint) {
        val base = tint ?: if (dark) Color(0xFF1C1C1E) else Color(0xFFF7F7F9)
        val edgeTop = if (dark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.95f)
        val edgeBottom = if (dark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.06f)
        Triple(base.copy(alpha = if (dark) 0.78f else 0.80f),
            Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.07f else 0.35f), Color.Transparent)),
            Brush.verticalGradient(listOf(edgeTop, edgeBottom)))
    }
    return this
        .shadow(18.dp, shape, ambientColor = GlassShadow, spotColor = GlassShadow)
        .clip(shape)
        .background(p.first)
        .background(p.second)
        .border(0.8.dp, p.third, shape)
}

/** Color de texto e iconos sobre el cristal. */
@Composable
fun glassContent(private: Boolean = false, dark: Boolean = isSystemInDarkTheme()) =
    if (private || dark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)

@Composable
fun glassContentDim(private: Boolean = false, dark: Boolean = isSystemInDarkTheme()) =
    if (private || dark) Color(0xFFAEAEB2) else Color(0xFF6C6C70)

/** Tinte de la barra en pestañas privadas: gris oscuro casi opaco, como en Safari. */
val PrivateGlass = Color(0xFF2C2C2E)
private val GlassShadow = Color.Black.copy(alpha = 0.25f)

@Suppress("unused") private val keepImports = MaterialTheme
