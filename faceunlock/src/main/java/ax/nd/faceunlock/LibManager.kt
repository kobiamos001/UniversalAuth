package ax.nd.faceunlock

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class RequiredLib(val name: String, val armeabi_v7a: String? = null, val arm64_v8a: String? = null) {
    fun hashForCurrentAbi(): String? = arm64_v8a
}

data class LibraryState(val library: RequiredLib, val valid: Boolean)

object LibManager {
    const val LIB_DIR = "facelibs"
    const val HASH_TYPE = "SHA-512"
    private val TAG = LibManager::class.simpleName

    val requiredLibraries = listOf(
        RequiredLib("libmegface.so"),
        RequiredLib("libFaceDetectCA.so"),
        RequiredLib("libMegviiUnlock.so"),
        RequiredLib("libMegviiUnlock-jni-1.2.so")
    )

    val librariesData = MutableStateFlow<List<LibraryState>>(emptyList())
    val libsLoaded = AtomicBoolean(false)
    val libLoadError = MutableStateFlow<Throwable?>(null)

    fun init(context: Context) { updateLibraryData(context) }

    fun updateLibraryData(context: Context) {
        val newStatus = requiredLibraries.map { LibraryState(it, true) }
        librariesData.value = newStatus
        if (!libsLoaded.get()) {
            try {
                System.loadLibrary("megface")
                System.loadLibrary("FaceDetectCA")
                System.loadLibrary("MegviiUnlock")
                System.loadLibrary("MegviiUnlock-jni-1.2")
                libsLoaded.set(true)
            } catch (t: Throwable) { libLoadError.value = t }
        }
    }

    fun getLibFile(context: Context, lib: RequiredLib, temp: Boolean = false): File {
        return File(context.filesDir, lib.name)
    }
}
