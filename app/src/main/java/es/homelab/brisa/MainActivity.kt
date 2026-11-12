@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package es.homelab.brisa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    override fun closeTab(tab: Tab) {
        val i = tabs.indexOf(tab); if (i < 0) return
        val back = tab.opener?.let { tabs.indexOf(it) } ?: -1
        tab.close(); tabs.removeAt(i)
        if (tabs.isEmpty()) tabs += Tab(this, false, null)
        current = when {
            back >= 0 -> if (back > i) back - 1 else back
            i < current -> current - 1
            else -> current
        }.coerceIn(0, tabs.lastIndex)
    }
    override fun focusTab(tab: Tab) {
        val i = tabs.indexOf(tab); if (i < 0) return
        current = i; ui.closeGrid++
    }
    /** «Salir y borrar datos» (#43): borra todo y cierra Brisa de verdad, también de Recientes. */
    fun quitAndWipe() {
        wipeTabs()
        Privacy.clearAll(this) { finishAndRemoveTask() }
    }
    private fun wipeTabs() {
        tabs.forEach { it.close() }; tabs.clear(); tabsFile.delete()
        tabs += Tab(this, false, null); current = 0
    }

    /**
     * Poca memoria DE VERDAD: fuera las miniaturas de las pestañas que no se ven (se vuelven a hacer al abrir
     * la cuadrícula). Solo con falta de memoria con Brisa delante o en el extremo; NO con UI_HIDDEN,
     * BACKGROUND ni MODERATE, que llegan cada vez que Brisa pasa al fondo y dejaban la cuadrícula en gris (#54).
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        val low = level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        if (low) tabs.forEachIndexed { i, t -> if (i != current) t.thumb = null }
    }
    /** Solo el menú de la pestaña que se está viendo: uno de otra pestaña es un toque a medias que Gecko tomó por pulsación larga (#53). */
    override fun contextMenu(tab: Tab, e: org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement) {
        if (tab === Tab.visible) ui.context = tab to e
    }
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
        permsCb?.invoke(false); permsCb = cb; ownScreen = true; askPerms.launch(perms)
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
        // Atrás sin nada que deshacer: Brisa se va al fondo, NUNCA se cierra (#41). Si se cerrase, al volver
        // tendría que montarse otra vez entera. Si se abrió desde el icono, Android ya lo hace solo y con su
        // animación de «volver a inicio»; esto es para cuando se abrió con un enlace de otra app.
        if (intent?.action != Intent.ACTION_MAIN) onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { moveTaskToBack(true) }
        })
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
                    group = it.optString("group").ifEmpty { null }, lazy = true, id = it.optString("id").ifEmpty { null })
                    // Su miniatura de la última vez, del disco (#58)
                    .also { t -> Thumbs.load(this, t.id) { b -> if (t.thumb == null) t.thumb = b } }
            }
        }
        Thumbs.prune(this, tabs.map { it.id }.toSet())
        val incoming = intent?.dataString?.let(::safeIncoming) ?: fromNotification(intent)
        if (incoming != null) tabs += Tab(this, false, incoming)
        if (tabs.isEmpty()) tabs += Tab(this, false, null)
        current = tabs.lastIndex
        // La frecuencia de pantalla la decide Android (sube a 90/120 Hz al moverse y baja con la página quieta,
        // que es como ahorra batería). Forzar el modo máximo lo impedía (#22).
        // Pestaña nueva ya preparada:
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
        wipeIfAway()
        if (intent.hasExtra(WebNotifs.EXTRA_TAG)) {
            fromNotification(intent)?.let { tabs += Tab(this, false, it); current = tabs.lastIndex }
            return
        }
        intent.dataString?.let(::safeIncoming)?.let { tabs += Tab(this, false, it); current = tabs.lastIndex }
    }

    /** Notificación de una web tocada: si la web sigue viva, decide ella; si no, se abre la web que la mandó (#34). */
    private fun fromNotification(i: Intent?): String? {
        val tag = i?.getStringExtra(WebNotifs.EXTRA_TAG) ?: return null
        val source = i.getStringExtra(WebNotifs.EXTRA_SOURCE)?.let(::safeIncoming)
        if (WebNotifs.clicked(tag)) {
            // Plan B por si la web no pide ponerse delante: ir a la pestaña de esa web (#39)
            val h = History.hostOf(source ?: return null)
            if (tabs.getOrNull(current)?.let { History.hostOf(it.url) } != h)
                tabs.indexOfFirst { History.hostOf(it.url) == h }.takeIf { it >= 0 }?.let { current = it; ui.closeGrid++ }
            return null
        }
        return source
    }

    override fun onDestroy() {
        if (isFinishing && Privacy.clearOnExit) { Privacy.clearAll(this); tabsFile.delete() }
        super.onDestroy()
    }

    // Pantallas que abre la propia Brisa (selector de archivos, permisos, compartir, huella, abrir una
    // descarga…): al volver de ellas NO se bloquean las privadas, salvo que se tarde más de 2 minutos.
    private var ownScreen = false
    private var stoppedAt = 0L
    override fun startActivity(intent: Intent?, options: Bundle?) { ownScreen = true; super.startActivity(intent, options) }
    @Deprecated("Lo usan los ActivityResultLauncher") @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        ownScreen = true; super.startActivityForResult(intent, requestCode, options)
    }

    // «Borrar datos al salir»: 5 minutos fuera de Brisa cuentan como haber salido, se saliera como se saliera
    // (también a otra app con «Compartir», #49). Se borra a los 5 minutos con un temporizador (#51) y, si
    // Android congeló Brisa y no llegó a saltar, al volver, ANTES de abrir el enlace o la notificación
    // que la trae de vuelta (#50: onNewIntent llega antes que onStart).
    private val wipeTimer = android.os.Handler(android.os.Looper.getMainLooper())
    private val wipeNow = Runnable { wipeTabs(); Privacy.clearAll(this); wipeDue = false }
    private var wipeDue = false
    private fun wipeIfAway() {
        wipeTimer.removeCallbacks(wipeNow)
        if (wipeDue && Privacy.clearOnExit && System.currentTimeMillis() - stoppedAt > WIPE_AFTER) wipeNow.run()
        wipeDue = false
    }

    override fun onStart() {
        super.onStart()
        wipeIfAway()
        if (ownScreen && System.currentTimeMillis() - stoppedAt > 120_000) {
            if (Privacy.lockPrivate && tabs.any { it.private }) ui.privateLocked = true
            Secrets.relock++
        }
        ownScreen = false
    }

    override fun onStop() {
        super.onStop()
        Report.save()
        stoppedAt = System.currentTimeMillis()
        if (Privacy.clearOnExit) { wipeDue = true; wipeTimer.postDelayed(wipeNow, WIPE_AFTER) }
        // Al salir de la app, las pestañas privadas quedan bloqueadas hasta la huella/PIN
        if (!ownScreen && Privacy.lockPrivate && tabs.any { it.private }) ui.privateLocked = true
        if (!ownScreen) Secrets.relock++
    }

    override fun onResume() {
        super.onResume()
        Tab.foreground = true
        // Pestaña visible que se cayó con Brisa en segundo plano: se recupera ahora (#44)
        tabs.getOrNull(current)?.let { t -> if (!(t.private && ui.privateLocked)) t.wake() }
        // Una privada bloqueada sigue en pausa (sin audio ni vídeo) hasta desbloquearla
        tabs.getOrNull(current)?.let { t -> if (!(t.private && ui.privateLocked)) t.active(true) }
    }

    override fun onPause() {
        super.onPause()
        Tab.foreground = false
        ui.captureNow?.invoke() // miniatura de la pestaña que se ve, por si Android cierra Brisa (#58)
        tabs.forEach { it.active(false) }
        saveTabs()
    }

    private fun saveTabs() {
        val a = org.json.JSONArray()
        tabs.filter { !it.private && !it.isHome }.forEach {
            a.put(org.json.JSONObject().put("url", it.url).put("state", it.state ?: "").put("group", it.group ?: "").put("id", it.id))
        }
        if (!Privacy.clearOnExit) runCatching { tabsFile.writeText(a.toString()) }
    }

    /**
     * «Reiniciar Brisa» (#59): guarda las pestañas y reinicia la app entera, también los procesos de Gecko.
     * Es lo mismo que forzar la detención en los ajustes de Android, pero sin salir de Brisa ni perder nada.
     */
    fun restart() {
        saveTabs(); Report.save()
        startActivity(Intent.makeRestartActivityTask(android.content.ComponentName(this, MainActivity::class.java)))
        val me = android.os.Process.myPid()
        getSystemService(android.app.ActivityManager::class.java)?.runningAppProcesses?.forEach {
            if (it.uid == android.os.Process.myUid() && it.pid != me) android.os.Process.killProcess(it.pid)
        }
        Runtime.getRuntime().exit(0)
    }
}

