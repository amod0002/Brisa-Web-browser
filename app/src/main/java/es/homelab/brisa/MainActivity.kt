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

class MainActivity : ComponentActivity(), TabHost, PromptHost {
    private val tabs = mutableStateListOf<Tab>()
    private var current by mutableIntStateOf(0)
    private val ui = UiState()

    override fun openTab(tab: Tab) { tabs.add(current + 1, tab); current += 1 }
    override fun contextMenu(tab: Tab, e: org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement) { ui.context = tab to e }
    override fun fullScreen(on: Boolean) {
        ui.fullScreen = on
        val c = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (on) c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()) else c.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }

    // Ventanas de las webs, selector de archivos y permisos de Android
    override fun show(d: Dialog) { ui.dialogs.add(d) }
    private var filesCb: ((List<android.net.Uri>?) -> Unit)? = null
    private val pickOne = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u -> filesCb?.invoke(u?.let { listOf(it) }); filesCb = null }
    private val pickMany = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { l -> filesCb?.invoke(l); filesCb = null }
    override fun pickFiles(mimes: Array<String>?, multiple: Boolean, cb: (List<android.net.Uri>?) -> Unit) {
        filesCb?.invoke(null); filesCb = cb
        val types = mimes?.filter { it.contains('/') }?.toTypedArray()?.takeIf { it.isNotEmpty() } ?: arrayOf("*/*")
        if (multiple) pickMany.launch(types) else pickOne.launch(types)
    }
    private var unlockCb: ((Boolean) -> Unit)? = null
    private val unlockLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        unlockCb?.invoke(r.resultCode == RESULT_OK); unlockCb = null
    }
    override fun unlock(reason: String, cb: (Boolean) -> Unit) {
        val km = getSystemService(android.app.KeyguardManager::class.java)
        if (!km.isDeviceSecure) { cb(true); return }   // sin bloqueo de pantalla no hay nada que pedir
        @Suppress("DEPRECATION")
        val i = km.createConfirmDeviceCredentialIntent("Brisa", reason) ?: run { cb(true); return }
        unlockCb?.invoke(false); unlockCb = cb; unlockLauncher.launch(i)
    }

    private var permsCb: ((Boolean) -> Unit)? = null
    private val askPerms = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
        permsCb?.invoke(r.values.all { it }); permsCb = null
    }
    override fun androidPermissions(perms: Array<String>, cb: (Boolean) -> Unit) {
        if (perms.all { checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) { cb(true); return }
        permsCb?.invoke(false); permsCb = cb; askPerms.launch(perms)
    }

    private val tabsFile get() = java.io.File(filesDir, "pestanas.json")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Engine.get(this) // arranca el motor cuanto antes
        Tab.tabHost = this
        Downloads.load(this)
        // Pestañas de la última vez, con su historial atrás/adelante
        runCatching {
            val a = org.json.JSONArray(tabsFile.readText())
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                tabs += Tab(this, false, it.optString("url"), it.optString("state").ifEmpty { null },
                    group = it.optString("group").ifEmpty { null }, lazy = true)
            }
        }
        val incoming = intent?.dataString
        if (incoming != null) tabs += Tab(this, false, incoming)
        if (tabs.isEmpty()) tabs += Tab(this, false, null)
        current = tabs.lastIndex
        // Pantalla a la máxima frecuencia (90/120 Hz) y una pestaña nueva ya preparada
        if (android.os.Build.VERSION.SDK_INT >= 30) display?.let { d ->
            val now = d.mode
            d.supportedModes.filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight }
                .maxByOrNull { it.refreshRate }?.let { m -> window.attributes = window.attributes.apply { preferredDisplayModeId = m.modeId } }
        }
        window.decorView.postDelayed({ Engine.warm(this) }, 2000)
        window.decorView.postDelayed({
            Engine.autoUblock(this) { ok ->
                if (ok) android.widget.Toast.makeText(this, "Bloqueador de anuncios activado: las webs cargarán más rápido", android.widget.Toast.LENGTH_LONG).show()
            }
        }, 3000)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (android.os.Build.VERSION.SDK_INT >= 31) { if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this) } else if (dark) darkColorScheme() else lightColorScheme()) {
                Browser(tabs, current, { current = it }, ui)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { tabs += Tab(this, false, it); current = tabs.lastIndex }
    }

    override fun onResume() {
        super.onResume()
        tabs.getOrNull(current)?.session?.setActive(true)
    }

    override fun onPause() {
        super.onPause()
        tabs.forEach { it.session.setActive(false) }
        val a = org.json.JSONArray()
        tabs.filter { !it.private && !it.isHome }.forEach {
            a.put(org.json.JSONObject().put("url", it.url).put("state", it.state ?: "").put("group", it.group ?: ""))
        }
        runCatching { tabsFile.writeText(a.toString()) }
    }
}

