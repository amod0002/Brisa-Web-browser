@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package es.homelab.brisa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoView

@Composable
fun SettingsSheet(dismiss: () -> Unit) {
    val ctx = LocalContext.current
    var custom by remember { mutableStateOf(Settings.search.takeIf { it !in Settings.engines.values } ?: "") }
    var cleared by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("Ajustes", style = MaterialTheme.typography.titleLarge)
            Text("Buscador", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            Settings.engines.forEach { (name, t) ->
                Row(Modifier.fillMaxWidth().clickable { Settings.setSearch(ctx, t); custom = "" }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(Settings.search == t, { Settings.setSearch(ctx, t); custom = "" }); Text(name)
                }
            }
            OutlinedTextField(custom, { custom = it; if (Settings.isValidTemplate(it)) Settings.setSearch(ctx, it) },
                Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                label = { Text("Buscador propio (SearXNG…)") },
                placeholder = { Text("http://192.168.1.60:8888/search?q=%s") },
                isError = custom.isNotEmpty() && !Settings.isValidTemplate(custom),
                supportingText = { Text("Pon %s donde va lo que buscas") })
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sugerencias de búsqueda")
                    Text("Lo que escribes se envía a DuckDuckGo para sugerir búsquedas", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(Settings.suggest, { Settings.setSuggest(ctx, it) })
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Estirar hacia abajo para recargar")
                    Text("En lo alto de la página, tira hacia abajo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(Settings.pullRefresh, { Settings.changePullRefresh(ctx, it) })
            }
            if (Settings.isTablet) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Webs de escritorio en la tablet")
                    Text("Como en el iPad. Se puede cambiar por web en «aA».", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(Settings.desktopTablet, { Settings.changeDesktopTablet(ctx, it) })
            }
            Text("Contraseñas", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            Passwords.quarantined?.let { f ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("No se han podido leer tus contraseñas guardadas", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Puede pasar si cambiaste el bloqueo de pantalla o el archivo se dañó. No se ha borrado nada: está apartado como «${f.name}» dentro de Brisa. Las contraseñas nuevas se guardarán aparte.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(top = 4.dp))
                        TextButton({ Passwords.dismissQuarantine() }) { Text("Entendido") }
                    }
                }
            }
            var showPw by remember { mutableStateOf(false) }
            OutlinedButton({
                (ctx as? PromptHost)?.unlock("Ver tus contraseñas guardadas") { ok -> if (ok) showPw = true } ?: run { showPw = true }
            }, Modifier.fillMaxWidth()) { Text("Contraseñas guardadas (${Passwords.logins.size})") }
            if (showPw) PasswordsSheet { showPw = false }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ofrecer guardar contraseñas", Modifier.weight(1f))
                Switch(Passwords.offerSave, { Passwords.changeOfferSave(it) })
            }
            PrivacySection()
            Text("Privacidad", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            OutlinedButton({ Engine.clearData(ctx) { cleared = true } }, Modifier.fillMaxWidth()) {
                Text(if (cleared) "Datos borrados" else "Borrar historial, cookies y caché")
            }
            Text("Brisa ${ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName} · motor Gecko",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

/** Contraseñas guardadas: buscar, ver, copiar, editar, borrar y webs en «Nunca». */
@Composable
fun PasswordsSheet(dismiss: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<Passwords.Login?>(null) }
    fun copy(label: String, t: String) {
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        val clip = android.content.ClipData.newPlainText(label, t)
        // Android 13+: marcar como sensible para que no se muestre en la vista previa del portapapeles
        if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true) }
        cm.setPrimaryClip(clip)
        android.widget.Toast.makeText(ctx, "$label copiado", android.widget.Toast.LENGTH_SHORT).show()
    }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Contraseñas", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Buscar web o usuario") })
        }
        val list = Passwords.logins.filter { q.isBlank() || it.host.contains(q, true) || it.username.contains(q, true) }.sortedBy { it.host }
        if (list.isEmpty()) Text(if (Passwords.logins.isEmpty()) "Cuando inicies sesión en una web, Brisa te ofrecerá guardar la contraseña aquí, cifrada en el móvil." else "Sin resultados.",
            Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(bottom = 16.dp)) {
            items(list.size) { i ->
                val l = list[i]
                ListItem(leadingContent = { SiteIcon(l.host, 36.dp) },
                    headlineContent = { Text(l.host, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(l.username.ifBlank { "(sin usuario)" }, maxLines = 1) },
                    modifier = Modifier.clickable { open = l })
            }
            if (Passwords.never.isNotEmpty()) {
                item { Text("Nunca guardar en", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)) }
                items(Passwords.never.size) { i ->
                    val h = Passwords.never.sorted()[i]
                    ListItem(leadingContent = { SiteIcon(h, 28.dp, 6.dp) }, headlineContent = { Text(h) },
                        trailingContent = { IconButton({ Passwords.removeNever(h) }) { Icon(Icons.Filled.Close, "Quitar") } })
                }
            }
        }
    }
    open?.let { l ->
        var user by remember(l.guid) { mutableStateOf(l.username) }
        var pass by remember(l.guid) { mutableStateOf(l.password) }
        var visible by remember(l.guid) { mutableStateOf(false) }
        var confirmDelete by remember(l.guid) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { open = null },
            icon = { SiteIcon(l.host, 40.dp) },
            title = { Text(l.host) },
            text = { Column {
                OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Usuario") },
                    trailingIcon = { IconButton({ copy("Usuario", user) }) { Icon(Icons.Outlined.ContentCopy, "Copiar usuario") } })
                OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, label = { Text("Contraseña") },
                    visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = { Row {
                        IconButton({ visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Mostrar") }
                        IconButton({ copy("Contraseña", pass) }) { Icon(Icons.Outlined.ContentCopy, "Copiar contraseña") }
                    } })
                Text(l.origin, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                TextButton({ confirmDelete = true }, Modifier.padding(top = 4.dp)) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
                if (confirmDelete) Text("Toca «Borrar» otra vez para confirmar.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { TextButton({
                if (user != l.username || pass != l.password) Passwords.update(l.guid, user, pass); open = null
            }) { Text("Hecho") } },
            dismissButton = { if (confirmDelete) TextButton({ Passwords.delete(l.guid); open = null }) {
                Text("Borrar", color = MaterialTheme.colorScheme.error) } })
    }
}

