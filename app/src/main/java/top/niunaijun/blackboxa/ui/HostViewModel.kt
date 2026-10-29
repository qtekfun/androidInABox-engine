package top.niunaijun.blackboxa.ui

import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.App
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackboxa.bean.AppInfo
import top.niunaijun.blackboxa.bean.GmsBean
import top.niunaijun.blackboxa.bean.GmsInstallBean
import top.niunaijun.blackboxa.bean.InstalledAppBean
import top.niunaijun.blackboxa.data.AppsRepository
import top.niunaijun.blackboxa.data.GmsRepository
import top.niunaijun.blackboxa.data.ProfileNames
import kotlin.coroutines.resume

const val GMS_GSF = "com.google.android.gsf"
const val GMS_CORE = "com.google.android.gms"
const val GMS_STORE = "com.android.vending"
val GMS_PACKAGES = listOf(GMS_GSF, GMS_CORE, GMS_STORE)

data class UiProfile(val id: Int, val name: String, val appCount: Int)

data class UiApp(
    val name: String,
    val packageName: String,
    val sourceDir: String,
    val icon: ImageBitmap?,
)

data class UiInstalled(
    val name: String,
    val packageName: String,
    val icon: ImageBitmap?,
    val alreadyAdded: Boolean,
)

/** Estado de la interfaz. Reutiliza los repositorios y la API del motor. */
class HostViewModel : ViewModel() {

    private val appsRepo = AppsRepository()
    private val gmsRepo = GmsRepository()

    private val _profiles = MutableStateFlow<List<UiProfile>>(emptyList())
    val profiles: StateFlow<List<UiProfile>> = _profiles.asStateFlow()

    private val _apps = MutableStateFlow<List<UiApp>>(emptyList())
    val apps: StateFlow<List<UiApp>> = _apps.asStateFlow()

    private val _appsLoading = MutableStateFlow(false)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    private val _installed = MutableStateFlow<List<UiInstalled>>(emptyList())
    val installed: StateFlow<List<UiInstalled>> = _installed.asStateFlow()

    private val _installedLoading = MutableStateFlow(false)
    val installedLoading: StateFlow<Boolean> = _installedLoading.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _gms = MutableStateFlow<List<GmsBean>>(emptyList())
    val gms: StateFlow<List<GmsBean>> = _gms.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val ctx get() = App.getContext()
    private fun str(id: Int, vararg a: Any) = ctx.getString(id, *a)
    private fun say(msg: String) { _messages.tryEmit(msg) }

    private val prefs get() = AppManager.mRemarkSharedPreferences

    fun profileName(id: Int): String = ProfileNames.of(id)

    private fun Drawable?.toUiBitmap(): ImageBitmap? = try {
        this?.toBitmap(96, 96)?.asImageBitmap()
    } catch (_: Throwable) {
        null
    }