class UiState {
    var context by mutableStateOf<Pair<Tab, org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement>?>(null)
    var fullScreen by mutableStateOf(false)
    val dialogs = mutableStateListOf<Dialog>()
}

@Composable
fun Browser(tabs: SnapshotStateList<Tab>, current: Int, select: (Int) -> Unit, ui: UiState) {
    val ctx = LocalContext.current
    val tab = tabs[current.coerceIn(0, tabs.lastIndex)]
    var showTabs by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(false) }
    val geckoView = remember { GeckoView(ctx) }
    val favs = remember { Favorites(ctx) }

    LaunchedEffect(tab) {
        geckoView.releaseSession(); geckoView.setSession(tab.session); collapsed = false
        tab.session.setActive(true); tab.wake()
        // Las pestañas en segundo plano se pausan: no gastan red ni procesador mientras carga la visible
        tabs.forEach { if (it !== tab) it.session.setActive(false) }
    }
    LaunchedEffect(tab.scrollDelta) {
        if (tab.scrollDelta > 8) collapsed = true else if (tab.scrollDelta < -8) collapsed = false
    }

    fun newTab(private: Boolean = false) {
        tabs += Tab(ctx, private, null); select(tabs.lastIndex); showTabs = false; editing = true
    }

    BackHandler(ui.fullScreen || showTabs || showMenu || editing || tab.findText != null || tab.canBack) {
        when {
            ui.fullScreen -> tab.session.exitFullScreen()
            tab.findText != null -> tab.endFind()
            showMenu -> showMenu = false
            editing -> editing = false
            showTabs -> showTabs = false
            else -> tab.session.goBack()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().then(if (ui.fullScreen) Modifier else Modifier.statusBarsPadding())) {
            Box(Modifier.weight(1f)) {
                AndroidView({ geckoView }, Modifier.fillMaxSize())
                if (tab.isHome) HomePage(favs, tab.private) { tab.load(it) }
            }
        }

        // Barra inferior estilo Safari (oculta con vídeo a pantalla completa)
        if (!ui.fullScreen) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding().imePadding().padding(horizontal = 12.dp).padding(bottom = 8.dp)
        ) {
            AnimatedContent(collapsed && !editing && !tab.isHome && tab.findText == null, label = "bar") { small ->
                if (small) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(Modifier.glass(RoundedCornerShape(50), tint = if (tab.private) PrivateGlass else null)
                        .clickable { collapsed = false }.padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (tab.url.startsWith("https")) Icon(Icons.Filled.Lock, null, Modifier.size(12.dp), tint = glassContentDim(tab.private))
                        Text(tab.host, fontSize = 13.sp, color = glassContent(tab.private), modifier = Modifier.padding(start = 4.dp))
                    }
                } else if (tab.findText != null) FindBar(tab)
                else Column {
                    AddressBar(tab, editing, { editing = it }, onMenu = { showMenu = true },
                        onSwipe = { dir ->
                            val n = current + dir
                            if (n in tabs.indices) select(n) else if (n == tabs.size) newTab()
                        })
                    if (!editing) CompositionLocalProvider(LocalContentColor provides glassContent()) { Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).glass(RoundedCornerShape(26.dp)).padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        IconButton({ tab.session.goBack() }, enabled = tab.canBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                        IconButton({ tab.session.goForward() }, enabled = tab.canForward) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Adelante") }
                        IconButton({
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, tab.url), null))
                        }, enabled = !tab.isHome) { Icon(Icons.Outlined.Share, "Compartir") }
                        IconButton({ if (!tab.isHome) favs.toggle(tab.title.ifBlank { tab.host }, tab.url) }, enabled = !tab.isHome) {
                            Icon(if (favs.has(tab.url)) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Favorito")
                        }
                        IconButton({ showTabs = true; geckoView.capturePixels().accept({ tab.thumb = it }, { }) }) {
                            Box(Modifier.size(22.dp).border(1.6.dp, LocalContentColor.current, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                Text("${tabs.size}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } }
                }
            }
        }

        var showHistory by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var showDownloads by remember { mutableStateOf(false) }
        if (showMenu) PageMenu(tab, { showMenu = false }, { newTab(private = true) }, { showHistory = true }, { showSettings = true }, { showDownloads = true })
        if (showDownloads) DownloadsSheet { showDownloads = false }
        ui.dialogs.toList().forEach { d -> key(d) { d { ui.dialogs.remove(d) } } }
        ui.context?.let { (t, e) ->
            LinkSheet(e, dismiss = { ui.context = null },
                newTab = { u, priv ->
                    val nt = Tab(ctx, priv, u)
                    tabs.add(tabs.indexOf(t) + 1, nt)
                    if (priv) select(tabs.indexOf(nt))
                    android.widget.Toast.makeText(ctx, if (priv) "Abierta en pestaña privada" else "Abierta en una pestaña nueva", android.widget.Toast.LENGTH_SHORT).show()
                },
                open = { t.load(it) })
        }
        if (showSettings) SettingsSheet { showSettings = false }
        if (showHistory) HistorySheet({ showHistory = false }) { tab.load(it) }

        AnimatedVisibility(showTabs, enter = fadeIn() + scaleIn(initialScale = 1.1f), exit = fadeOut() + scaleOut(targetScale = 1.1f)) {
            TabGrid(tabs, current, select = { select(it); showTabs = false }, onClose = { i ->
                tabs[i].close(); tabs.removeAt(i)
                if (tabs.isEmpty()) tabs += Tab(ctx, false, null)
                select(current.coerceAtMost(tabs.lastIndex))
            }, onNew = { newTab(it) }, onDone = { showTabs = false })
        }
    }
}
}

