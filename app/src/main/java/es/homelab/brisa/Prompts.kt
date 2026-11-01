package es.homelab.brisa

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.*

/** Ventana que la pantalla principal pinta encima; [close] la quita. */
typealias Dialog = @Composable (close: () -> Unit) -> Unit

/** Lo que las ventanas de las webs y los permisos necesitan de la actividad. */
interface PromptHost {
    /** Pide PIN/huella del móvil antes de enseñar contraseñas. */
    fun unlock(reason: String, cb: (Boolean) -> Unit)
    fun show(d: Dialog)
    fun pickFiles(mimes: Array<String>?, multiple: Boolean, cb: (List<Uri>?) -> Unit)
    fun androidPermissions(perms: Array<String>, cb: (Boolean) -> Unit)
}

/**
 * Ventanas que piden las webs: alert/confirm/prompt, desplegables, usuario y contraseña (HTTP),
 * subir archivos, fecha y hora.
 */
class Prompts(private val ctx: Context, private val host: () -> PromptHost?) : PromptDelegate {

    private fun <T : BasePrompt> ask(p: T, d: (T, (PromptResponse) -> Unit) -> Dialog): GeckoResult<PromptResponse> {
        val r = GeckoResult<PromptResponse>()
        val h = host() ?: return GeckoResult.fromValue(p.dismiss())
        var answered = false
        h.show(d(p) { resp -> if (!answered) { answered = true; r.complete(resp) } }.let { inner ->
            { close -> inner { close(); if (!answered) { answered = true; r.complete(p.dismiss()) } } }
        })
        return r
    }

    override fun onAlertPrompt(s: GeckoSession, p: AlertPrompt) = ask(p) { pr, done ->
        { close -> AlertDialog(onDismissRequest = { close() },
            title = pr.title?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
            text = { Text(pr.message ?: "") },
            confirmButton = { TextButton({ done(pr.dismiss()); close() }) { Text("Aceptar") } }) }
    }

    override fun onButtonPrompt(s: GeckoSession, p: ButtonPrompt) = ask(p) { pr, done ->
        { close -> AlertDialog(onDismissRequest = { close() },
            title = pr.title?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
            text = { Text(pr.message ?: "") },
            confirmButton = { TextButton({ done(pr.confirm(ButtonPrompt.Type.POSITIVE)); close() }) { Text("Aceptar") } },
            dismissButton = { TextButton({ done(pr.confirm(ButtonPrompt.Type.NEGATIVE)); close() }) { Text("Cancelar") } }) }
    }

    override fun onTextPrompt(s: GeckoSession, p: TextPrompt) = ask(p) { pr, done ->
        { close ->
            var v by remember { mutableStateOf(pr.defaultValue ?: "") }
            AlertDialog(onDismissRequest = { close() },
                title = pr.title?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                text = { Column { Text(pr.message ?: ""); OutlinedTextField(v, { v = it }, Modifier.padding(top = 12.dp), singleLine = true) } },
                confirmButton = { TextButton({ done(pr.confirm(v)); close() }) { Text("Aceptar") } },
                dismissButton = { TextButton({ close() }) { Text("Cancelar") } })
        }
    }

