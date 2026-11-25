package com.example.data.repository

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import com.example.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InAppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<InAppUpdateDownloadState>(InAppUpdateDownloadState.Idle)
    val state: StateFlow<InAppUpdateDownloadState> = _state.asStateFlow()

    private val _installPromptIntents = MutableSharedFlow<Intent>(extraBufferCapacity = 2)
    val installPromptIntents: SharedFlow<Intent> = _installPromptIntents.asSharedFlow()

    private var downloadJob: Job? = null
    private var activeDownloadTag: String? = null

    private val updatesDir: File
        get() = File(context.cacheDir, "updates").apply { mkdirs() }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun getInstalledSigningDetails(): AppSigningDetails {
        val pm = context.packageManager
        val info = getInstalledPackageInfo(pm, context.packageName)
        val activeHashes = info?.let { extractActiveSigningHashes(it) }.orEmpty()
        val primaryHex = activeHashes.firstOrNull().orEmpty()
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val debugCert = info?.let { hasAndroidDebugCertificate(it) } == true
        return AppSigningDetails(
            isDebugBuild = debuggable || debugCert,
            sha256Hex = primaryHex,
            sha256ColonHex = AppSigningFormatter.formatColonHex(primaryHex),
            shortFingerprint = AppSigningFormatter.shortFingerprint(primaryHex)
        )
    }

    fun startBackgroundDownload(updateInfo: AppUpdateInfo, forceRestart: Boolean = false) {
        val downloadUrl = updateInfo.apkDownloadUrl?.takeIf { it.isNotBlank() }
        if (downloadUrl == null) {
            _state.value = InAppUpdateDownloadState.Error(
                updateInfo = updateInfo,
                message = "Pacchetto APK non disponibile per questa release. Riprova più tardi."
            )
            return
        }

        val current = _state.value
        if (!forceRestart) {
            if (current is InAppUpdateDownloadState.Downloading &&
                current.updateInfo.tagName == updateInfo.tagName &&
                downloadJob?.isActive == true
            ) {
                return
            }
            if (current is InAppUpdateDownloadState.ReadyToInstall &&
                current.updateInfo.tagName == updateInfo.tagName &&
                current.apkFile.exists()
            ) {
                return
            }
            if (current is InAppUpdateDownloadState.SignatureMismatch &&
                current.updateInfo.tagName == updateInfo.tagName &&
                current.apkFile.exists()
            ) {
                return
            }
            if (current is InAppUpdateDownloadState.Installing &&
                current.updateInfo.tagName == updateInfo.tagName
            ) {
                return
            }
        }

        val targetApk = apkFileForTag(updateInfo.tagName)
        if (!forceRestart && targetApk.exists() && isCachedSizeValid(targetApk, updateInfo)) {
            when (val cachedValidation = validateDownloadedApkDetailed(targetApk)) {
                ApkValidationResult.Valid -> {
                    _state.value = InAppUpdateDownloadState.ReadyToInstall(updateInfo, targetApk)
                    return
                }
                is ApkValidationResult.SignatureMismatch -> {
                    _state.value = cachedValidation.toDownloadState(updateInfo, targetApk)
                    return
                }
                is ApkValidationResult.Invalid -> {
                    targetApk.delete()
                }
            }
        }

        downloadJob?.cancel()
        activeDownloadTag = updateInfo.tagName

        _state.value = InAppUpdateDownloadState.Downloading(
            updateInfo = updateInfo,
            downloadedBytes = 0L,
            totalBytes = updateInfo.apkSizeBytes,
            progress = 0f
        )

        downloadJob = scope.launch {
            val tempFile = File(updatesDir, "${sanitizeTag(updateInfo.tagName)}.apk.tmp")
            try {
                cleanupOtherUpdates(keepTag = updateInfo.tagName)
                if (tempFile.exists()) tempFile.delete()

                val downloadClient = okHttpClient.newBuilder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("Accept", "application/vnd.android.package-archive, application/octet-stream")
                    .build()

                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _state.value = InAppUpdateDownloadState.Error(
                            updateInfo = updateInfo,
                            message = "Impossibile scaricare il pacchetto (HTTP ${response.code})."
                        )
                        return@launch
                    }

                    val body = response.body ?: throw IOException("Corpo della risposta vuoto.")
                    val contentLength = body.contentLength().takeIf { it > 0L }
                        ?: updateInfo.apkSizeBytes?.takeIf { it > 0L }

                    var downloaded = 0L
                    var lastEmitTime = 0L
                    val buffer = ByteArray(16 * 1024)

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read

                                val now = System.currentTimeMillis()
                                if (now - lastEmitTime >= 80L) {
                                    lastEmitTime = now
                                    val fraction = contentLength?.let { total ->
                                        (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 0.99f)
                                    }
                                    _state.value = InAppUpdateDownloadState.Downloading(
                                        updateInfo = updateInfo,
                                        downloadedBytes = downloaded,
                                        totalBytes = contentLength,
                                        progress = fraction
                                    )
                                }
                            }
                            output.fd.sync()
                        }
                    }

                    if (downloaded <= 0L || (contentLength != null && downloaded < contentLength * 0.95)) {
                        tempFile.delete()
                        _state.value = InAppUpdateDownloadState.Error(
                            updateInfo = updateInfo,
                            message = "Download incompleto. Controlla la connessione e riprova."
                        )
                        return@launch
                    }
                }

                if (targetApk.exists()) targetApk.delete()
                if (!tempFile.renameTo(targetApk)) {
                    tempFile.copyTo(targetApk, overwrite = true)
                    tempFile.delete()
                }

                when (val validation = validateDownloadedApkDetailed(targetApk)) {
                    ApkValidationResult.Valid -> {
                        _state.value = InAppUpdateDownloadState.ReadyToInstall(
                            updateInfo = updateInfo,
                            apkFile = targetApk
                        )
                    }
                    is ApkValidationResult.SignatureMismatch -> {
                        // Conserva targetApk in cache così l'utente può salvarlo in Download
                        _state.value = validation.toDownloadState(updateInfo, targetApk)
                    }
                    is ApkValidationResult.Invalid -> {
                        targetApk.delete()
                        _state.value = InAppUpdateDownloadState.Error(
                            updateInfo = updateInfo,
                            message = validation.message
                        )
                    }
                }
            } catch (e: CancellationException) {
                if (tempFile.exists()) tempFile.delete()
                throw e
            } catch (_: IOException) {
                if (tempFile.exists()) tempFile.delete()
                _state.value = InAppUpdateDownloadState.Error(
                    updateInfo = updateInfo,
                    message = "Connessione interrotta durante il download del pacchetto."
                )
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                _state.value = InAppUpdateDownloadState.Error(
                    updateInfo = updateInfo,
                    message = e.localizedMessage ?: "Errore imprevisto durante il download."
                )
            }
        }
    }

    fun installDownloadedUpdate() {
        val (updateInfo, apkFile) = when (val current = _state.value) {
            is InAppUpdateDownloadState.ReadyToInstall -> current.updateInfo to current.apkFile
            is InAppUpdateDownloadState.Error -> {
                val cached = current.cachedApkFile
                if (cached != null && cached.exists()) {
                    current.updateInfo to cached
                } else {
                    startBackgroundDownload(current.updateInfo, forceRestart = true)
                    return
                }
            }
            else -> return
        }

        if (!apkFile.exists()) {
            startBackgroundDownload(updateInfo, forceRestart = true)
            return
        }

        when (val validation = validateDownloadedApkDetailed(apkFile)) {
            ApkValidationResult.Valid -> Unit
            is ApkValidationResult.SignatureMismatch -> {
                _state.value = validation.toDownloadState(updateInfo, apkFile)
                return
            }
            is ApkValidationResult.Invalid -> {
                apkFile.delete()
                _state.value = InAppUpdateDownloadState.Error(
                    updateInfo = updateInfo,
                    message = validation.message
                )
                return
            }
        }

        _state.value = InAppUpdateDownloadState.Installing(updateInfo)

        scope.launch {
            var session: PackageInstaller.Session? = null
            try {
                val packageInstaller = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                ).apply {
                    setAppPackageName(context.packageName)
                    setSize(apkFile.length())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }
                val sessionId = packageInstaller.createSession(params)
                session = packageInstaller.openSession(sessionId)

                session.openWrite("pantrymaster_update.apk", 0, apkFile.length()).use { out ->
                    apkFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                    session.fsync(out)
                }

                val callbackIntent = Intent(context, AppUpdateInstallReceiver::class.java).apply {
                    action = ACTION_INSTALL_COMMIT
                    putExtra(EXTRA_UPDATE_TAG, updateInfo.tagName)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, callbackIntent, flags)

                session.commit(pendingIntent.intentSender)
                session.close()
                session = null
            } catch (e: SecurityException) {
                session?.abandon()
                _state.value = InAppUpdateDownloadState.Error(
                    updateInfo = updateInfo,
                    message = "Permesso di installazione pacchetti non concesso.",
                    cachedApkFile = apkFile
                )
            } catch (e: Exception) {
                session?.abandon()
                _state.value = InAppUpdateDownloadState.Error(
                    updateInfo = updateInfo,
                    message = e.localizedMessage ?: "Impossibile avviare l'installazione del pacchetto.",
                    cachedApkFile = apkFile
                )
            }
        }
    }

    fun handleInstallStatus(
        status: Int,
        statusMessage: String?,
        confirmIntent: Intent?,
        tagName: String?
    ) {
        val activeInfo = when (val s = _state.value) {
            is InAppUpdateDownloadState.Installing -> s.updateInfo
            is InAppUpdateDownloadState.ReadyToInstall -> s.updateInfo
            is InAppUpdateDownloadState.SignatureMismatch -> s.updateInfo
            is InAppUpdateDownloadState.Error -> s.updateInfo
            is InAppUpdateDownloadState.Downloading -> s.updateInfo
            InAppUpdateDownloadState.Idle -> null
        } ?: return

        val cachedApk = tagName?.let { apkFileForTag(it) }?.takeIf { it.exists() }
            ?: apkFileForTag(activeInfo.tagName).takeIf { it.exists() }

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (confirmIntent != null) {
                    _installPromptIntents.tryEmit(confirmIntent)
                } else {
                    _state.value = InAppUpdateDownloadState.Error(
                        updateInfo = activeInfo,
                        message = "Impossibile aprire la finestra di conferma installazione.",
                        cachedApkFile = cachedApk
                    )
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                cachedApk?.delete()
                _state.value = InAppUpdateDownloadState.Idle
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                if (cachedApk != null) {
                    _state.value = InAppUpdateDownloadState.ReadyToInstall(activeInfo, cachedApk)
                } else {
                    _state.value = InAppUpdateDownloadState.Idle
                }
            }

            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                if (cachedApk != null && cachedApk.exists()) {
                    val installed = getInstalledSigningDetails()
                    val archiveInfo = getPackageArchiveInfoCompat(context.packageManager, cachedApk.absolutePath)
                    val archiveHex = archiveInfo?.let { extractAllSigningHashes(it).firstOrNull() }.orEmpty()
                    val archiveDebug = archiveInfo?.let { isArchiveDebugBuild(it) } ?: false
                    _state.value = InAppUpdateDownloadState.SignatureMismatch(
                        updateInfo = activeInfo,
                        apkFile = cachedApk,
                        installedFingerprint = installed.sha256ColonHex,
                        archiveFingerprint = AppSigningFormatter.formatColonHex(archiveHex),
                        installedIsDebug = installed.isDebugBuild,
                        archiveIsDebug = archiveDebug
                    )
                } else {
                    _state.value = InAppUpdateDownloadState.Error(
                        updateInfo = activeInfo,
                        message = "Firma digitale diversa dall'app installata. Fai un backup da Impostazioni, disinstalla questa versione e installa l'APK dalla pagina GitHub.",
                        cachedApkFile = null
                    )
                }
            }

            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                _state.value = InAppUpdateDownloadState.Error(
                    updateInfo = activeInfo,
                    message = "Spazio di archiviazione insufficiente per installare l'aggiornamento.",
                    cachedApkFile = cachedApk
                )
            }

            else -> {
                val detail = statusMessage?.takeIf { it.isNotBlank() }
                    ?: "Installazione non riuscita (codice $status)."
                _state.value = InAppUpdateDownloadState.Error(
                    updateInfo = activeInfo,
                    message = detail,
                    cachedApkFile = cachedApk
                )
            }
        }
    }

    suspend fun exportApkToUri(apkFile: File, destUri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (!apkFile.exists()) return@withContext false
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                apkFile.inputStream().use { input ->
                    input.copyTo(out)
                }
                out.flush()
            } ?: return@withContext false
            true
        }.getOrDefault(false)
    }

    fun cancelAndClear(tagName: String? = null) {
        downloadJob?.cancel()
        downloadJob = null
        activeDownloadTag = null
        _state.value = InAppUpdateDownloadState.Idle
        scope.launch {
            if (tagName != null) {
                apkFileForTag(tagName).delete()
                File(updatesDir, "${sanitizeTag(tagName)}.apk.tmp").delete()
            } else {
                updatesDir.listFiles()?.forEach { it.delete() }
            }
        }
    }

    private fun isCachedSizeValid(file: File, updateInfo: AppUpdateInfo): Boolean {
        val expectedSize = updateInfo.apkSizeBytes
        return expectedSize == null || expectedSize <= 0L || file.length() == expectedSize
    }

    internal fun validateDownloadedApkDetailed(apkFile: File): ApkValidationResult {
        if (!apkFile.exists() || apkFile.length() < 1024L) {
            return ApkValidationResult.Invalid("File APK danneggiato o vuoto.")
        }
        val pm = context.packageManager
        val archiveInfo = getPackageArchiveInfoCompat(pm, apkFile.absolutePath)
            ?: return ApkValidationResult.Invalid("Il pacchetto scaricato non è un APK Android valido.")

        if (!AppSigningFormatter.isPackageCompatible(context.packageName, archiveInfo.packageName)) {
            return ApkValidationResult.Invalid(
                "Il pacchetto appartiene a un'altra applicazione (${archiveInfo.packageName})."
            )
        }

        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.versionCode.toLong()
        }
        if (!AppSigningFormatter.isValidVersionUpgrade(BuildConfig.VERSION_CODE.toLong(), archiveVersionCode)) {
            return ApkValidationResult.Invalid(
                "La versione del pacchetto ($archiveVersionCode) è precedente a quella installata."
            )
        }

        val installedPackage = getInstalledPackageInfo(pm, context.packageName)
        val installedHashes = installedPackage?.let { extractActiveSigningHashes(it) }.orEmpty()
        val archiveHashes = extractAllSigningHashes(archiveInfo)

        if (archiveHashes.isEmpty()) {
            return ApkValidationResult.Invalid(
                "Il pacchetto APK scaricato non contiene una firma digitale valida."
            )
        }

        if (!AppSigningFormatter.areSigningHashesCompatible(installedHashes, archiveHashes)) {
            val installedDetails = getInstalledSigningDetails()
            val archiveHex = archiveHashes.firstOrNull().orEmpty()
            return ApkValidationResult.SignatureMismatch(
                installedFingerprint = installedDetails.sha256ColonHex,
                archiveFingerprint = AppSigningFormatter.formatColonHex(archiveHex),
                installedIsDebug = installedDetails.isDebugBuild,
                archiveIsDebug = isArchiveDebugBuild(archiveInfo)
            )
        }

        return ApkValidationResult.Valid
    }

    internal fun validateDownloadedApk(apkFile: File): String? {
        return when (val result = validateDownloadedApkDetailed(apkFile)) {
            ApkValidationResult.Valid -> null
            is ApkValidationResult.SignatureMismatch ->
                "Firma digitale diversa dall'app installata: impossibile aggiornare senza reinstallare."
            is ApkValidationResult.Invalid -> result.message
        }
    }

    private fun ApkValidationResult.SignatureMismatch.toDownloadState(
        updateInfo: AppUpdateInfo,
        apkFile: File
    ) = InAppUpdateDownloadState.SignatureMismatch(
        updateInfo = updateInfo,
        apkFile = apkFile,
        installedFingerprint = installedFingerprint,
        archiveFingerprint = archiveFingerprint,
        installedIsDebug = installedIsDebug,
        archiveIsDebug = archiveIsDebug
    )

    @Suppress("DEPRECATION")
    private fun getPackageArchiveInfoCompat(pm: PackageManager, path: String): PackageInfo? {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(path, flags)
        } else {
            pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledPackageInfo(pm: PackageManager, pkg: String): PackageInfo? {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(pkg, flags)
            } else {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun extractActiveSigningHashes(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { signingInfo ->
                signingInfo.apkContentsSigners?.takeIf { it.isNotEmpty() }
                    ?: signingInfo.signingCertificateHistory
            } ?: info.signatures
        } else {
            info.signatures
        }
        return sigs.orEmpty().map { AppSigningFormatter.sha256Hex(it.toByteArray()) }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun extractAllSigningHashes(info: PackageInfo): Set<String> {
        val collected = mutableListOf<Signature>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { signingInfo ->
                signingInfo.apkContentsSigners?.let { collected.addAll(it) }
                signingInfo.signingCertificateHistory?.let { collected.addAll(it) }
            }
        }
        info.signatures?.let { collected.addAll(it) }
        return collected.map { AppSigningFormatter.sha256Hex(it.toByteArray()) }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun hasAndroidDebugCertificate(info: PackageInfo): Boolean {
        val sigs = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners?.let { addAll(it) }
            }
            info.signatures?.let { addAll(it) }
        }
        if (sigs.isEmpty()) return false
        val factory = runCatching { CertificateFactory.getInstance("X.509") }.getOrNull() ?: return false
        return sigs.any { sig ->
            runCatching {
                val cert = factory.generateCertificate(ByteArrayInputStream(sig.toByteArray())) as? X509Certificate
                cert?.subjectX500Principal?.name?.contains("CN=Android Debug", ignoreCase = true) == true
            }.getOrDefault(false)
        }
    }

    private fun isArchiveDebugBuild(archiveInfo: PackageInfo): Boolean {
        val debuggable = ((archiveInfo.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return debuggable || hasAndroidDebugCertificate(archiveInfo)
    }

    private fun apkFileForTag(tagName: String): File =
        File(updatesDir, "${sanitizeTag(tagName)}.apk")

    private fun sanitizeTag(tagName: String): String =
        tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun cleanupOtherUpdates(keepTag: String) {
        val keepPrefix = sanitizeTag(keepTag)
        updatesDir.listFiles()?.forEach { file ->
            if (!file.name.startsWith(keepPrefix)) {
                file.delete()
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_COMMIT = "com.example.action.APP_UPDATE_INSTALL_COMMIT"
        const val EXTRA_UPDATE_TAG = "extra_update_tag"
    }
}