@Composable
fun AddressBar(tab: Tab, editing: Boolean, setEditing: (Boolean) -> Unit, onMenu: () -> Unit, onSwipe: (Int) -> Unit) {
    // Al tocar la barra se empieza en blanco; la dirección actual queda como sugerencia «Editar» por si se quiere retocar.
    var text by remember(editing) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // Solo se cierra al PERDER el foco después de haberlo tenido; el aviso inicial «sin foco» se ignora.
    var hadFocus by remember(editing) { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (editing) {
            androidx.compose.runtime.withFrameNanos { } // esperar a que el campo exista
            focus.requestFocus(); keyboard?.show()
        } else { fm.clearFocus(); keyboard?.hide() }
    }
    var drag by remember { mutableFloatStateOf(0f) }
    val ctx = LocalContext.current

    var remote by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(text, editing) {
        remote = emptyList()
        if (!editing || tab.private || !Settings.suggest || text.isBlank() || text.contains("://")) return@LaunchedEffect
        kotlinx.coroutines.delay(200)
        remote = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Settings.fetchSuggestions(text) }
            .filter { !it.equals(text.trim(), ignoreCase = true) }
    }
    if (editing && !tab.private) {
        val hist = History.get(ctx).suggest(text, max = if (remote.isEmpty()) 6 else 3)
        val current = tab.url.takeUnless { tab.isHome }
        if (hist.isNotEmpty() || remote.isNotEmpty() || (current != null && text.isEmpty())) Column(Modifier.padding(bottom = 8.dp).glass(RoundedCornerShape(22.dp))) {
            if (current != null && text.isEmpty()) Row(Modifier.fillMaxWidth().clickable { text = current }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SiteIcon(tab.host, 28.dp, 6.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(tab.title.ifBlank { tab.host }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, color = glassContent())
                    Text(current, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = glassContentDim())
                }
                Icon(Icons.Outlined.Edit, "Editar dirección", Modifier.size(20.dp), tint = glassContentDim())
            }
            hist.forEach { e ->
                Row(Modifier.fillMaxWidth().clickable { tab.load(e.url); setEditing(false) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    SiteIcon(History.hostOf(e.url), 28.dp, 6.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(e.title.ifBlank { History.hostOf(e.url) }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, color = glassContent())
                        Text(History.hostOf(e.url), maxLines = 1, fontSize = 12.sp, color = glassContentDim())
                    }
                }
            }
            remote.forEach { q ->
                Row(Modifier.fillMaxWidth().clickable { tab.load(q); setEditing(false) }.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = glassContentDim()) }
                    Text(q, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, color = glassContent())
                    Icon(Icons.Outlined.NorthWest, "Completar", Modifier.size(18.dp).clickable { text = q + " " }, tint = glassContentDim())
                }
            }
        }
    }

    Box(contentAlignment = Alignment.BottomCenter) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).glass(RoundedCornerShape(25.dp), tint = if (tab.private) PrivateGlass else null)
            .pointerInput(tab) {
                detectHorizontalDragGestures(onDragEnd = {
                    if (drag < -120) onSwipe(1) else if (drag > 120) onSwipe(-1); drag = 0f
                }) { _, d -> drag += d }
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = glassContentDim(tab.private)
        val strong = glassContent(tab.private)
        if (editing) {
            Icon(Icons.Outlined.Search, null, tint = tint)
            BasicTextFieldUrl(text, { text = it }, color = strong, placeholder = if (tab.isHome) "Buscar o escribir web" else tab.host, modifier = Modifier.weight(1f).focusRequester(focus).onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus && editing) setEditing(false) }) {
                if (text.isNotBlank()) tab.load(text); setEditing(false)
            }
            if (text.isNotEmpty()) IconButton({ text = "" }) { Icon(Icons.Filled.Cancel, "Borrar", tint = tint) }
        } else {
            Text("aA", fontWeight = FontWeight.Medium, color = tint, modifier = Modifier.clickable(onClick = onMenu).padding(6.dp))
            if (tab.readerable || tab.reading) IconButton({ tab.toggleReader() }, Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Description, if (tab.reading) "Salir del modo lectura" else "Modo lectura",
                    tint = if (tab.reading) MaterialTheme.colorScheme.primary else tint, modifier = Modifier.size(20.dp))
            }
            Row(Modifier.weight(1f).clickable { setEditing(true) }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (tab.url.startsWith("https")) Icon(Icons.Filled.Lock, null, Modifier.size(14.dp), tint = tint)
                Text(if (tab.isHome) "Buscar o escribir web" else tab.host, color = if (tab.isHome) tint else strong, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
            }
            if (tab.progress < 100) IconButton({ tab.session.stop() }) { Icon(Icons.Filled.Close, "Detener", tint = tint) }
            else IconButton({ tab.session.reload() }, enabled = !tab.isHome) { Icon(Icons.Filled.Refresh, "Recargar", tint = tint) }
        }
    }
    // Progreso de carga: una línea fina dentro de la cápsula
    if (tab.progress < 100 && !editing) LinearProgressIndicator({ tab.progress / 100f },
        Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 3.dp).height(2.dp).clip(RoundedCornerShape(1.dp)),
        trackColor = androidx.compose.ui.graphics.Color.Transparent)
    }
}

