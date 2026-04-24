package ax.nd.faceunlock

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.codec.binary.Hex
import java.io.*
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.zip.ZipInputStream

sealed interface CheckResult {
    val library: RequiredLib
    data class BadHash(override val library: RequiredLib, val tmpFile: File) : CheckResult
    data class FileError(override val library: RequiredLib, val error: Exception) : CheckResult
}

sealed interface DownloadStatus {
    data class Downloading(val importing: Boolean) : DownloadStatus
    object AskImport : DownloadStatus
    data class DownloadError(val importing: Boolean, val error: Exception?) : DownloadStatus
}

class ChooseLibsViewModel : ViewModel() {
    val checkingStatus = MutableStateFlow(false)
    val checkResult = MutableStateFlow<CheckResult?>(null)
    val downloadStatus = MutableStateFlow<DownloadStatus?>(null)
    private val okhttp = OkHttpClient()

    // קיצור דרך לקבלת סטרינג מהאפליקציה
    private fun getString(resId: Int): String = FaceApplication.getApp().getString(resId)

    fun downloadLibs(context: Context, uri: Uri?) {
        if(downloadStatus.value !is DownloadStatus.Downloading) {
            downloadStatus.value = DownloadStatus.Downloading(uri != null)
            viewModelScope.launch {
                try {
                    downloadLibsInternal(context, uri)
                    downloadStatus.value = DownloadStatus.DownloadError(uri != null, null)
                } catch (e: Exception) {
                    downloadStatus.value = DownloadStatus.DownloadError(uri != null, e)
                }
            }
        }
    }

    private fun downloadApk(): InputStream {
        val url = "$IPFS_GATEWAY/ipfs/$LIBS_CID"
        val req = Request.Builder().url(url).build()
        val body = okhttp.newCall(req).execute().body ?: run {
            throw IOException(getString(R.string.error_null_body))
        }
        return body.byteStream()
    }

    private fun openImportUri(context: Context, uri: Uri): InputStream {
        return context.contentResolver.openInputStream(uri) ?: run {
            throw NullPointerException(getString(R.string.error_provider_crashed))
        }
    }

    private suspend fun downloadLibsInternal(context: Context, uri: Uri?) {
        withContext(Dispatchers.IO) {
            val inputStream = if(uri != null) openImportUri(context, uri) else downloadApk()
            inputStream.buffered().use { resp ->
                val zin = ZipInputStream(resp)
                while (true) {
                    val entry = zin.nextEntry ?: break
                    val name = entry.name
                    if (!name.startsWith("lib/arm64-v8a/")) continue
                    
                    val fname = name.substringAfterLast('/')
                    val lib = LibManager.requiredLibraries.find { it.name == fname } ?: continue

                    val digest = MessageDigest.getInstance(LibManager.HASH_TYPE)
                    val outFile = LibManager.getLibFile(context, lib, temp = true)
                    outFile.parentFile?.mkdirs()
                    DigestOutputStream(outFile.outputStream().buffered(), digest).use { ostream ->
                        zin.copyTo(ostream)
                    }

                    val hex = Hex.encodeHexString(digest.digest(), true)
                    val targetHash = lib.hashForCurrentAbi() ?: run {
                        throw UnsupportedOperationException(getString(R.string.error_unsupported_abi))
                    }

                    if(hex == targetHash) {
                        val realFile = LibManager.getLibFile(context, lib)
                        realFile.parentFile?.mkdirs()
                        if(!outFile.renameTo(realFile)) {
                            throw IOException(getString(R.string.error_rename_failed))
                        }
                        LibManager.updateLibraryData(context)
                    } else {
                        throw IOException(getString(R.string.error_hash_mismatch))
                    }
                }
            }
        }
    }

    fun addLib(context: Context, library: RequiredLib, uri: Uri) {
        if (!checkingStatus.value && checkResult.value == null) {
            checkingStatus.value = true
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    addLibInternal(context, library, uri)
                } finally {
                    checkingStatus.value = false
                }
            }
        }
    }

    private fun addLibInternal(context: Context, library: RequiredLib, uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: throw NullPointerException(getString(R.string.error_provider_crashed))
            inputStream.use { stream ->
                val targetFile = LibManager.getLibFile(context, library, temp = true)
                targetFile.parentFile?.mkdirs()

                val digest = MessageDigest.getInstance(LibManager.HASH_TYPE)
                val wrappedInputStream = DigestInputStream(stream, digest)
                targetFile.outputStream().use { wrappedInputStream.copyTo(it) }

                val hex = Hex.encodeHexString(digest.digest(), true)
                val targetHash = library.hashForCurrentAbi() ?: throw UnsupportedOperationException(getString(R.string.error_unsupported_abi))

                if(hex == targetHash) {
                    if(!targetFile.renameTo(LibManager.getLibFile(context, library))) {
                        throw IOException(getString(R.string.error_rename_failed))
                    }
                } else {
                    checkResult.value = CheckResult.BadHash(library, tmpFile = targetFile)
                }
            }
        } catch (e: Exception) {
            checkResult.value = CheckResult.FileError(library, e)
            Log.e(TAG, "Error in addLibInternal", e)
        }
    }

    fun saveBadHashLib(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = checkResult.value
            if (!checkingStatus.value && result is CheckResult.BadHash) {
                if(!result.tmpFile.renameTo(LibManager.getLibFile(context, result.library))) {
                    val exception = IOException(getString(R.string.error_rename_failed))
                    checkResult.value = CheckResult.FileError(result.library, exception)
                    return@launch
                }
                LibManager.updateLibraryData(context)
            }
            clearCheckResult()
        }
    }

    fun setAskImport() { if(downloadStatus.value !is DownloadStatus.Downloading) downloadStatus.value = DownloadStatus.AskImport }
    fun clearDownloadResult() { if(downloadStatus.value !is DownloadStatus.Downloading) downloadStatus.value = null }
    fun clearCheckResult() { checkResult.value = null }

    companion object {
        private val TAG = ChooseLibsViewModel::class.simpleName
        private const val IPFS_GATEWAY = "https://cloudflare-ipfs.com"
        private const val LIBS_CID = "QmQNREjjXTQBDpd69gFqEreNi1dV91eSGQByqi5nXU3rBt"
    }
}