    override fun onAuthPrompt(s: GeckoSession, p: AuthPrompt) = ask(p) { pr, done ->
        { close ->
            val onlyPass = pr.authOptions.flags and AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD != 0
            var user by remember { mutableStateOf(pr.authOptions.username ?: "") }
            var pass by remember { mutableStateOf(pr.authOptions.password ?: "") }
            AlertDialog(onDismissRequest = { close() },
                title = { Text("Iniciar sesión") },
                text = { Column {
                    Text(pr.message ?: pr.title ?: "")
                    if (!onlyPass) OutlinedTextField(user, { user = it }, Modifier.padding(top = 12.dp), singleLine = true, label = { Text("Usuario") })
                    OutlinedTextField(pass, { pass = it }, Modifier.padding(top = 8.dp), singleLine = true, label = { Text("Contraseña") },
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                } },
                confirmButton = { TextButton({ done(if (onlyPass) pr.confirm(pass) else pr.confirm(user, pass)); close() }) { Text("Entrar") } },
                dismissButton = { TextButton({ close() }) { Text("Cancelar") } })
        }
    }

    override fun onChoicePrompt(s: GeckoSession, p: ChoicePrompt) = ask(p) { pr, done ->
        { close ->
            val multiple = pr.type == ChoicePrompt.Type.MULTIPLE
            // Aplanar grupos (<optgroup>) en una sola lista
            val flat = remember { buildList { fun add(cs: Array<ChoicePrompt.Choice>, depth: Int) {
                cs.forEach { c -> add(c to depth); c.items?.let { add(it, depth + 1) } } }; add(pr.choices, 0) } }
            val chosen = remember { mutableStateListOf<ChoicePrompt.Choice>().apply { addAll(flat.map { it.first }.filter { it.selected }) } }
            AlertDialog(onDismissRequest = { close() },
                title = pr.title?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                    flat.forEach { (c, depth) ->
                        when {
                            c.separator -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            c.items != null -> Text(c.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = (depth * 16).dp, top = 8.dp))
                            else -> Row(Modifier.fillMaxWidth().clickable(enabled = !c.disabled) {
                                if (multiple) { if (c in chosen) chosen.remove(c) else chosen.add(c) }
                                else { done(pr.confirm(c)); close() }
                            }.padding(start = (depth * 16).dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (multiple) Checkbox(c in chosen, null, enabled = !c.disabled)
                                else RadioButton(c.selected, null, enabled = !c.disabled)
                                Text(c.label, Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                } },
                confirmButton = { if (multiple) TextButton({ done(pr.confirm(chosen.toTypedArray())); close() }) { Text("Aceptar") } },
                dismissButton = { TextButton({ close() }) { Text("Cancelar") } })
        }
    }

    // ---- Contraseñas ----------------------------------------------------------------------------
    override fun onLoginSave(s: GeckoSession, req: AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSaveOption>): GeckoResult<PromptResponse> {
        val opt = req.options.firstOrNull() ?: return GeckoResult.fromValue(req.dismiss())
        val e = opt.value
        val host = History.hostOf(e.origin)
        if (!Passwords.offerSave || host in Passwords.never || Passwords.isKnown(e)) return GeckoResult.fromValue(req.dismiss())
        val updating = Passwords.logins.any { it.origin == e.origin && it.username == (e.username ?: "") }
        return ask(req) { r, done ->
            { close -> AlertDialog(onDismissRequest = { close() },
                icon = { SiteIcon(host, 40.dp) },
                title = { Text(if (updating) "¿Actualizar la contraseña de $host?" else "¿Guardar la contraseña de $host?") },
                text = { Text(listOfNotNull(e.username?.takeIf { it.isNotBlank() }, "•".repeat(e.password.length.coerceAtMost(12))).joinToString("\n")) },
                confirmButton = { TextButton({ done(r.confirm(opt)); close() }) { Text(if (updating) "Actualizar" else "Guardar") } },
                dismissButton = { Row {
                    if (!updating) TextButton({ Passwords.addNever(host); close() }) { Text("Nunca") }
                    TextButton({ close() }) { Text("Ahora no") }
                } }) }
        }
    }

    override fun onLoginSelect(s: GeckoSession, req: AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSelectOption>): GeckoResult<PromptResponse> {
        if (req.options.isEmpty()) return GeckoResult.fromValue(req.dismiss())
        if (req.options.size == 1) return GeckoResult.fromValue(req.confirm(req.options[0]))
        return ask(req) { r, done ->
            { close -> AlertDialog(onDismissRequest = { close() },
                title = { Text("Elegir cuenta") },
                text = { Column { r.options.forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable { done(r.confirm(o)); close() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SiteIcon(History.hostOf(o.value.origin), 28.dp, 6.dp)
                        Text(o.value.username?.ifBlank { null } ?: "(sin usuario)", Modifier.padding(start = 12.dp))
                    }
                } } },
                confirmButton = { TextButton({ close() }) { Text("Cancelar") } }) }
        }
    }

    override fun onDateTimePrompt(s: GeckoSession, p: DateTimePrompt): GeckoResult<PromptResponse> {
        val r = GeckoResult<PromptResponse>()
        val cal = java.util.Calendar.getInstance()
        val def = p.defaultValue
        fun pad(n: Int) = n.toString().padStart(2, '0')
        when (p.type) {
            DateTimePrompt.Type.TIME -> {
                if (!def.isNullOrBlank()) runCatching { def.split(":").let { cal.set(java.util.Calendar.HOUR_OF_DAY, it[0].toInt()); cal.set(java.util.Calendar.MINUTE, it[1].toInt()) } }
                android.app.TimePickerDialog(ctx, { _, h, m -> r.complete(p.confirm("${pad(h)}:${pad(m)}")) },
                    cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).apply {
                    setOnCancelListener { r.complete(p.dismiss()) }; show() }
            }
            DateTimePrompt.Type.DATE, DateTimePrompt.Type.MONTH, DateTimePrompt.Type.WEEK -> {
                if (!def.isNullOrBlank()) runCatching { def.split("-").let { cal.set(it[0].toInt(), it[1].toInt() - 1, it.getOrNull(2)?.toIntOrNull() ?: 1) } }
                android.app.DatePickerDialog(ctx, { _, y, m, d ->
                    r.complete(p.confirm(if (p.type == DateTimePrompt.Type.MONTH) "$y-${pad(m + 1)}" else "$y-${pad(m + 1)}-${pad(d)}"))
                }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).apply {
                    setOnCancelListener { r.complete(p.dismiss()) }; show() }
            }
            else -> r.complete(p.dismiss())
        }
        return r
    }

    override fun onFilePrompt(s: GeckoSession, p: FilePrompt): GeckoResult<PromptResponse> {
        val r = GeckoResult<PromptResponse>()
        val h = host() ?: return GeckoResult.fromValue(p.dismiss())
        h.pickFiles(p.mimeTypes, p.type == FilePrompt.Type.MULTIPLE) { uris ->
            r.complete(when {
                uris.isNullOrEmpty() -> p.dismiss()
                uris.size == 1 -> p.confirm(ctx, uris[0])
                else -> p.confirm(ctx, uris.toTypedArray())
            })
        }
        return r
    }
}

/** Permisos que piden las webs: ubicación, notificaciones, cámara y micrófono. */
class Permissions(private val host: () -> PromptHost?) : PermissionDelegate {

    override fun onAndroidPermissionsRequest(s: GeckoSession, perms: Array<String>?, cb: PermissionDelegate.Callback) {
        val h = host()
        if (perms.isNullOrEmpty() || h == null) { cb.grant(); return }
        h.androidPermissions(perms) { ok -> if (ok) cb.grant() else cb.reject() }
    }

    override fun onContentPermissionRequest(s: GeckoSession, p: ContentPermission): GeckoResult<Int> {
        val what = when (p.permission) {
            PermissionDelegate.PERMISSION_GEOLOCATION -> "conocer tu ubicación"
            PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "enviarte notificaciones"
            PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "guardar datos en el móvil"
            PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> return GeckoResult.fromValue(ContentPermission.VALUE_DENY)
            PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW)
            PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW) // vídeo con DRM
            else -> return GeckoResult.fromValue(ContentPermission.VALUE_DENY)
        }
        val r = GeckoResult<Int>()
        val h = host() ?: return GeckoResult.fromValue(ContentPermission.VALUE_DENY)
        var answered = false
        fun answer(v: Int) { if (!answered) { answered = true; r.complete(v) } }
        h.show { close -> PermissionDialog(History.hostOf(p.uri), what, { answer(it); close() }, { answer(ContentPermission.VALUE_DENY); close() }) }
        return r
    }

