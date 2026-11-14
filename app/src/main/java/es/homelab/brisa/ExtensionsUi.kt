@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package es.homelab.brisa

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension

/** Icono de una extensión (o de su botón). Mientras carga, o si no tiene, una pieza de puzle. */
@Composable
fun ExtIcon(img: org.mozilla.geckoview.Image?, size: Dp = 28.dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    var bmp by remember(img) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(img) { Extensions.icon(img, px) { bmp = it } }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), null, Modifier.size(size).clip(RoundedCornerShape(6.dp)))
    else Icon(Icons.Outlined.Extension, null, Modifier.size(size))
}

/**
 * Botones de las extensiones (los que tienen botón o ventanita), para el menú «aA».
 * Tocar uno hace lo mismo que en Firefox: abre su ventanita o le avisa del toque.
 */
@Composable
fun ExtensionActions(onAction: () -> Unit) {
    val shown = Extensions.list.filter { it.metaData.enabled && Extensions.actions[it.id] != null }
    if (shown.isEmpty()) return
    // En varias líneas: ninguno queda cortado sin que se note (#80)
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEach { e ->
            val a = Extensions.actionFor(e.id) ?: return@forEach
            val name = a.title ?: e.metaData.name ?: e.id
            FilledTonalButton({
                // #67: Gecko abre la ventanita en la pestaña con foco; la hoja «aA» se lo ha quitado a la vista
                Tab.visible?.session?.takeIf { it.isOpen }?.setFocused(true)
                onAction(); a.click()
            }, enabled = a.enabled != false,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                ExtIcon(a.icon ?: e.metaData.icon, 20.dp)
                Text(name, Modifier.padding(start = 8.dp).widthIn(max = 140.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                a.badgeText?.takeIf { it.isNotBlank() }?.let {
                    Text(it, Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp), color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp)
                }
            }
        }
    }
}

/** La ventanita de una extensión (su «popup»), en una hoja inferior con su propia vista de Gecko. */
@Composable
fun ExtensionPopupSheet(p: Extensions.Popup) {
    val ctx = LocalContext.current
    val view = remember(p) { GeckoView(ctx).apply { setSession(p.session) } }
    DisposableEffect(p) { onDispose { runCatching { view.releaseSession() } } }
    ModalBottomSheet(onDismissRequest = { Extensions.closePopup() }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ExtIcon(p.ext.metaData.icon, 22.dp)
            Text(p.ext.metaData.name ?: p.ext.id, Modifier.padding(start = 10.dp).weight(1f), fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // #79: según la pantalla (en horizontal no se corta); la web de la ventanita hace scroll dentro
        val h = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.7f).coerceIn(200f, 560f)
        AndroidView({ view }, Modifier.fillMaxWidth().height(h.dp).padding(top = 8.dp))
    }
}

/** Ajustes → Extensiones (y menú «aA»): las instaladas, las recomendadas y cómo instalar más. */
@Composable
fun ExtensionsSheet(dismiss: () -> Unit, openTab: (String) -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Extensions.refresh() }
    var link by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<WebExtension?>(null) }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("Extensiones", style = MaterialTheme.typography.titleLarge)
            Text("Solo se instalan extensiones firmadas por Mozilla. Antes de instalar, Brisa te enseña qué permisos piden.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

            if (Extensions.list.isNotEmpty()) Text("Instaladas", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            Extensions.list.forEach { e ->
                val m = e.metaData
                var open by remember(e.id) { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open }.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExtIcon(m.icon, 32.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(m.name ?: e.id, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.version, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val rec = Extensions.recommended(e)
                                Text(if (rec) "Recomendada" else "Experimental", Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50))
                                    .background(if (rec) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 1.dp), fontSize = 11.sp,
                                    color = if (rec) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                        Switch(m.enabled, { Extensions.setEnabled(e, it) })
                    }
                    if (open) Column(Modifier.padding(start = 44.dp, top = 4.dp)) {
                        m.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        if (!Extensions.recommended(e)) Text("Experimental: no se ha probado en Brisa. Puede que alguna parte no funcione.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Permitir en pestañas privadas", Modifier.weight(1f), fontSize = 14.sp)
                            Switch(m.allowedInPrivateBrowsing, { Extensions.setPrivate(e, it) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (m.optionsPageUrl != null) OutlinedButton({ dismiss(); Extensions.openOptions(e) }) { Text("Opciones") }
                            TextButton({ confirmRemove = e }) { Text("Desinstalar", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }

            val missing = Extensions.RECOMMENDED.filterKeys { id -> Extensions.list.none { it.id == id } }
            if (missing.isNotEmpty()) {
                Text("Recomendadas", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                missing.forEach { (_, v) ->
                    val (name, slug, what) = v
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Extension, null, Modifier.size(32.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(name, fontWeight = FontWeight.Medium)
                            Text(what, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton({ Extensions.install(ctx, "https://addons.mozilla.org/android/addon/$slug/") }) { Text("Instalar") }
                    }
                }
            }

            Text("Más extensiones", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            OutlinedButton({ dismiss(); openTab(Extensions.AMO) }, Modifier.fillMaxWidth()) { Text("Buscar en addons.mozilla.org") }
            Text("Toca «Añadir a Firefox» en la ficha de la extensión y Brisa la instala.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                label = { Text("O pega el enlace de la extensión") },
                placeholder = { Text("https://addons.mozilla.org/…/addon/…") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { if (link.isNotBlank()) Extensions.install(ctx, link) { ok -> if (ok) link = "" } }),
                trailingIcon = { TextButton({ Extensions.install(ctx, link) { ok -> if (ok) link = "" } }, enabled = link.isNotBlank()) { Text("Instalar") } })
        }
    }
    confirmRemove?.let { e ->
        AlertDialog(onDismissRequest = { confirmRemove = null },
            title = { Text("¿Desinstalar «${e.metaData.name ?: e.id}»?") },
            text = { Text("Se borran también sus ajustes y datos.") },
            confirmButton = { TextButton({ Extensions.uninstall(e); confirmRemove = null }) { Text("Desinstalar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmRemove = null }) { Text("Cancelar") } })
    }
}
