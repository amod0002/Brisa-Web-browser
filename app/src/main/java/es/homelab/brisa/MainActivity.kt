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

    // Atajos de teclado: hay que verlos antes que la web (GeckoView tiene el foco). Uso deliberado.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(e: android.view.KeyEvent): Boolean {
        if (e.action == android.view.KeyEvent.ACTION_DOWN && (e.isCtrlPressed || e.isAltPressed)) {
            val c = e.keyCode
            val k = when {
                e.isCtrlPressed && e.isShiftPressed && c == android.view.KeyEvent.KEYCODE_N -> "privada"
                e.isCtrlPressed && e.isShiftPressed && c == android.view.KeyEvent.KEYCODE_TAB -> "anterior"
                e.isCtrlPressed && c == android.view.KeyEvent.KEYCODE_TAB -> "siguiente"
                e.isCtrlPressed && c == android.view.KeyEvent.KEYCODE_T -> "nueva"
                e.isCtrlPressed && c == android.view.KeyEvent.KEYCODE_W -> "cerrar"
                e.isCtrlPressed && c == android.view.KeyEvent.KEYCODE_L -> "barra"
                e.isCtrlPressed && c == android.view.KeyEvent.KEYCODE_F -> "buscar"
                e.isCtrlPressed && c == android.view.KeyEvent.KEYCODE_R -> "recargar"
                e.isCtrlPressed && c == android.view.KeyEvent.KEYCODE_D -> "favorito"
                e.isCtrlPressed && c in android.view.KeyEvent.KEYCODE_1..android.view.KeyEvent.KEYCODE_9 -> "pestaña:${c - android.view.KeyEvent.KEYCODE_1}"
                e.isAltPressed && c == android.view.KeyEvent.KEYCODE_DPAD_LEFT -> "atras"
                e.isAltPressed && c == android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> "adelante"
                else -> null
            }
            if (k != null && ui.shortcut?.invoke(k) == true) return true
        }
        if (e.action == android.view.KeyEvent.ACTION_DOWN && e.keyCode == android.view.KeyEvent.KEYCODE_F5 && ui.shortcut?.invoke("recargar") == true) return true
        return super.dispatchKeyEvent(e)
    }

    private val tabsFile get() = java.io.File(filesDir, "pestanas.json")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Engine.get(this) // arranca el motor cuanto antes
        Tab.tabHost = this
        Downloads.load(this)
        Engine.get(this)
        // «Borrar datos al salir»: si quedó pendiente (la app se cerró sin pasar por onDestroy), se borra ahora
        val wipe = Privacy.clearOnExit
        if (wipe) { Privacy.clearAll(this); tabsFile.delete() }
        // Pestañas de la última vez, con su historial atrás/adelante
        runCatching {
            val a = org.json.JSONArray(tabsFile.readText())
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                tabs += Tab(this, false, it.optString("url"), it.optString("state").ifEmpty { null },
                    group = it.optString("group").ifEmpty { null }, lazy = true)
            }
        }
        val incoming = intent?.dataString?.let(::safeIncoming)
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
        intent.dataString?.let(::safeIncoming)?.let { tabs += Tab(this, false, it); current = tabs.lastIndex }
    }

    override fun onDestroy() {
        if (isFinishing && Privacy.clearOnExit) { Privacy.clearAll(this); tabsFile.delete() }
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        Report.save()
        // Al salir de la app, las pestañas privadas quedan bloqueadas hasta la huella/PIN
        if (Privacy.lockPrivate && tabs.any { it.private }) ui.privateLocked = true
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
        if (!Privacy.clearOnExit) runCatching { tabsFile.writeText(a.toString()) }
    }
}

class UiState {
    var context by mutableStateOf<Pair<Tab, org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement>?>(null)
    var fullScreen by mutableStateOf(false)
    val dialogs = mutableStateListOf<Dialog>()
    var privateLocked by mutableStateOf(false)
    /** Atajos de teclado: los recibe la actividad y los resuelve la pantalla. */
    var shortcut: ((String) -> Boolean)? = null
    /** Centro del botón de descargas (para la animación del icono que vuela hasta él). */
    var downloadTarget by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
    fun unlockPrivate(ctx: android.content.Context) {
        (ctx as? PromptHost)?.unlock("Ver tus pestañas privadas") { ok -> if (ok) privateLocked = false } ?: run { privateLocked = false }
    }
}