    override fun onMediaPermissionRequest(
        s: GeckoSession, uri: String,
        video: Array<PermissionDelegate.MediaSource>?, audio: Array<PermissionDelegate.MediaSource>?,
        cb: PermissionDelegate.MediaCallback
    ) {
        val h = host() ?: run { cb.reject(); return }
        val what = listOfNotNull(if (!video.isNullOrEmpty()) "la cámara" else null, if (!audio.isNullOrEmpty()) "el micrófono" else null).joinToString(" y ")
        val v = video?.firstOrNull { it.source == PermissionDelegate.MediaSource.SOURCE_CAMERA } ?: video?.firstOrNull()
        val a = audio?.firstOrNull()
        var answered = false
        h.show { close -> PermissionDialog(History.hostOf(uri), "usar $what",
            { ok -> if (!answered) { answered = true; if (ok == ContentPermission.VALUE_ALLOW) cb.grant(v, a) else cb.reject() }; close() },
            { if (!answered) { answered = true; cb.reject() }; close() }) }
    }
}

@Composable
private fun PermissionDialog(site: String, what: String, answer: (Int) -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss,
        icon = { SiteIcon(site, 40.dp) },
        title = { Text("¿Permitir que $site pueda $what?") },
        confirmButton = { TextButton({ answer(ContentPermission.VALUE_ALLOW) }) { Text("Permitir") } },
        dismissButton = { TextButton({ answer(ContentPermission.VALUE_DENY) }) { Text("No permitir") } })
}