/** Ajustes → Seguridad y privacidad. */
@Composable
fun PrivacySection() {
    val ctx = LocalContext.current
    var showPerms by remember { mutableStateOf(false) }
    @Composable fun toggle(title: String, sub: String, v: Boolean, key: String) =
        Row(Modifier.fillMaxWidth().clickable { Privacy.set(ctx, key, !v) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title)
                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(v, { Privacy.set(ctx, key, it) })
        }
    @Composable fun choice(options: List<String>, selected: Int, key: String) =
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            options.forEachIndexed { i, o ->
                SegmentedButton(selected == i, { Privacy.set(ctx, key, i) }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(o, fontSize = 13.sp) }
            }
        }

    Text("Seguridad y privacidad", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))

    Text("Solo HTTPS")
    Text("Bloquea las webs sin conexión segura y te deja continuar si quieres. Las direcciones de tu red local (192.168.x.x, .local…) no se ven afectadas.",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    choice(listOf("No", "En privadas", "Siempre"), Privacy.httpsOnly, "https")

    Text("DNS cifrado", Modifier.padding(top = 8.dp))
    Text("Tu operadora no ve qué webs visitas. «Automático» usa el DNS normal si el cifrado falla; «Estricto» nunca.",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    choice(listOf("No", "Automático", "Estricto"), Privacy.dohMode, "doh")
    if (Privacy.dohMode > 0) {
        Privacy.DOH.forEach { (name, uri) ->
            Row(Modifier.fillMaxWidth().clickable { Privacy.set(ctx, "doh_uri", uri) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(Privacy.dohUri == uri, { Privacy.set(ctx, "doh_uri", uri) }); Text(name)
            }
        }
        // DNS propio (AdGuard Home, Pi-hole con DoH, NextDNS…)
        var custom by remember { mutableStateOf(Privacy.dohCustom) }
        val valid = Privacy.isValidDoh(custom)
        val usingCustom = Privacy.dohCustom.isNotEmpty() && Privacy.dohUri == Privacy.dohCustom
        Row(Modifier.fillMaxWidth().clickable(enabled = valid) { Privacy.set(ctx, "doh_propio", custom.trim()); Privacy.set(ctx, "doh_uri", custom.trim()) },
            verticalAlignment = Alignment.CenterVertically) {
            RadioButton(usingCustom, { if (valid) { Privacy.set(ctx, "doh_propio", custom.trim()); Privacy.set(ctx, "doh_uri", custom.trim()) } }, enabled = valid)
            Text("Propio")
        }
        OutlinedTextField(custom, { v ->
                custom = v
                // Si ya estaba elegido, se aplica al escribir una dirección válida
                if (usingCustom && Privacy.isValidDoh(v)) { Privacy.set(ctx, "doh_propio", v.trim()); Privacy.set(ctx, "doh_uri", v.trim()) }
            },
            Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 4.dp), singleLine = true,
            label = { Text("DNS propio (DNS sobre HTTPS)") },
            placeholder = { Text("https://dns.midominio.es/dns-query") },
            isError = custom.isNotEmpty() && !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            supportingText = { Text(
                if (custom.isNotEmpty() && !valid) "Tiene que empezar por https:// (DNS sobre HTTPS)"
                else "Necesita un certificado válido. Si falla, usa «Automático»: en «Estricto» te quedarías sin internet.") })
        if (usingCustom && Privacy.dohMode == 2) Text("Estás en modo estricto con tu DNS propio: si ese servidor se cae, no cargará ninguna web.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 12.dp))
    }

    toggle("Protección contra huella digital", "Las webs no pueden identificarte por las características del móvil", Privacy.fingerprint, "huella")
    toggle("Limpiar enlaces de rastreo", "Quita utm_, fbclid, gclid y similares de las direcciones", Privacy.stripLinks, "enlaces")
    toggle("Rechazar avisos de cookies", "Pulsa «Rechazar» por ti en los avisos más comunes", Privacy.cookieBanners, "cookies")
    toggle("Pedir que no vendan tus datos", "Envía la señal Global Privacy Control a cada web", Privacy.gpc, "gpc")
    toggle("Proteger IP en videollamadas", "WebRTC no revela tu IP real (útil con VPN)", Privacy.webrtc, "webrtc")
    toggle("Preguntar antes de descargar", "Ninguna web puede descargarte un archivo sin que lo confirmes", Privacy.askDownloads, "preguntar_descargas")
    toggle("Bloquear pestañas privadas", "Al volver a Brisa, piden huella o PIN para verlas", Privacy.lockPrivate, "bloquear_privadas")
    toggle("Borrar datos al salir", "Al cerrar Brisa se borran historial, cookies, caché y pestañas abiertas (cerrarás sesión en las webs)", Privacy.clearOnExit, "borrar_salir")

    OutlinedButton({ showPerms = true }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Permisos por web") }
    if (showPerms) SitePermissionsSheet { showPerms = false }
}

/** Permisos que has dado a cada web (ubicación, notificaciones…), con opción de quitarlos. */
@Composable
fun SitePermissionsSheet(dismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sc = remember { Engine.get(ctx).storageController }
    var perms by remember { mutableStateOf<List<org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission>?>(null) }
    fun reload() = sc.allPermissions.accept({ l -> perms = l.orEmpty().filter { label(it.permission) != null && !it.privateMode } }, { perms = emptyList() })
    LaunchedEffect(Unit) { reload() }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Text("Permisos por web", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Text("La cámara y el micrófono se piden cada vez y no aparecen aquí.", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        val list = perms
        if (list == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (list.isEmpty()) Text("Ninguna web tiene permisos especiales.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(bottom = 16.dp)) {
            val byHost = list.groupBy { History.hostOf(it.uri) }.toSortedMap()
            byHost.forEach { (host, ps) ->
                item(host) {
                    ListItem(leadingContent = { SiteIcon(host, 32.dp) }, headlineContent = { Text(host, fontWeight = FontWeight.Medium) })
                }
                items(ps.size, key = { "$host-${ps[it].permission}" }) { i ->
                    val p = ps[i]
                    val allowed = p.value == org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                    ListItem(modifier = Modifier.padding(start = 40.dp),
                        headlineContent = { Text(label(p.permission)!!) },
                        supportingContent = { Text(if (allowed) "Permitido" else "Bloqueado") },
                        trailingContent = { TextButton({
                            sc.setPermission(p, org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT); reload()
                        }) { Text("Quitar") } })
                }
            }
        }
    }
}

private fun label(p: Int): String? = when (p) {
    org.mozilla.geckoview.GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "Ubicación"
    org.mozilla.geckoview.GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "Notificaciones"
    org.mozilla.geckoview.GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "Guardar datos"
    org.mozilla.geckoview.GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS -> "Cookies entre webs"
    org.mozilla.geckoview.GeckoSession.PermissionDelegate.PERMISSION_TRACKING -> "Protección desactivada"
    else -> null
}