@Composable
fun BasicTextFieldUrl(value: String, onChange: (String) -> Unit, modifier: Modifier,
                     color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
                     placeholder: String? = null, onGo: () -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value, onChange, modifier.padding(horizontal = 8.dp), singleLine = true,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder != null) Text(placeholder, color = color.copy(alpha = 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            }
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = color),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { onGo() })
    )
}

@Composable
fun PageMenu(tab: Tab, dismiss: () -> Unit, newPrivate: () -> Unit, openHistory: () -> Unit, openSettings: () -> Unit, openDownloads: () -> Unit) {
    val ctx = LocalContext.current
    var size by remember { mutableFloatStateOf(Engine.textSize(ctx)) }
    var ublock by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { Engine.hasUblock(ctx) { ublock = it } }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            ListItem(leadingContent = { Icon(Icons.Outlined.Shield, null) },
                headlineContent = { Text("${tab.blocked} rastreadores bloqueados") },
                supportingContent = { Text("Protección estricta de Firefox") })
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tamaño del texto", Modifier.weight(1f))
                OutlinedButton({ size = (size - 0.1f).coerceAtLeast(0.8f); Engine.setTextSize(ctx, size) }) { Text("A", fontSize = 13.sp) }
                Text("${(size * 100).toInt()} %", Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                OutlinedButton({ size = (size + 0.1f).coerceAtMost(1.6f); Engine.setTextSize(ctx, size) }) { Text("A", fontSize = 19.sp) }
            }
            if (tab.readerable || tab.reading) ListItem(leadingContent = { Icon(Icons.Outlined.Description, null) },
                headlineContent = { Text(if (tab.reading) "Salir del modo lectura" else "Modo lectura") },
                modifier = Modifier.clickable { tab.toggleReader(); dismiss() })
            ListItem(leadingContent = { Icon(Icons.Outlined.Search, null) },
                headlineContent = { Text("Buscar en la página") },
                modifier = Modifier.clickable { tab.findText = ""; dismiss() })
            ListItem(leadingContent = { Icon(Icons.Outlined.History, null) },
                headlineContent = { Text("Historial") },
                modifier = Modifier.clickable { dismiss(); openHistory() })
            ListItem(leadingContent = { Icon(Icons.Outlined.Download, null) },
                headlineContent = { Text("Descargas") },
                supportingContent = { Downloads.items.count { it.status == "Descargando" }.takeIf { it > 0 }?.let { Text("$it en curso") } },
                modifier = Modifier.clickable { dismiss(); openDownloads() })
            ListItem(leadingContent = { Icon(Icons.Outlined.Extension, null) },
                headlineContent = { Text(when (ublock) { true -> "uBlock Origin activo"; null -> "Extensiones…"; else -> "Instalar uBlock Origin" }) },
                supportingContent = { Text("Bloqueador de anuncios") },
                modifier = Modifier.clickable(enabled = ublock == false) {
                    ublock = null
                    android.widget.Toast.makeText(ctx, "Instalando uBlock Origin…", android.widget.Toast.LENGTH_SHORT).show()
                    Engine.installUblock(ctx) { ok ->
                        ublock = ok
                        android.widget.Toast.makeText(ctx, if (ok) "uBlock Origin instalado" else "No se pudo instalar uBlock Origin", android.widget.Toast.LENGTH_SHORT).show()
                    }
                })
            ListItem(leadingContent = { Icon(Icons.Outlined.DesktopWindows, null) },
                headlineContent = { Text(if (tab.desktop) "Versión móvil" else "Versión de escritorio") },
                modifier = Modifier.clickable { tab.toggleDesktop(); dismiss() })
            ListItem(leadingContent = { Icon(Icons.Outlined.VisibilityOff, null) },
                headlineContent = { Text("Nueva pestaña privada") },
                modifier = Modifier.clickable { dismiss(); newPrivate() })
            ListItem(leadingContent = { Icon(Icons.Outlined.OpenInNew, null) },
                headlineContent = { Text("Abrir en otra app") },
                modifier = Modifier.clickable {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(tab.url))) }; dismiss()
                })
            ListItem(leadingContent = { Icon(Icons.Outlined.Settings, null) },
                headlineContent = { Text("Ajustes") },
                modifier = Modifier.clickable { dismiss(); openSettings() })
        }
    }
}