@Composable
fun Browser(tabs: SnapshotStateList<Tab>, current: Int, select: (Int) -> Unit, ui: UiState) {
    val ctx = LocalContext.current
    val tab = tabs[current.coerceIn(0, tabs.lastIndex)]
    var showTabs by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(false) }
    val geckoView = remember {
        GeckoView(ctx).apply {
            // Autorrelleno de Android (Bitwarden, 1Password, KeePassDX…): Gecko describe los campos de la web a Android
            setAutofillEnabled(true)
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_YES
        }
    }
    val favs = remember { Favorites(ctx) }

    LaunchedEffect(tab) {
        geckoView.releaseSession(); geckoView.setSession(tab.session); collapsed = false
        Tab.visible = tab
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
    fun closeTab(i: Int) {
        if (i !in tabs.indices) return
        tabs[i].close(); tabs.removeAt(i)
        if (tabs.isEmpty()) tabs += Tab(ctx, false, null)
        select((if (i < current) current - 1 else current).coerceIn(0, tabs.lastIndex))
    }

    // Diseño de tablet: pantalla de tablet y ventana ancha (en pantalla dividida estrecha vuelve el de móvil)
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    val wide = conf.smallestScreenWidthDp >= 600 && conf.screenWidthDp >= 600

    // Con pestañas privadas abiertas, Android no guarda la miniatura de Brisa en «Recientes»
    val anyPrivate = tabs.any { it.private }
    LaunchedEffect(anyPrivate) {
        if (android.os.Build.VERSION.SDK_INT >= 33) (ctx as? android.app.Activity)?.setRecentsScreenshotEnabled(!anyPrivate)
    }

    SideEffect {
        ui.shortcut = { k ->
            when {
                k == "nueva" -> newTab()
                k == "privada" -> newTab(private = true)
                k == "cerrar" -> closeTab(current)
                k == "barra" -> { showTabs = false; editing = true }
                k == "buscar" -> if (!tab.isHome) tab.findText = ""
                k == "recargar" -> tab.session.reload()
                k == "atras" -> tab.session.goBack()
                k == "adelante" -> tab.session.goForward()
                k == "favorito" -> if (!tab.isHome) favs.toggle(tab.title.ifBlank { tab.host }, tab.url)
                k == "siguiente" -> select((current + 1) % tabs.size)
                k == "anterior" -> select((current - 1 + tabs.size) % tabs.size)
                k.startsWith("pestaña:") -> k.substringAfter(':').toInt().let { n -> select(if (n == 8) tabs.lastIndex else n.coerceAtMost(tabs.lastIndex)) }
            }
            true
        }
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
            if (wide && !ui.fullScreen) {
                TabletToolbar(tab, tabs.size, favs, editing, { editing = it }, onMenu = { showMenu = true },
                    onTabs = { showTabs = true; geckoView.capturePixels().accept({ tab.thumb = it }, { }) },
                    onNewTab = { newTab(tab.private) }, ui = ui, onDownloads = { showDownloads = true })
                if (tab.findText != null) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.widthIn(max = 640.dp)) { FindBar(tab) }
                }
            }
            Box(Modifier.weight(1f)) {
                AndroidView({ geckoView }, Modifier.fillMaxSize(), update = { v ->
                    // El contenedor de Compose no debe ocultar la web al autorrelleno de Android
                    (v.parent as? android.view.View)?.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_YES
                })
                if (tab.isHome) HomePage(favs, tab.private) { tab.load(it) }
                if (tab.private && ui.privateLocked) PrivateLock { ui.unlockPrivate(ctx) }
            }
        }

        // Barra inferior estilo Safari (solo móvil; oculta con vídeo a pantalla completa)
        if (!ui.fullScreen && !wide) Column(
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
                        DownloadButton(ui) { showDownloads = true }
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

        DownloadFly(ui)

        AnimatedVisibility(showTabs, enter = fadeIn() + scaleIn(initialScale = 1.1f), exit = fadeOut() + scaleOut(targetScale = 1.1f)) {
            TabGrid(tabs, current, ui, select = { select(it); showTabs = false }, onClose = { i -> closeTab(i) },
                onNew = { newTab(it) }, onDone = { showTabs = false })
        }
    }
}
}