    private suspend fun <T> awaitLive(call: (MutableLiveData<T>) -> Unit): T? {
        val live = MutableLiveData<T>()
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val obs = object : Observer<T> {
                    override fun onChanged(value: T) {
                        live.removeObserver(this)
                        if (cont.isActive) cont.resume(value)
                    }
                }
                live.observeForever(obs)
                cont.invokeOnCancellation { live.removeObserver(obs) }
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        call(live)
                    } catch (t: Throwable) {
                        live.postValue(null)
                    }
                }
            }
        }
    }

    // ---- Perfiles ----

    fun refreshProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val core = BlackBoxCore.get()
                val ids = core.users.map { it.id }.sorted().ifEmpty { listOf(0) }
                _profiles.value = ids.map { id ->
                    val count = try { core.getInstalledApplications(0, id).size } catch (_: Throwable) { 0 }
                    UiProfile(id, profileName(id), count)
                }
            } catch (t: Throwable) {
                _profiles.value = listOf(UiProfile(0, profileName(0), 0))
            }
        }
    }

    /** Crea un perfil vacio y devuelve su id mediante [onCreated]. */
    fun createProfile(onCreated: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val core = BlackBoxCore.get()
            val next = (core.users.maxOfOrNull { it.id } ?: -1) + 1
            try { core.createUser(next) } catch (_: Throwable) { }
            refreshProfiles()
            withContext(Dispatchers.Main) { onCreated(next) }
        }
    }

    /** Elimina el perfil con sus apps, cuentas y datos. Solo se llama tras confirmacion explicita. */
    fun deleteProfile(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            val name = profileName(id)
            try {
                BlackBoxCore.get().deleteUser(id)
                ProfileNames.forget(id)
                say(str(R.string.profile_deleted, name))
            } catch (t: Throwable) {
                say(str(R.string.profile_delete_failed, name))
            }
            _busy.value = false
            refreshProfiles()
        }
    }

    fun renameProfile(id: Int, name: String) {
        prefs.edit().putString("Remark$id", name.trim()).apply()
        refreshProfiles()
    }

    // ---- Apps de un perfil ----

    fun loadApps(userId: Int) {
        _appsLoading.value = true
        viewModelScope.launch {
            val list = awaitLive<List<AppInfo>> { appsRepo.getVmInstallList(userId, it) }
            _apps.value = withContext(Dispatchers.Default) {
                list.orEmpty().map { UiApp(it.name, it.packageName, it.sourceDir, it.icon.toUiBitmap()) }
            }
            _appsLoading.value = false
        }
    }

    fun launch(pkg: String, userId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try { BlackBoxCore.get().launchApk(pkg, userId) } catch (_: Throwable) { false }
            if (!ok) say(str(R.string.app_launch_failed))
        }
    }

    /** Opción por app: perfil de propiedades falso. Se aplica al reiniciar la app invitada. */
    fun isSpoofEnabled(pkg: String, userId: Int): Boolean =
        try { BlackBoxCore.get().isPropertySpoofEnabled(pkg, userId) } catch (_: Throwable) { false }

    fun setSpoofEnabled(pkg: String, userId: Int, on: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try { BlackBoxCore.get().setPropertySpoofEnabled(pkg, userId, on) } catch (_: Throwable) { }
        }
    }

    fun stop(pkg: String, userId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try { BlackBoxCore.get().stopPackage(pkg, userId) } catch (_: Throwable) { }
            say(str(R.string.app_stopped))
        }
    }

    fun clearData(pkg: String, userId: Int) {
        viewModelScope.launch {
            val msg = awaitLive<String> { appsRepo.clearApkData(pkg, userId, it) }
            if (msg != null) say(msg)
        }
    }

    fun uninstall(pkg: String, userId: Int) {
        viewModelScope.launch {
            _busy.value = true
            val msg = awaitLive<String> { appsRepo.unInstall(pkg, userId, it) }
            _busy.value = false
            if (msg != null) say(msg)
            loadApps(userId)
            refreshProfiles()
        }
    }

    // ---- Anadir app ----

    fun loadInstalled(userId: Int) {
        _installedLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                appsRepo.previewInstallList()
                val loading = MutableLiveData<Boolean>()
                val result = awaitLive<List<InstalledAppBean>> {
                    appsRepo.getInstalledAppList(userId, loading, it)
                }
                _installed.value = withContext(Dispatchers.Default) {
                    result.orEmpty()
                        .map { UiInstalled(it.name, it.packageName, it.icon.toUiBitmap(), it.isInstall) }
                        .sortedBy { it.name.lowercase() }
                }
            } finally {
                _installedLoading.value = false
            }
        }
    }

    /** [source] es un nombre de paquete instalado o una URI de archivo. */
    fun install(source: String, userId: Int, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            val msg = awaitLive<String> { appsRepo.installApk(source, userId, it) }
            _busy.value = false
            if (msg != null) say(msg)
            val ok = msg == str(R.string.install_success)
            refreshProfiles()
            onDone(ok)
        }
    }

    /** Instala uno o varios ficheros (APK sueltos o bundles) como un unico conjunto. */
    fun install(uris: List<Uri>, userId: Int, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            val msg = awaitLive<String> { appsRepo.installUris(uris.map { it.toString() }, userId, it) }
            _busy.value = false
            if (msg != null) say(msg)
            val ok = msg == str(R.string.install_success)
            refreshProfiles()
            onDone(ok)
        }
    }

    // ---- GMS ----

    val gmsSupported: Boolean get() = try { BlackBoxCore.get().isSupportGms } catch (_: Throwable) { false }

    /** Paquetes GMS presentes por perfil (GSF, GmsCore, Play Store). */
    private val _gmsPkgs = MutableStateFlow<Map<Int, Set<String>>>(emptyMap())
    val gmsPkgs: StateFlow<Map<Int, Set<String>>> = _gmsPkgs.asStateFlow()

    /** Ultimo error legible del motor al instalar GMS, por perfil. */
    private val _gmsError = MutableStateFlow<Map<Int, String>>(emptyMap())
    val gmsError: StateFlow<Map<Int, String>> = _gmsError.asStateFlow()

    /** Perfiles con microG en lugar de GMS real (ADR-0007). */
    private val _microg = MutableStateFlow<Set<Int>>(emptySet())
    val microg: StateFlow<Set<Int>> = _microg.asStateFlow()

    /**
     * Cambia el modo del perfil. Si cambia de verdad, desinstala el conjunto anterior
     * (GSF, GmsCore y Play Store/Companion) en ese perfil; la confirmación la pide la UI.
     */
    fun setGmsMode(userId: Int, microg: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            withContext(Dispatchers.IO) {
                val core = BlackBoxCore.get()
                if (core.isMicrogMode(userId) != microg) {
                    GMS_PACKAGES.forEach { try { core.uninstallPackageAsUser(it, userId) } catch (_: Throwable) {} }
                    core.setMicrogMode(userId, microg)
                }
            }
            _gmsError.update { it - userId }
            _busy.value = false
            say(str(R.string.gms_switched))
            refreshProfiles()
            loadGms()
        }
    }

    fun loadGms() {
        viewModelScope.launch {
            val list = awaitLive<List<GmsBean>> { gmsRepo.getGmsInstalledList(it) }.orEmpty()
            _gms.value = list
            _microg.value = withContext(Dispatchers.IO) {
                list.filter { try { BlackBoxCore.get().isMicrogMode(it.userID) } catch (_: Throwable) { false } }
                    .map { it.userID }.toSet()
            }
            _gmsPkgs.value = withContext(Dispatchers.IO) {
                list.associate { b ->
                    b.userID to GMS_PACKAGES.filter {
                        try { BlackBoxCore.get().isInstalled(it, b.userID) } catch (_: Throwable) { false }
                    }.toSet()
                }
            }
        }
    }

    fun installGms(userId: Int) = gmsAction(userId) { gmsRepo.installGms(userId, it) }
    fun uninstallGms(userId: Int) = gmsAction(userId) { gmsRepo.uninstallGms(userId, it) }

    private fun gmsAction(userId: Int, call: (MutableLiveData<GmsInstallBean>) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _gmsError.update { it - userId }
            val r = awaitLive<GmsInstallBean>(call)
            _busy.value = false
            if (r != null) {
                say(r.msg)
                if (!r.success) _gmsError.update { it + (userId to r.msg) }
            }
            loadGms()
        }
    }

    /**
     * Instala GMS desde archivos elegidos por el usuario. Cada archivo se identifica por su nombre
     * de paquete y se instala en el orden GSF, GmsCore, Play Store, sin importar el orden elegido.
     */
    fun installGmsFiles(userId: Int, uris: List<Uri>) {
        viewModelScope.launch {
            _busy.value = true
            _gmsError.update { it - userId }
            val byPkg = withContext(Dispatchers.IO) {
                uris.mapNotNull { u -> detectPackage(u)?.let { it to u } }.toMap()
            }
            val unknown = uris.size - byPkg.size
            if (byPkg.isEmpty()) {
                val m = str(R.string.gms_files_none)
                say(m); _gmsError.update { it + (userId to m) }
            }
            for (pkg in GMS_PACKAGES) {
                val uri = byPkg[pkg] ?: continue
                val msg = awaitLive<String> { appsRepo.installUris(listOf(uri.toString()), userId, it) }
                val ok = msg == str(R.string.install_success)
                say(if (ok) str(R.string.gms_file_ok, gmsLabel(pkg)) else (msg ?: ""))
                if (!ok) {
                    _gmsError.update { it + (userId to (msg ?: "")) }
                    break
                }
            }
            if (unknown > 0 && byPkg.isNotEmpty()) say(str(R.string.gms_files_unknown, unknown))
            _busy.value = false
            refreshProfiles()
            loadGms()
        }
    }

    private fun gmsLabel(pkg: String) = str(
        when (pkg) {
            GMS_GSF -> R.string.gms_step_gsf
            GMS_CORE -> R.string.gms_step_core
            else -> R.string.gms_step_store
        }
    ).substringAfter(". ")

    /** Nombre de paquete de un APK o bundle (XAPK con manifest.json); null si no se reconoce. */
    private fun detectPackage(uri: Uri): String? {
        val tmp = java.io.File(ctx.cacheDir, "gms-detect-${System.nanoTime()}")
        try {
            ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                ?: return null
            ctx.packageManager.getPackageArchiveInfo(tmp.absolutePath, 0)?.packageName?.let { return it }
            java.util.zip.ZipFile(tmp).use { z ->
                z.getEntry("manifest.json")?.let { e ->
                    val m = z.getInputStream(e).bufferedReader().readText()
                    Regex("\"package_name\"\\s*:\\s*\"([^\"]+)\"").find(m)?.groupValues?.get(1)?.let { return it }
                }
                // APKS/APKM: el APK base va dentro; se extrae para leer su paquete.
                val base = z.entries().asSequence().firstOrNull { it.name == "base.apk" }
                    ?: z.entries().asSequence().firstOrNull { it.name.endsWith(".apk") && !it.name.contains("split") }
                if (base != null) {
                    val inner = java.io.File(ctx.cacheDir, "gms-detect-inner-${System.nanoTime()}")
                    try {
                        z.getInputStream(base).use { i -> inner.outputStream().use { o -> i.copyTo(o) } }
                        return ctx.packageManager.getPackageArchiveInfo(inner.absolutePath, 0)?.packageName
                    } finally {
                        inner.delete()
                    }
                }
            }
            return null
        } catch (_: Throwable) {
            return null
        } finally {
            tmp.delete()
        }
    }

    // ---- Ajustes ----

    private val loader get() = AppManager.mBlackBoxLoader

    fun getSetting(key: String): Boolean = when (key) {
        "root_hide" -> loader.hideRoot()
        "daemon_enable" -> loader.daemonEnable()
        "use_vpn_network" -> loader.useVpnNetwork()
        "disable_flag_secure" -> loader.disableFlagSecure()
        "notification_title_prefix" -> loader.notificationTitlePrefix()
        else -> false
    }

    fun setSetting(key: String, value: Boolean) {
        when (key) {
            "root_hide" -> loader.invalidHideRoot(value)
            "daemon_enable" -> loader.invalidDaemonEnable(value)
            "use_vpn_network" -> loader.invalidUseVpnNetwork(value)
            "disable_flag_secure" -> loader.invalidDisableFlagSecure(value)
            "notification_title_prefix" -> loader.invalidNotificationTitlePrefix(value)
        }
        say(str(R.string.settings_restart))
    }
}
