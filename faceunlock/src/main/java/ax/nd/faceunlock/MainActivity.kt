package ax.nd.faceunlock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import ax.nd.faceunlock.databinding.ActivityMain2Binding
import ax.nd.faceunlock.service.LockscreenFaceAuthService
import ax.nd.faceunlock.service.RemoveFaceController
import ax.nd.faceunlock.service.RemoveFaceControllerCallbacks
import ax.nd.faceunlock.util.SharedUtil
import ax.nd.universalauth.xposed.common.XposedConstants
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), RemoveFaceControllerCallbacks {
    private lateinit var binding: ActivityMain2Binding
    private var removeFaceController: RemoveFaceController? = null
    private val chooseLibsViewModel: ChooseLibsViewModel by viewModels()
    private var pickApkLauncher: ActivityResultLauncher<String>? = null
    private var requestUnlockPermsLauncher: ActivityResultLauncher<String>? = null
    private lateinit var authForDeleteLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        LibManager.init(this)

        authForDeleteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val faceId = SharedUtil(this).getIntValueByKey(AppConstants.SHARED_KEY_FACE_ID)
                if (faceId > -1) {
                    removeFace(faceId)
                }
            } else {
                Toast.makeText(this, getString(R.string.auth_failed_delete), Toast.LENGTH_SHORT).show()
            }
        }

        binding.setupBtn.setOnClickListener {
            startActivity(Intent(this, SetupFaceIntroActivity::class.java))
        }
        
        binding.authBtn.setOnClickListener {
            startActivity(Intent(this, FaceAuthActivity::class.java))
        }

        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        pickApkLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if(uri != null) chooseLibsViewModel.downloadLibs(this, uri)
        }

        requestUnlockPermsLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

        checkAndAskForPermissions()

        lifecycleScope.launch {
            LibManager.libLoadError.flowWithLifecycle(lifecycle).collect { error ->
                if(error != null) {
                    MaterialDialog(this@MainActivity).show {
                        title(res = R.string.fatal_error)
                        message(res = R.string.lib_load_jni_error)
                        positiveButton(res = R.string.ok) { finish() }
                    }
                }
            }
        }
    }

    fun checkAndAskForPermissions() {
        if(ContextCompat.checkSelfPermission(this, XposedConstants.PERMISSION_UNLOCK_DEVICE) != PackageManager.PERMISSION_GRANTED) {
            requestUnlockPermsLauncher?.launch(XposedConstants.PERMISSION_UNLOCK_DEVICE)
        }

        if(!isAccessServiceEnabled(this, LockscreenFaceAuthService::class.java)) {
            MaterialDialog(this).show {
                title(res = R.string.accessibility_required_title)
                message(res = R.string.accessibility_required_msg)
                positiveButton(res = R.string.open_settings) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateButtons()
    }

    override fun onStop() {
        super.onStop()
        releaseRemoveFaceController()
    }

    private fun isAccessServiceEnabled(context: Context, accessibilityServiceClass: Class<*>): Boolean {
        val prefString = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return prefString?.contains(accessibilityServiceClass.canonicalName) == true
    }

    private fun removeFace(faceId: Int) {
        releaseRemoveFaceController()
        removeFaceController = RemoveFaceController(this, faceId, this).apply { start() }
    }

    private fun releaseRemoveFaceController() {
        removeFaceController?.stop()
        removeFaceController = null
    }

    private fun updateButtons() {
        val faceId = SharedUtil(this).getIntValueByKey(AppConstants.SHARED_KEY_FACE_ID)
        if(faceId > -1) {
            binding.setupBtn.isEnabled = false
            binding.authBtn.isEnabled = true
            binding.removeBtn.isEnabled = true
            binding.removeBtn.setOnClickListener { 
                val intent = Intent(this, FaceAuthActivity::class.java)
                authForDeleteLauncher.launch(intent)
            }
        } else {
            binding.setupBtn.isEnabled = true
            binding.authBtn.isEnabled = false
            binding.removeBtn.isEnabled = false
        }
    }

    override fun onRemove() {
        runOnUiThread {
            Toast.makeText(this, getString(R.string.face_removed_success), Toast.LENGTH_SHORT).show()
            updateButtons()
            releaseRemoveFaceController()
        }
    }

    override fun onError(errId: Int, message: String) {
        runOnUiThread {
            Toast.makeText(this, getString(R.string.error_prefix, message), Toast.LENGTH_LONG).show()
            releaseRemoveFaceController()
        }
    }

    fun browseForFiles() {
        pickApkLauncher?.launch("*/*")
    }
}
