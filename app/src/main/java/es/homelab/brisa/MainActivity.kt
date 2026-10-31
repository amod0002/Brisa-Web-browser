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

class MainActivity : ComponentActivity() {
    private val tabs = mutableStateListOf<Tab>()
    private var current by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Engine.get(this) // arranca el motor cuanto antes
        val saved = getPreferences(MODE_PRIVATE).getString("tabs", "")!!.split('\n').filter { it.isNotBlank() }
        saved.forEach { tabs += Tab(this, false, it) }
        val incoming = intent?.dataString
        if (incoming != null) tabs += Tab(this, false, incoming)
        if (tabs.isEmpty()) tabs += Tab(this, false, null)
        current = tabs.lastIndex
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (android.os.Build.VERSION.SDK_INT >= 31) { if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this) } else if (dark) darkColorScheme() else lightColorScheme()) {
                Browser(tabs, current, { current = it })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { tabs += Tab(this, false, it); current = tabs.lastIndex }
    }

    override fun onPause() {
        super.onPause()
        getPreferences(MODE_PRIVATE).edit()
            .putString("tabs", tabs.filter { !it.private && !it.isHome }.joinToString("\n") { it.url }).apply()
    }
}

@Composable
fun Browser(tabs: SnapshotStateList<Tab>, current: Int, select: (Int) -> Unit) {
    val ctx = LocalContext.current
    val tab = tabs[current.coerceIn(0, tabs.lastIndex)]
    var showTabs by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(false) }
    val geckoView = remember { GeckoView(ctx) }
    val favs = remember { Favorites(ctx) }

    LaunchedEffect(tab) { geckoView.releaseSession(); geckoView.setSession(tab.session); collapsed = false }
    LaunchedEffect(tab.scrollDelta) {
        if (tab.scrollDelta > 8) collapsed = true else if (tab.scrollDelta < -8) collapsed = false
    }

    fun newTab(private: Boolean = false) {
        tabs += Tab(ctx, private, null); select(tabs.lastIndex); showTabs = false; editing = true
    }

    BackHandler(showTabs || showMenu || editing || tab.findText != null || tab.canBack) {
        when {
            tab.findText != null -> tab.endFind()
            showMenu -> showMenu = false
            editing -> editing = false
            showTabs -> showTabs = false
            else -> tab.session.goBack()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                AndroidView({ geckoView }, Modifier.fillMaxSize())
                if (tab.isHome) HomePage(favs, tab.private) { tab.load(it) }
            }
        }

        // Barra inferior estilo Safari
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .navigationBarsPadding().imePadding()
        ) {
            if (tab.progress < 100) LinearProgressIndicator(
                progress = { tab.progress / 100f }, Modifier.fillMaxWidth().height(2.dp)
            )
            AnimatedContent(collapsed && !editing && !tab.isHome && tab.findText == null, label = "bar") { small ->
                if (small) {
                    Text(
                        tab.host, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().clickable { collapsed = false }.padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else if (tab.findText != null) FindBar(tab)
                else Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    AddressBar(tab, editing, { editing = it }, onMenu = { showMenu = true },
                        onSwipe = { dir ->
                            val n = current + dir
                            if (n in tabs.indices) select(n) else if (n == tabs.size) newTab()
                        })
                    if (!editing) Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
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
                            Badge { Text("${tabs.size}") }
                        }
                    }
                }
            }
        }

        var showHistory by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        if (showMenu) PageMenu(tab, { showMenu = false }, { newTab(private = true) }, { showHistory = true }, { showSettings = true })
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

@Composable
fun AddressBar(tab: Tab, editing: Boolean, setEditing: (Boolean) -> Unit, onMenu: () -> Unit, onSwipe: (Int) -> Unit) {
    var text by remember(editing) { mutableStateOf(if (editing) tab.url.takeUnless { tab.isHome } ?: "" else "") }
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

    if (editing && !tab.private) {
        val hist = History.get(ctx).suggest(text)
        if (hist.isNotEmpty()) Column(Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            hist.forEach { e ->
                Row(Modifier.fillMaxWidth().clickable { tab.load(e.url); setEditing(false) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    SiteIcon(History.hostOf(e.url), 28.dp, 6.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(e.title.ifBlank { History.hostOf(e.url) }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                        Text(History.hostOf(e.url), maxLines = 1, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
            .background(if (tab.private) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(tab) {
                detectHorizontalDragGestures(onDragEnd = {
                    if (drag < -120) onSwipe(1) else if (drag > 120) onSwipe(-1); drag = 0f
                }) { _, d -> drag += d }
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (tab.private) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurfaceVariant
        if (editing) {
            Icon(Icons.Outlined.Search, null, tint = tint)
            BasicTextFieldUrl(text, { text = it }, Modifier.weight(1f).focusRequester(focus).onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus && editing) setEditing(false) }) {
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
                Text(if (tab.isHome) "Buscar o escribir web" else tab.host, color = tint, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
            }
            if (tab.progress < 100) IconButton({ tab.session.stop() }) { Icon(Icons.Filled.Close, "Detener", tint = tint) }
            else IconButton({ tab.session.reload() }, enabled = !tab.isHome) { Icon(Icons.Filled.Refresh, "Recargar", tint = tint) }
        }
    }
}

@Composable
fun BasicTextFieldUrl(value: String, onChange: (String) -> Unit, modifier: Modifier, onGo: () -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value, onChange, modifier.padding(horizontal = 8.dp), singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { onGo() })
    )
}

@Composable
fun PageMenu(tab: Tab, dismiss: () -> Unit, newPrivate: () -> Unit, openHistory: () -> Unit, openSettings: () -> Unit) {
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
    val shown = tabs.withIndex().filter { it.value.private == privateView }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).systemBarsPadding()) {
        LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown.size) { k ->
                val (i, t) = shown[k]
                Column(Modifier.clip(RoundedCornerShape(16.dp))
                    .border(if (i == current) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface).clickable { select(i) }) {
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
            IconButton({ onNew(privateView) }) { Icon(Icons.Filled.Add, "Nueva pestaña") }
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(20.dp)) {
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(46.dp).clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextFieldUrl(q, { q = it; tab.find(it) }, Modifier.weight(1f).focusRequester(focus)) { tab.find(q) }
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
            Text("Privacidad", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            OutlinedButton({ Engine.clearData(ctx) { cleared = true } }, Modifier.fillMaxWidth()) {
                Text(if (cleared) "Datos borrados" else "Borrar historial, cookies y caché")
            }
            Text("Brisa ${ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName} · motor Gecko",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp))
        }
    }
}
