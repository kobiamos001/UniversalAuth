package ax.nd.faceunlock

import android.app.Dialog
import android.app.ProgressDialog
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

class DownloadLibsDialog(private val activity: MainActivity, private val viewModel: ChooseLibsViewModel) {
    fun open() {
        var dialog: Dialog? = null

        activity.lifecycleScope.launch {
            viewModel.downloadStatus
                .combine(LibManager.librariesData) { a, b -> a to b }
                .transformWhile { pair ->
                    emit(pair)
                    pair.second.any { !it.valid } // Terminate flow once all libs are valid
                }
                .flowWithLifecycle(activity.lifecycle)
                .collect { (status, libs) ->
                    dialog?.cancel()
                    dialog = null

                    when {
                        libs.all { it.valid } -> {
                            // All libs valid, continue to check perms
                            activity.checkAndAskForPermissions()
                        }
                        status == null -> {
                            // Ask download
                            dialog = MaterialDialog(activity).show {
                                title(text = activity.getString(R.string.dialog_download_required_title))
                                message(text = activity.getString(R.string.dialog_download_required_message))
                                positiveButton(android.R.string.ok) {
                                    viewModel.downloadLibs(activity, null)
                                }
                                negativeButton(text = activity.getString(R.string.btn_manual_import)) {
                                    viewModel.setAskImport()
                                }
                                cancelOnTouchOutside(false)
                                cancelable(false)
                                noAutoDismiss()
                            }
                        }
                        status is DownloadStatus.AskImport -> {
                            // Ask user to import libraries manually
                            dialog = MaterialDialog(activity).show {
                                title(text = activity.getString(R.string.dialog_manual_import_title))
                                message(text = activity.getString(R.string.dialog_manual_import_message))
                                positiveButton(android.R.string.ok) {
                                    activity.browseForFiles()
                                }
                                negativeButton(android.R.string.cancel) {
                                    viewModel.clearDownloadResult()
                                }
                                cancelOnTouchOutside(false)
                                cancelable(false)
                                noAutoDismiss()
                            }
                        }
                        status is DownloadStatus.Downloading -> {
                            // Downloading
                            dialog = ProgressDialog.show(
                                activity,
                                activity.getString(R.string.dialog_processing_title),
                                if(status.importing) activity.getString(R.string.dialog_importing_apk) else activity.getString(R.string.dialog_downloading_files),
                                true,
                                false
                            )
                        }
                        status is DownloadStatus.DownloadError -> {
                            // Download failed
                            dialog = MaterialDialog(activity).show {
                                title(text = activity.getString(R.string.dialog_error_title))
                                if(status.importing) {
                                    message(text = activity.getString(R.string.dialog_import_apk_error))
                                    positiveButton(android.R.string.ok) {
                                        viewModel.setAskImport()
                                    }
                                } else {
                                    val errorMessage = status.error?.message ?: activity.getString(R.string.error_unknown)
                                    message(text = activity.getString(R.string.dialog_download_error, errorMessage))
                                    positiveButton(text = activity.getString(R.string.btn_retry)) {
                                        viewModel.downloadLibs(activity, null)
                                    }
                                    negativeButton(text = activity.getString(R.string.btn_manual_import)) {
                                        viewModel.setAskImport()
                                    }
                                }
                                cancelOnTouchOutside(false)
                                cancelable(false)
                                noAutoDismiss()
                            }
                        }
                    }
            }
        }
    }
}