@Composable
fun TabGrid(tabs: List<Tab>, current: Int, select: (Int) -> Unit, onClose: (Int) -> Unit, onNew: (Boolean) -> Unit, onDone: () -> Unit) {
    var privateView by remember { mutableStateOf(tabs.getOrNull(current)?.private == true) }
    // Grupo que se está viendo: null = todas
    var groupView by remember { mutableStateOf(tabs.getOrNull(current)?.group) }
    var grouping by remember { mutableStateOf<Tab?>(null) }
    val groups = tabs.filter { !it.private }.mapNotNull { it.group }.distinct().sorted()
    if (groupView != null && groupView !in groups) groupView = null
    val shown = tabs.withIndex().filter { it.value.private == privateView && (privateView || groupView == null || it.value.group == groupView) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).systemBarsPadding()) {
        if (!privateView && groups.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(groupView == null, { groupView = null }, { Text("Todas") })
            groups.forEach { g -> FilterChip(groupView == g, { groupView = g }, { Text("$g · ${tabs.count { it.group == g }}") }) }
        }
        grouping?.let { t -> GroupDialog(t, groups, { grouping = null }) { g -> t.group = g; if (g != null) groupView = g } }
        LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown.size) { k ->
                val (i, t) = shown[k]
                Column(Modifier.clip(RoundedCornerShape(16.dp))
                    .border(if (i == current) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .combinedClickableCompat({ select(i) }, { if (!t.private) grouping = t })) {
                    Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!t.isHome) { SiteIcon(t.host, 18.dp, 4.dp); Spacer(Modifier.width(6.dp)) }
                        Text(t.title.ifBlank { if (t.isHome) "Inicio" else t.host }, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton({ onClose(i) }, Modifier.size(36.dp)) { Icon(Icons.Filled.Close, "Cerrar", Modifier.size(16.dp)) }
                    }
                    val th = t.thumb
                    if (th != null) Image(th.asImageBitmap(), null, Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                    else Box(Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onNew(privateView); if (!privateView) groupView?.let { g -> tabs.lastOrNull()?.group = g } }) { Icon(Icons.Filled.Add, "Nueva pestaña") }
            Spacer(Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(privateView, { privateView = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Privada") }
                SegmentedButton(!privateView, { privateView = false }, SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text("${tabs.count { !it.private }} pestañas")
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onDone) { Text("OK", fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
fun HomePage(favs: Favorites, private: Boolean, open: (String) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = 160.dp)) {
        if (private) Text("Navegación privada: no se guarda historial ni cookies al cerrar.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
        Text("Favoritos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        val list = favs.list
        if (list.isEmpty()) Text("Toca el marcador de la barra para añadir webs aquí (Proxmox, Cloudreve, Immich…).", color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                row.forEach { (name, url) ->
                    Column(Modifier.weight(1f).combinedClickableCompat({ open(url) }, { favs.toggle(name, url) }), horizontalAlignment = Alignment.CenterHorizontally) {
                        SiteIcon(History.hostOf(url), 58.dp, 14.dp)
                        Text(name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp))
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (!private) {
            val freq = History.get(ctx).frequent(exclude = favs.list.map { History.hostOf(it.second) }.toSet())
            if (freq.isNotEmpty()) {
                Text("Visitados con frecuencia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
                freq.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        row.forEach { e ->
                            Column(Modifier.weight(1f).clickable { open(e.url) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                SiteIcon(History.hostOf(e.url), 58.dp, 14.dp)
                                Text(e.title.ifBlank { History.hostOf(e.url) }.substringBefore(" - ").substringBefore(" | "), fontSize = 12.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp))
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLong: () -> Unit) = combinedClickable(onClick = onClick, onLongClick = onLong)

/** Favoritos guardados en el móvil. Mantener pulsado uno lo quita. */
class Favorites(ctx: android.content.Context) {
    private val prefs = ctx.getSharedPreferences("favs", 0)
    var list by mutableStateOf(load())
        private set

    private fun load() = prefs.getString("l", "")!!.split('\n').filter { '\t' in it }
        .map { it.substringBefore('\t') to it.substringAfter('\t') }

    fun has(url: String) = list.any { it.second == url }
    fun toggle(name: String, url: String) {
        list = if (has(url)) list.filter { it.second != url } else list + (name to url)
        prefs.edit().putString("l", list.joinToString("\n") { "${it.first}\t${it.second}" }).apply()
    }
}

@Composable
fun FindBar(tab: Tab) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; focus.requestFocus() }
    Row(Modifier.fillMaxWidth().height(50.dp).glass(RoundedCornerShape(25.dp)).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextFieldUrl(q, { q = it; tab.find(it) }, Modifier.weight(1f).focusRequester(focus), color = glassContent(), placeholder = "Buscar en la página") { tab.find(q) }
        Text(tab.findResult, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton({ tab.find(q, back = true) }) { Icon(Icons.Filled.KeyboardArrowUp, "Anterior") }
        IconButton({ tab.find(q) }) { Icon(Icons.Filled.KeyboardArrowDown, "Siguiente") }
        TextButton({ tab.endFind() }) { Text("OK") }
    }
}

@Composable
fun HistorySheet(dismiss: () -> Unit, open: (String) -> Unit) {
    val h = History.get(LocalContext.current)
    val fmt = remember { java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("es")) }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Historial", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (h.entries.isNotEmpty()) TextButton({ h.clear() }) { Text("Borrar todo") }
        }
        if (h.entries.isEmpty()) Text("Aún no hay historial.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(bottom = 16.dp)) {
            items(h.entries.size) { i ->
                val e = h.entries[i]
                ListItem(headlineContent = { Text(e.title.ifBlank { History.hostOf(e.url) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${History.hostOf(e.url)} · ${fmt.format(java.util.Date(e.time))}", maxLines = 1) },
                    modifier = Modifier.clickable { open(e.url); dismiss() })
            }
        }
    }
}

/** Icono de la web (o su inicial) en un cuadrado redondeado. */
@Composable
fun SiteIcon(host: String, size: androidx.compose.ui.unit.Dp, corner: androidx.compose.ui.unit.Dp = 8.dp) {
    val icon = WebIcons.get(LocalContext.current, host)
    Box(Modifier.size(size).clip(RoundedCornerShape(corner)).background(
        if (icon != null) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center) {
        if (icon != null) Image(icon, null, Modifier.fillMaxSize(0.72f))
        else Text(host.take(1).uppercase(), fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

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
            Text("Contraseñas", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            var showPw by remember { mutableStateOf(false) }
            OutlinedButton({
                (ctx as? PromptHost)?.unlock("Ver tus contraseñas guardadas") { ok -> if (ok) showPw = true } ?: run { showPw = true }
            }, Modifier.fillMaxWidth()) { Text("Contraseñas guardadas (${Passwords.logins.size})") }
            if (showPw) PasswordsSheet { showPw = false }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ofrecer guardar contraseñas", Modifier.weight(1f))
                Switch(Passwords.offerSave, { Passwords.setOfferSave(it) })
            }
            Text("Privacidad", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            OutlinedButton({ Engine.clearData(ctx) { cleared = true } }, Modifier.fillMaxWidth()) {
                Text(if (cleared) "Datos borrados" else "Borrar historial, cookies y caché")
            }
            Text("Brisa ${ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName} · motor Gecko",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

/** Mantener pulsado un enlace o una imagen. */
@Composable
fun LinkSheet(e: org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement, dismiss: () -> Unit,
              newTab: (String, Boolean) -> Unit, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val link = e.linkUri
    val src = e.srcUri
    val isImg = e.type == org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE
    fun copy(t: String) {
        (ctx.getSystemService(android.content.ClipboardManager::class.java))
            .setPrimaryClip(android.content.ClipData.newPlainText("enlace", t))
    }
    fun share(t: String) = ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null))
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SiteIcon(History.hostOf(link ?: src ?: ""), 32.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(e.title ?: e.altText ?: History.hostOf(link ?: src ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(link ?: src ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            if (link != null) {
                ListItem(leadingContent = { Icon(Icons.Outlined.Tab, null) }, headlineContent = { Text("Abrir en pestaña nueva") },
                    modifier = Modifier.clickable { newTab(link, false); dismiss() })
                ListItem(leadingContent = { Icon(Icons.Outlined.VisibilityOff, null) }, headlineContent = { Text("Abrir en pestaña privada") },
                    modifier = Modifier.clickable { newTab(link, true); dismiss() })
                ListItem(leadingContent = { Icon(Icons.Outlined.ContentCopy, null) }, headlineContent = { Text("Copiar enlace") },
                    modifier = Modifier.clickable { copy(link); dismiss() })
                ListItem(leadingContent = { Icon(Icons.Outlined.Share, null) }, headlineContent = { Text("Compartir enlace") },
                    modifier = Modifier.clickable { share(link); dismiss() })
            }
            if (isImg && src != null) {
                ListItem(leadingContent = { Icon(Icons.Outlined.Image, null) }, headlineContent = { Text("Abrir imagen") },
                    modifier = Modifier.clickable { open(src); dismiss() })
                ListItem(leadingContent = { Icon(Icons.Outlined.Link, null) }, headlineContent = { Text("Copiar dirección de la imagen") },
                    modifier = Modifier.clickable { copy(src); dismiss() })
            }
        }
    }
}

@Composable
fun DownloadsSheet(dismiss: () -> Unit) {
    val ctx = LocalContext.current
    val fmt = remember { java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("es")) }
    fun mb(b: Long) = if (b < 0) "?" else if (b < 1_048_576) "${b / 1024} KB" else String.format(java.util.Locale("es"), "%.1f MB", b / 1_048_576.0)
    ModalBottomSheet(onDismissRequest = dismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Descargas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (Downloads.items.any { it.status != "Descargando" }) TextButton({ Downloads.clear(ctx) }) { Text("Limpiar lista") }
        }
        if (Downloads.items.isEmpty()) Text("Lo que descargues aparecerá aquí y en Descargas/Brisa.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(bottom = 16.dp)) {
            items(Downloads.items.size) { i ->
                val d = Downloads.items[i]
                ListItem(
                    leadingContent = { Icon(when {
                        d.mime.startsWith("image") -> Icons.Outlined.Image
                        d.mime.startsWith("video") -> Icons.Outlined.Movie
                        d.mime.startsWith("audio") -> Icons.Outlined.MusicNote
                        d.mime.contains("pdf") -> Icons.Outlined.PictureAsPdf
                        else -> Icons.Outlined.Description }, null) },
                    headlineContent = { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        if (d.status == "Descargando") Column {
                            if (d.total > 0) LinearProgressIndicator({ d.done.toFloat() / d.total }, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                            else LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                            Text("${mb(d.done)} de ${mb(d.total)}")
                        } else Text(if (d.status == "Terminada") "${mb(d.total)} · ${fmt.format(java.util.Date(d.time))}" else d.status)
                    },
                    trailingContent = { if (d.status == "Descargando") IconButton({ d.cancel = true }) { Icon(Icons.Filled.Close, "Cancelar") } },
                    modifier = Modifier.clickable(enabled = d.uri != null) { Downloads.open(ctx, d) })
            }
        }
    }
}

/** Mantener pulsada una pestaña: elegir grupo, crear uno nuevo o quitarla del grupo. */
@Composable
fun GroupDialog(tab: Tab, groups: List<String>, dismiss: () -> Unit, set: (String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss,
        title = { Text("Grupo de la pestaña") },
        text = { Column {
            groups.forEach { g ->
                Row(Modifier.fillMaxWidth().clickable { set(g); dismiss() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(tab.group == g, null); Text(g, Modifier.padding(start = 8.dp))
                }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                label = { Text("Grupo nuevo") }, placeholder = { Text("Homelab, Compras, Viaje…") })
        } },
        confirmButton = { TextButton({ if (name.isNotBlank()) { set(name.trim()); dismiss() } }, enabled = name.isNotBlank()) { Text("Crear") } },
        dismissButton = { if (tab.group != null) TextButton({ set(null); dismiss() }) { Text("Quitar del grupo") } else TextButton(dismiss) { Text("Cancelar") } })
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
