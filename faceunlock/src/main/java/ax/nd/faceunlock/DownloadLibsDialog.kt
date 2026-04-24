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
                    pair.second.any { !it.valid }
                }
                .flowWithLifecycle(activity.lifecycle)
                .collect { (status, libs) ->
                    dialog?.cancel()
                    dialog = null

                    when {
                        libs.all { it.valid } -> {
                            activity.checkAndAskForPermissions()
                        }
                        status == null -> {
                            dialog = MaterialDialog(activity).show {
                                title(res = R.string.download_required)
                                message(res = R.string.download_message)
                                positiveButton(android.R.string.ok) {
                                    viewModel.downloadLibs(activity, null)
                                }
                                negativeButton(res = R.string.manual_import) {
                                    viewModel.setAskImport()
                                }
                                cancelOnTouchOutside(false)
                                cancelable(false)
                                noAutoDismiss()
                            }
                        }
                        status is DownloadStatus.AskImport -> {
                            dialog = MaterialDialog(activity).show {
                                title(res = R.string.manual_import)
                                message(res = R.string.manual_import_instructions)
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
                            dialog = ProgressDialog.show(
                                activity,
                                activity.getString(R.string.processing),
                                if(status.importing) activity.getString(R.string.importing_apk) else activity.getString(R.string.downloading_files),
                                true,
                                false
                            )
                        }
                        status is DownloadStatus.DownloadError -> {
                            dialog = MaterialDialog(activity).show {
                                title(res = R.string.error_title)
                                if(status.importing) {
                                    message(res = R.string.import_error_message)
                                    positiveButton(android.R.string.ok) {
                                        viewModel.setAskImport()
                                    }
                                } else {
                                    val errorMsg = status.error?.localizedMessage ?: activity.getString(R.string.unknown_error)
                                    message(text = activity.getString(R.string.download_error_message, errorMsg))
                                    positiveButton(res = R.string.retry) {
                                        viewModel.downloadLibs(activity, null)
                                    }
                                    negativeButton(res = R.string.manual_import) {
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