@Composable
fun AddressBar(tab: Tab, editing: Boolean, setEditing: (Boolean) -> Unit, onMenu: () -> Unit, onSwipe: (Int) -> Unit, dropDown: Boolean = false) {
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
        if (!editing || tab.private || !Settings.suggest || text.isBlank() || Settings.looksLikeAddress(text)) return@LaunchedEffect
        kotlinx.coroutines.delay(200)
        remote = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Settings.fetchSuggestions(ctx, text) }
            .filter { !it.equals(text.trim(), ignoreCase = true) }
    }
    var barWidth by remember { mutableIntStateOf(0) }
    val suggestionsUi: @Composable () -> Unit = { if (editing && !tab.private) {
        val hist = History.get(ctx).suggest(text, max = if (remote.isEmpty()) 6 else 3)
        val current = tab.url.takeUnless { tab.isHome }
        // Una palabra suelta (proxmox, nas…) se busca, pero se ofrece ir a esa máquina de la red
        val bareHost = text.trim().takeIf { it.matches(Regex("^[a-zA-Z0-9-]{2,}$")) }
        if (hist.isNotEmpty() || remote.isNotEmpty() || bareHost != null || (current != null && text.isEmpty())) Column(Modifier.padding(bottom = 8.dp).glass(RoundedCornerShape(22.dp))) {
            if (bareHost != null) Row(Modifier.fillMaxWidth().clickable { tab.load("http://$bareHost/"); setEditing(false) }.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Lan, null, Modifier.size(20.dp), tint = glassContentDim()) }
                Text("Ir a http://$bareHost/", Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, color = glassContent())
            }
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
    } }
    if (!dropDown) suggestionsUi()

    Box(Modifier.onSizeChanged { barWidth = it.width }, contentAlignment = Alignment.BottomCenter) {
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
    // Tablet: las sugerencias salen debajo de la barra, por encima de la página
    if (dropDown && editing) {
        val off = with(androidx.compose.ui.platform.LocalDensity.current) { 58.dp.roundToPx() }
        androidx.compose.ui.window.Popup(offset = androidx.compose.ui.unit.IntOffset(0, off),
            properties = androidx.compose.ui.window.PopupProperties(focusable = false)) {
            Box(Modifier.width(with(androidx.compose.ui.platform.LocalDensity.current) { barWidth.toDp() })) { suggestionsUi() }
        }
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
            var showReport by remember { mutableStateOf(false) }
            ListItem(leadingContent = { Icon(if (tab.shieldOff) Icons.Outlined.RemoveModerator else Icons.Outlined.Shield, null) },
                headlineContent = { Text(if (tab.shieldOff) "Protección desactivada en ${tab.host}" else "${tab.blocked} rastreadores bloqueados") },
                supportingContent = { Text("Ver informe de privacidad") },
                trailingContent = { if (!tab.isHome && !tab.private && tab.trackingPerm != null) Switch(!tab.shieldOff, { tab.toggleShield(); dismiss() }) },
                modifier = Modifier.clickable { showReport = true })
            if (showReport) ReportSheet { showReport = false }
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
fun TabGrid(tabs: List<Tab>, current: Int, ui: UiState, select: (Int) -> Unit, onClose: (Int) -> Unit, onNew: (Boolean) -> Unit, onDone: () -> Unit) {
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
        val ctx = LocalContext.current
        if (privateView && ui.privateLocked && shown.isNotEmpty()) Box(Modifier.weight(1f).fillMaxWidth()) { PrivateLock { ui.unlockPrivate(ctx) } }
        else LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
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
    val w = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val cols = if (w >= 600) (w / 110).coerceIn(5, 8) else 4
    val side = ((w - 960) / 2).coerceAtLeast(0).dp
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(horizontal = side).padding(20.dp).padding(bottom = 160.dp)) {
        if (private) Text("Navegación privada: no se guarda historial ni cookies al cerrar.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
        Text("Favoritos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        val list = favs.list
        if (list.isEmpty()) Text("Toca el marcador de la barra para añadir webs aquí (Proxmox, Cloudreve, Immich…).", color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                row.forEach { (name, url) ->
                    Column(Modifier.weight(1f).combinedClickableCompat({ open(url) }, { favs.toggle(name, url) }), horizontalAlignment = Alignment.CenterHorizontally) {
                        SiteIcon(History.hostOf(url), 58.dp, 14.dp)
                        Text(name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp))
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (!private) {
            val freq = History.get(ctx).frequent(exclude = favs.list.map { History.hostOf(it.second) }.toSet())
            if (freq.isNotEmpty()) {
                Text("Visitados con frecuencia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
                freq.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        row.forEach { e ->
                            Column(Modifier.weight(1f).clickable { open(e.url) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                SiteIcon(History.hostOf(e.url), 58.dp, 14.dp)
                                Text(e.title.ifBlank { History.hostOf(e.url) }.substringBefore(" - ").substringBefore(" | "), fontSize = 12.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp))
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
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
            items(Downloads.items.size, key = { System.identityHashCode(Downloads.items[it]) }) { i ->
                val d = Downloads.items[i]
                val smooth by animateFloatAsState(if (d.total > 0) d.done.toFloat() / d.total else 0f, tween(450), label = "progreso")
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
                            if (d.total > 0) LinearProgressIndicator({ smooth }, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                            else LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                            Text("${mb(d.done)} de ${mb(d.total)}")
                        } else Text(if (d.status == "Terminada") "${mb(d.total)} · ${fmt.format(java.util.Date(d.time))}" else d.status)
                    },
                    trailingContent = { if (d.status == "Descargando") IconButton({ d.cancel = true }) { Icon(Icons.Filled.Close, "Cancelar") } },
                    modifier = Modifier.animateItem().clickable(enabled = d.uri != null) { Downloads.open(ctx, d) })
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

/** Tapa una pestaña privada bloqueada. */
@Composable
fun PrivateLock(unlock: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).clickable(onClick = unlock),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Pestañas privadas bloqueadas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        FilledTonalButton(unlock, Modifier.padding(top = 16.dp)) { Text("Desbloquear") }
    }
}

/** Informe de privacidad de los últimos 7 días. */
@Composable
fun ReportSheet(dismiss: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Report.version   // repintar con datos nuevos
    val days = Report.perDay(7)
    val max = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val names = listOf("L", "M", "X", "J", "V", "S", "D")
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Informe de privacidad", style = MaterialTheme.typography.titleLarge)
            Text("Últimos 7 días · solo en este móvil", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${Report.total(7)}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Text("rastreadores bloqueados por Brisa", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().height(120.dp).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                days.forEach { (d, n) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(0.82f * n / max + 0.02f).clip(RoundedCornerShape(6.dp))
                            .background(if (d == java.time.LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer))
                        Text(names[d.dayOfWeek.value - 1], fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            val top = Report.topSites(7)
            if (top.isNotEmpty()) {
                Text("Webs con más rastreadores", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
                top.forEach { (h, n) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SiteIcon(h, 28.dp, 6.dp)
                        Text(h, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$n", fontWeight = FontWeight.Medium)
                    }
                }
            } else Text("Aún no hay datos. Navega un poco y vuelve.", Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Cuenta lo que bloquea la protección de Gecko; uBlock Origin bloquea además los anuncios por su cuenta.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            TextButton({ Report.clear() }, Modifier.padding(top = 4.dp)) { Text("Borrar informe") }
        }
    }
}

/** Barra de tablet: navegación a la izquierda, dirección en el centro, acciones a la derecha. */
@Composable
fun TabletToolbar(tab: Tab, tabCount: Int, favs: Favorites, editing: Boolean, setEditing: (Boolean) -> Unit,
                  onMenu: () -> Unit, onTabs: () -> Unit, onNewTab: () -> Unit, ui: UiState, onDownloads: () -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ tab.session.goBack() }, enabled = tab.canBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
        IconButton({ tab.session.goForward() }, enabled = tab.canForward) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Adelante") }
        Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                AddressBar(tab, editing, setEditing, onMenu = onMenu, onSwipe = {}, dropDown = true)
            }
        }
        IconButton({
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, tab.url), null))
        }, enabled = !tab.isHome) { Icon(Icons.Outlined.Share, "Compartir") }
        IconButton({ if (!tab.isHome) favs.toggle(tab.title.ifBlank { tab.host }, tab.url) }, enabled = !tab.isHome) {
            Icon(if (favs.has(tab.url)) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Favorito")
        }
        DownloadButton(ui, onDownloads)
        IconButton(onNewTab) { Icon(Icons.Filled.Add, "Nueva pestaña") }
        IconButton(onTabs) {
            Box(Modifier.size(22.dp).border(1.6.dp, LocalContentColor.current, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                Text("$tabCount", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


/**
 * Botón de descargas con anillo de progreso. Aparece mientras hay descargas (y unos segundos después);
 * al terminar se convierte en ✓ con un rebote y una vibración suave; si falla, el anillo se pone rojo.
 */
@Composable
fun DownloadButton(ui: UiState, onClick: () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    val seenFinished = remember { Downloads.finished }
    val seenFailed = remember { Downloads.failed }
    var recent by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val bounce = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Downloads.finished) {
        if (Downloads.finished <= seenFinished) return@LaunchedEffect
        recent = true; done = true; error = false
        view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
            else android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        bounce.animateTo(1.3f, spring(dampingRatio = 0.35f, stiffness = 600f))
        bounce.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
        kotlinx.coroutines.delay(1800); done = false
        kotlinx.coroutines.delay(3000); if (Downloads.progress() == null) recent = false
    }
    LaunchedEffect(Downloads.failed) {
        if (Downloads.failed <= seenFailed) return@LaunchedEffect
        recent = true; error = true
        view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.REJECT
            else android.view.HapticFeedbackConstants.LONG_PRESS)
        kotlinx.coroutines.delay(5000); if (Downloads.progress() == null) { recent = false; error = false }
    }
    val progress = Downloads.progress()
    LaunchedEffect(progress != null) { if (progress != null) { recent = true; error = false } }
    val smooth by animateFloatAsState((progress ?: 1f).coerceAtLeast(0f), tween(450), label = "anillo")

    AnimatedVisibility(progress != null || recent, enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
        IconButton(onClick, Modifier
            .onGloballyPositioned { ui.downloadTarget = it.boundsInRoot().center }
            .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }) {
            Box(contentAlignment = Alignment.Center) {
                val ringColor = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                if (progress != null && progress < 0f) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp, color = ringColor)
                else CircularProgressIndicator({ smooth }, Modifier.size(26.dp), strokeWidth = 2.5.dp, color = ringColor,
                    trackColor = LocalContentColor.current.copy(alpha = 0.18f))
                AnimatedContent(when { error -> 2; done -> 1; else -> 0 }, label = "icono") { st ->
                    Icon(when (st) { 2 -> Icons.Filled.PriorityHigh; 1 -> Icons.Filled.Check; else -> Icons.Filled.ArrowDownward },
                        "Descargas", Modifier.size(14.dp), tint = if (st == 2) MaterialTheme.colorScheme.error else LocalContentColor.current)
                }
            }
        }
    }
}

/** Al empezar una descarga, un icono vuela desde el centro hasta el botón de descargas (en arco, como Safari). */
@Composable
fun DownloadFly(ui: UiState) {
    val seen = remember { Downloads.started }
    var flying by remember { mutableStateOf(false) }
    val t = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Downloads.started) {
        if (Downloads.started <= seen) return@LaunchedEffect
        t.snapTo(0f); flying = true
        kotlinx.coroutines.delay(80)   // dejar que aparezca el botón para saber dónde está
        t.animateTo(1f, tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        flying = false
    }
    if (!flying) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = androidx.compose.ui.platform.LocalDensity.current
        val w = with(d) { maxWidth.toPx() }; val h = with(d) { maxHeight.toPx() }
        val from = androidx.compose.ui.geometry.Offset(w / 2, h / 2)
        val to = ui.downloadTarget ?: androidx.compose.ui.geometry.Offset(w - with(d) { 60.dp.toPx() }, h - with(d) { 60.dp.toPx() })
        val arc = with(d) { 140.dp.toPx() }
        val half = with(d) { 28.dp.toPx() }
        Box(Modifier
            .offset {
                val v = t.value
                val x = from.x + (to.x - from.x) * v
                val y = from.y + (to.y - from.y) * v - kotlin.math.sin(Math.PI * v).toFloat() * arc
                androidx.compose.ui.unit.IntOffset((x - half).toInt(), (y - half).toInt())
            }
            .size(56.dp)
            .graphicsLayer {
                val v = t.value
                val sc = 1.35f - 0.95f * v
                scaleX = sc; scaleY = sc
                alpha = if (v > 0.85f) ((1f - v) / 0.15f).coerceIn(0f, 1f) else 1f
            }
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ArrowDownward, null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** Direcciones que otras apps pueden abrir en Brisa: solo webs (nada de file://, about:, javascript:…). */
fun safeIncoming(s: String): String? {
    val t = s.trim()
    return if (t.startsWith("https://", true) || t.startsWith("http://", true)) t else null
}
