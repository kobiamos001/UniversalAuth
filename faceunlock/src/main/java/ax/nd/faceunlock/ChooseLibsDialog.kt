package ax.nd.faceunlock

import android.app.ProgressDialog
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.color
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import ax.nd.faceunlock.util.dpToPx
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.customview.customView
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ChooseLibsDialog(private val activity: MainActivity, private val viewModel: ChooseLibsViewModel) {
    fun open() {
        val linearLayout = LinearLayout(activity)
        linearLayout.orientation = LinearLayout.VERTICAL

        val dialog = MaterialDialog(activity)

        activity.lifecycleScope.launch {
            LibManager.librariesData
                .combine(viewModel.checkingStatus) { a, b -> a to b }
                .flowWithLifecycle(activity.lifecycle).collect { (libData, checking) ->
                    linearLayout.removeAllViews()
                    libData.map { lib ->
                        createAndAttachChooserView(
                            lib.library,
                            lib.valid,
                            !checking,
                            linearLayout
                        )
                    }

                    val allLibsValid = libData.all { it.valid }
                    dialog.setActionButtonEnabled(WhichButton.POSITIVE, !checking && allLibsValid)
            }
        }

        activity.lifecycleScope.launch {
            var progressDialog: ProgressDialog? = null
            viewModel.checkingStatus.flowWithLifecycle(activity.lifecycle).collect { checking ->
                if(checking) {
                    if (progressDialog == null) {
                        progressDialog = ProgressDialog.show(
                            activity,
                            activity.getString(R.string.processing),
                            activity.getString(R.string.validating_file),
                            true,
                            false
                        )
                    }
                } else {
                    progressDialog?.cancel()
                    progressDialog = null
                }
            }
        }

        activity.lifecycleScope.launch {
            var curErrorDialog: MaterialDialog? = null
            viewModel.checkResult.flowWithLifecycle(activity.lifecycle).collect { result ->
                curErrorDialog?.cancel()
                curErrorDialog = if (result != null) {
                    openDialogForResult(result)
                } else null
            }
        }

        dialog.show {
            title(res = R.string.setup_libraries)
            customView(view = linearLayout, scrollable = true, noVerticalPadding = true)
            cancelable(false)
            cancelOnTouchOutside(false)
            positiveButton(res = R.string.continue_text) {
                activity.checkAndAskForPermissions()
            }
        }
    }

    private fun createAndAttachChooserView(lib: RequiredLib, valid: Boolean, enabled: Boolean, parent: ViewGroup) {
        val row = LinearLayout(activity)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val horizPadding = 24.dpToPx.toInt()
        val vertPadding = 8.dpToPx.toInt()
        row.setPadding(horizPadding, vertPadding, horizPadding, vertPadding)

        val textView = TextView(activity)
        val statusPrefix = activity.getString(R.string.status_label)
        textView.text = SpannableStringBuilder("${lib.name}\n$statusPrefix").apply {
            if(valid) {
                color(Color.GREEN) {
                    append(activity.getString(R.string.status_found))
                }
            } else {
                color(Color.RED) {
                    append(activity.getString(R.string.status_missing))
                }
            }
        }
        textView.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            weight = 1f
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(textView)

        val button = Button(activity)
        button.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        button.isEnabled = enabled
        button.text = activity.getString(R.string.browse)
        button.setOnClickListener {
            // activity.browseForFiles(lib)
        }
        row.addView(button)

        parent.addView(row)
    }

    fun openDialogForResult(result: CheckResult): MaterialDialog = MaterialDialog(activity).show {
        when(result) {
            is CheckResult.BadHash -> {
                title(res = R.string.invalid_file)
                message(text = activity.getString(R.string.bad_hash_message, result.library.name))
                positiveButton(android.R.string.cancel) {
                    viewModel.clearCheckResult()
                }
                negativeButton(res = R.string.continue_anyway) {
                    viewModel.saveBadHashLib(context)
                }
            }
            is CheckResult.FileError -> {
                title(res = R.string.error_title)
                message(text = activity.getString(R.string.load_lib_error, result.error.localizedMessage))
                positiveButton(android.R.string.ok) {
                    viewModel.clearCheckResult()
                }
            }
        }

        cancelable(false)
        cancelOnTouchOutside(false)
        noAutoDismiss()
    }
}