/** Pantallas con secretos a la vista (contraseñas): sin capturas y se cierran al salir de Brisa (#32). */
object Secrets {
    var open by mutableIntStateOf(0)
    /** Sube al salir de la app (o al volver tras más de 2 min en una pantalla propia): hay que desbloquear otra vez. */
    var relock by mutableIntStateOf(0)
}

private const val WIPE_AFTER = 5 * 60_000L

class UiState {
    var context by mutableStateOf<Pair<Tab, org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement>?>(null)
    var fullScreen by mutableStateOf(false)
    val dialogs = mutableStateListOf<Dialog>()
    var privateLocked by mutableStateOf(false)
    /** Sube cuando hay que salir de la cuadrícula de pestañas (una web pide ponerse delante). */
    var closeGrid by mutableIntStateOf(0)
    /** Atajos de teclado: los recibe la actividad y los resuelve la pantalla. */
    var shortcut: ((String) -> Boolean)? = null
    /** Guardar ya la miniatura de la pestaña que se ve (al salir de Brisa, #58). */
    var captureNow: (() -> Unit)? = null
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
    LaunchedEffect(ui.closeGrid) { if (ui.closeGrid > 0) showTabs = false }
    var showMenu by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(false) }
    val geckoView = remember {
        PullGeckoView(ctx).apply {
            // Autorrelleno de Android (Bitwarden, 1Password, KeePassDX…): Gecko describe los campos de la web a Android
            setAutofillEnabled(true)
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_YES
        }
    }
    val favs = remember { Favorites(ctx) }
    val scope = rememberCoroutineScope()
    /** Pestaña cuya sesión tiene ahora la vista (cambia en LaunchedEffect(tab), después de que cambie tab). */
    val attached = remember { arrayOfNulls<Tab>(1) }
    /**
     * Miniatura de [t], que tiene que ser la que está en la vista. No se hace si aún no ha pintado nada o
     * se está recargando (saldría en blanco): mejor quedarse con la anterior (#58).
     */
    suspend fun capture(t: Tab) {
        if (attached[0] !== t || t.isHome || !t.painted || t.progress < 50 || (t.private && ui.privateLocked) || !t.session.isOpen) return
        val b = kotlinx.coroutines.withTimeoutOrNull(300) {
            kotlinx.coroutines.suspendCancellableCoroutine<android.graphics.Bitmap?> { c ->
                geckoView.capturePixels().accept({ if (c.isActive) c.resumeWith(Result.success(it)) }, { if (c.isActive) c.resumeWith(Result.success(null)) })
            }
        }
        t.saveThumb(b)
    }
    SideEffect { ui.captureNow = { scope.launch { capture(tab) } } }
    // Estirar para recargar
    val density = androidx.compose.ui.platform.LocalDensity.current
    var pull by remember { mutableFloatStateOf(0f) }
    var refreshing by remember { mutableStateOf(false) }
    val threshold = with(density) { 96.dp.toPx() }
    SideEffect {
        geckoView.pullEnabled = Settings.pullRefresh && !tab.isHome && !ui.fullScreen
        geckoView.onPull = { pull = it }
        geckoView.onRelease = { d ->
            pull = 0f
            if (d >= threshold && !tab.isHome) { refreshing = true; tab.session.reload() }
        }
    }
    // #47: progress y scroll se leen con snapshotFlow, sin recomponer toda la pantalla en cada evento
    LaunchedEffect(refreshing, tab) {
        if (!refreshing) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { tab.progress }.first { it == 100 }
        kotlinx.coroutines.delay(250); refreshing = false
    }

    LaunchedEffect(tab) {
        // #53: se cierra el toque a medias de la pestaña que se deja y su menú de enlace, si había uno
        geckoView.cancelTouch(); ui.context = null
        // #58: miniatura de la pestaña que se deja, antes de quitarle la vista
        attached[0]?.let { if (it !== tab) capture(it) }
        tab.awaitOpen() // antes de dársela a la vista: si no, GeckoView abriría la sesión con otro motor
        geckoView.releaseSession(); geckoView.setSession(tab.session); collapsed = false
        attached[0] = tab
        Tab.visible = tab; ui.context = null
        if (!(tab.private && ui.privateLocked)) { tab.active(true); tab.wake() }
        // Las pestañas en segundo plano se pausan: no gastan red ni procesador mientras carga la visible
        tabs.forEach { if (it !== tab) it.active(false) }
    }
    LaunchedEffect(tab) {
        androidx.compose.runtime.snapshotFlow { tab.barsHidden }.collect { collapsed = it }
    }

    // Pestaña privada bloqueada: ni se ve el dominio, ni se navega, ni suena, hasta la huella/PIN
    val locked = tab.private && ui.privateLocked
    LaunchedEffect(locked) {
        if (locked) { tab.active(false); editing = false; showMenu = false; tab.endFind() }
        else { tab.active(true); tab.wake() }
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
    // …ni con las contraseñas a la vista (#32)
    val anyPrivate = tabs.any { it.private } || Secrets.open > 0
    LaunchedEffect(anyPrivate) {
        val act = ctx as? android.app.Activity
        // Con pestañas privadas: sin capturas de pantalla ni miniatura en «Recientes»
        if (anyPrivate) act?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else act?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        if (android.os.Build.VERSION.SDK_INT >= 33) act?.setRecentsScreenshotEnabled(!anyPrivate)
    }

    SideEffect {
        ui.shortcut = { k ->
            when {
                // Bloqueada: solo se permite cambiar de pestaña o abrir una nueva; lo demás pide desbloquear
                locked && k !in setOf("nueva", "privada", "siguiente", "anterior") && !k.startsWith("pestaña:") -> ui.unlockPrivate(ctx)
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

    BackHandler(ui.fullScreen || showTabs || showMenu || editing || tab.findText != null || (tab.canBack && !locked)) {
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
            if (wide && !ui.fullScreen && locked) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = 720.dp)) { LockedBar(tabs.size, onUnlock = { ui.unlockPrivate(ctx) }, onTabs = { showTabs = true }) }
            }
            else if (wide && !ui.fullScreen) {
                TabletToolbar(tab, tabs.size, favs, editing, { editing = it }, onMenu = { showMenu = true },
                    onTabs = { showTabs = true; scope.launch { capture(tab) } },
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
                // #55: pestaña que aún no ha pintado nada (p. ej. un enlace que llega de otra app): fondo neutro
                // en vez del último fotograma de la pestaña anterior. Las que ya tenían contenido no se tapan.
                if (!tab.isHome && !tab.painted) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    if (tab.progress < 100) LinearProgressIndicator({ tab.progress / 100f }, Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
                if (tab.isHome) HomePage(favs, tab.private) { tab.open(it) }
                if (tab.private && ui.privateLocked) PrivateLock { ui.unlockPrivate(ctx) }
                // Indicador de «estirar para recargar»: arriba del contenido (en tablet, debajo de la barra)
                if (pull > 0f || refreshing) {
                    val frac = (pull / threshold).coerceIn(0f, 1f)
                    val y = with(density) { if (refreshing) 24.dp.toPx() else (pull * 0.45f).coerceAtMost(80.dp.toPx()) }
                    Box(Modifier.align(Alignment.TopCenter).offset { androidx.compose.ui.unit.IntOffset(0, y.toInt()) }
                        .size(40.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        else Icon(Icons.Filled.Refresh, "Recargar", Modifier.size(22.dp).graphicsLayer {
                            rotationZ = frac * 270f; alpha = 0.4f + 0.6f * frac },
                            tint = if (frac >= 1f) MaterialTheme.colorScheme.primary else glassContent())
                    }
                }
            }
        }

        // Barra inferior estilo Safari (solo móvil; oculta con vídeo a pantalla completa)
        if (!ui.fullScreen && !wide) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding().imePadding().padding(horizontal = 12.dp).padding(bottom = 8.dp)
        ) {
            if (locked) LockedBar(tabs.size, onUnlock = { ui.unlockPrivate(ctx) }, onTabs = { showTabs = true })
            else AnimatedContent(collapsed && !editing && !tab.isHome && tab.findText == null, label = "bar") { small ->
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
                        IconButton({ showTabs = true; scope.launch { capture(tab) } }) {
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
        if (showMenu) PageMenu(tab, { showMenu = false }, { newTab(private = true) }, { showHistory = true }, { showSettings = true }, { showDownloads = true },
            openTab = { u -> tabs.add(current + 1, Tab(ctx, tab.private, u)); select(current + 1) })
        if (showDownloads) DownloadsSheet { showDownloads = false }
        // #16: ventanita de una extensión (su botón en el menú «aA»)
        Extensions.popup?.let { p -> key(p) { ExtensionPopupSheet(p) } }
        ui.dialogs.toList().forEach { d -> key(d) { d { ui.dialogs.remove(d) } } }
        ui.context?.let { (t, e) ->
            LinkSheet(e, dismiss = { ui.context = null },
                newTab = { u, priv ->
                    val nt = Tab(ctx, priv, u)
                    tabs.add(tabs.indexOf(t) + 1, nt)
                    // Si cambia de modo (normal ↔ privada) se salta a ella; si no, se queda detrás como en Safari
                    if (priv != t.private) select(tabs.indexOf(nt))
                    android.widget.Toast.makeText(ctx, when {
                        priv && t.private -> "Abierta en otra pestaña privada"
                        priv -> "Abierta en pestaña privada"
                        else -> "Abierta en una pestaña nueva" }, android.widget.Toast.LENGTH_SHORT).show()
                },
                open = { t.open(it) }, fromPrivate = t.private)
        }
        if (showSettings) SettingsSheet { showSettings = false }
        if (showHistory) HistorySheet({ showHistory = false }) { tab.open(it) }

        DownloadFly(ui)

        AnimatedVisibility(showTabs, enter = fadeIn() + scaleIn(initialScale = 1.1f), exit = fadeOut() + scaleOut(targetScale = 1.1f)) {
            TabGrid(tabs, current, ui, select = { select(it); showTabs = false }, onClose = { i -> closeTab(i) },
                onNew = { newTab(it) }, onDone = { showTabs = false })
        }
    }
}
}

/** Direcciones que otras apps pueden abrir en Brisa: solo webs (nada de file://, about:, javascript:…). */
fun safeIncoming(s: String): String? {
    val t = s.trim()
    return if (t.startsWith("https://", true) || t.startsWith("http://", true)) t else null
}
