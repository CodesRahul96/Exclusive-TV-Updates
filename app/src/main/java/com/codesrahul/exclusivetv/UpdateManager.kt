package com.codesrahul.exclusivetv

import android.app.DownloadManager
import android.app.DownloadManager.Request
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.codesrahul.exclusivetv.requests.ApiClient
import com.codesrahul.exclusivetv.requests.ReleaseRequest
import com.codesrahul.exclusivetv.requests.ReleaseResponse
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class UpdateManager(
    private var context: Context,
    private var versionCode: Int
) :
    ConfirmationFragment.ConfirmationListener {

    private var releaseRequest = ReleaseRequest()
    private var release: ReleaseResponse? = null

    private var downloadReceiver: DownloadReceiver? = null

    private var checkingDialog: android.app.Dialog? = null

    fun checkAndUpdate(isManualCheck: Boolean = false) {
        CoroutineScope(Dispatchers.Main).launch {
            if (isManualCheck) {
                try {
                    if (context is FragmentActivity && 
                        !(context as FragmentActivity).isFinishing && 
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !(context as FragmentActivity).isDestroyed)) {
                        
                        checkingDialog = android.app.Dialog(context, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
                        checkingDialog?.setContentView(R.layout.dialog_checking)
                        checkingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
                        checkingDialog?.setCancelable(false)
                        checkingDialog?.show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Check if we already have the release info from TVList's early check
            val cachedRelease = SecurityUtil.remoteRelease
            if (cachedRelease != null && !isManualCheck) {
                release = cachedRelease
                if (SecurityUtil.isAppOutdated) {
                    val text = "New version available: ${release?.version_name}\n\nPlease update to continue using the app."
                    updateUI(text, update = true, force = true)
                    return@launch
                }
            }

            // Artificial delay for better UX (so dialog doesn't flash)
            val startTime = System.currentTimeMillis()
            
            var text = "Failed to obtain version"
            var update = false
            var error = false
            
            try {
                // Perform network request on IO thread to avoid strict mode violations if any
                release = withContext(Dispatchers.IO) {
                    releaseRequest.getRelease()
                }
                
                val duration = System.currentTimeMillis() - startTime
                if (isManualCheck && duration < 1000) {
                    kotlinx.coroutines.delay(1000 - duration)
                }

                if (release?.version_code != null) {
                    // Update only if remote version code is STRICTLY GREATER than current
                    if (release?.version_code!! > versionCode) {
                        text = "New version available: ${release?.version_name}"
                        update = true
                        SecurityUtil.isAppOutdated = true
                        SecurityUtil.remoteRelease = release
                    } else {
                        text = "You are using the latest version."
                    }
                } else if (release == null) {
                    text = "Could not connect to update server."
                    error = true
                }
            } catch (e: Exception) {
                text = "Connection Error"
                error = true
                if (isManualCheck) {
                    val duration = System.currentTimeMillis() - startTime
                    if (duration < 1000) kotlinx.coroutines.delay(1000 - duration)
                }
            }
            
            updateUI(text, update, update, isManualCheck) // Force update is true if update is available
        }
    }

    private fun updateUI(text: String, update: Boolean, force: Boolean = false, isManualCheck: Boolean = false) {
        try { 
            if (checkingDialog?.isShowing == true) {
                checkingDialog?.dismiss() 
            }
        } catch (e: Exception) {}

        if (context is FragmentActivity) {
            val activity = context as FragmentActivity
            if (activity.isFinishing || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed)) {
                return
            }

            if (update) {
                val changelog = release?.changelog ?: "Bug fixes and performance improvements."
                val dialog = ConfirmationFragment(this@UpdateManager, text, changelog, update, force)
                if (!activity.supportFragmentManager.isStateSaved) {
                    dialog.show(activity.supportFragmentManager, TAG)
                }
                
                // Notify listener to block usage
                if (force && context is UpdateListener) {
                    (context as UpdateListener).onForceUpdate()
                }
            } else if (isManualCheck) {
                // Use ConfirmationFragment for "Up to Date" style too
                val dialog = ConfirmationFragment(this@UpdateManager, text, "", false, false)
                if (!activity.supportFragmentManager.isStateSaved) {
                    dialog.show(activity.supportFragmentManager, TAG)
                }
            }
        }
    }

    interface UpdateListener {
        fun onForceUpdate()
    }

    private fun startDownload(release: ReleaseResponse) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                (context as FragmentActivity).startActivityForResult(intent, 123)
                // Might want to return here and ask user to retry after granting permission
            }
        }

        val apkName = "ExclusiveTV"
        val apkFileName = "$apkName-${release.version_name}.apk"
        val downloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        // Use the appropriate download host based on which source provided the version info
        val downloadHost = if (releaseRequest.usedFallback) {
            ApiClient.DOWNLOAD_HOST_FALLBACK
        } else {
            ApiClient.DOWNLOAD_HOST
        }
        
        val downloadUrl = "$downloadHost${release.version_name}/$apkName-${release.version_name}.apk"
        val request = Request(Uri.parse(downloadUrl))
        
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.mkdirs()
        request.setDestinationInExternalFilesDir(
            context,
            Environment.DIRECTORY_DOWNLOADS,
            apkFileName
        )
        request.setTitle("${context.resources.getString(R.string.app_name)} ${release.version_name}")
        request.setNotificationVisibility(Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setAllowedOverRoaming(false)
        request.setMimeType("application/vnd.android.package-archive")

        // èŽ·å–ä¸‹è½½ä»»åŠ¡çš„å¼•ç”¨
        val downloadReference = downloadManager.enqueue(request)

        downloadReceiver = DownloadReceiver(context, apkFileName, downloadReference)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            context.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }

        getDownloadProgress(context, downloadReference) { progress ->
        }
    }

    private fun getDownloadProgress(
        context: Context,
        downloadId: Long,
        progressListener: (Int) -> Unit
    ) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val handler = Handler(Looper.getMainLooper())
        val intervalMillis: Long = 1000

        handler.post(object : Runnable {
            override fun run() {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor: Cursor = downloadManager.query(query)
                cursor.use {
                    if (it.moveToFirst()) {
                        val bytesDownloadedIndex =
                            it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIndex =
                            it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                        // Check if the column name exists
                        if (bytesDownloadedIndex != -1 && bytesTotalIndex != -1) {
                            val bytesDownloaded = it.getInt(bytesDownloadedIndex)
                            val bytesTotal = it.getInt(bytesTotalIndex)

                            if (bytesTotal != -1) {
                                val progress = (bytesDownloaded * 100L / bytesTotal).toInt()
                                progressListener(progress)
                                if (progress == 100) {
                                    return
                                }
                            }
                        }
                    }
                }

//                handler.postDelayed(this, intervalMillis)
            }
        })
    }

    private class DownloadReceiver(
        private val context: Context,
        private val apkFileName: String,
        private val downloadReference: Long
    ) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val reference = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)

            if (reference == downloadReference) {
                val downloadManager =
                    context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(downloadReference)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIndex < 0) {
                        return
                    }
                    val status = cursor.getInt(statusIndex)

                    val progressIndex =
                        cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    if (progressIndex < 0) {
                        return
                    }
                    val progress = cursor.getInt(progressIndex)

                    val totalSizeIndex =
                        cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val totalSize = cursor.getInt(totalSizeIndex)

                    cursor.close()

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            installNewVersion()
                        }

                        DownloadManager.STATUS_FAILED -> {
                            // Handle download failure
                        }

                        else -> {
                            // Update UI with download progress
                            val percentage = progress * 100 / totalSize
                        }
                    }
                }
            }
        }

        private fun installNewVersion() {
            try {
                val downloadManager =
                    context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                var apkUri: Uri? = downloadManager.getUriForDownloadedFile(downloadReference)

                // Fallback to FileProvider if DownloadManager URI is null
                if (apkUri == null) {
                    val apkFile = File(
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                        apkFileName
                    )
                    if (apkFile.exists()) {
                        apkUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            apkFile
                        )
                    }
                }

                if (apkUri != null) {
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(installIntent)
                } else {
                    Toast.makeText(context, "Install failed: File not found", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Install Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val TAG = "UpdateManager"
    }

    override fun onConfirm() {
        // Option 1: Direct Download (Current)
        // release?.let { startDownload(it) }

        // Option 2: Open in Browser (Safer for Play Protect)
        release?.let { rel ->
            val downloadHost = if (releaseRequest.usedFallback) {
                ApiClient.DOWNLOAD_HOST_FALLBACK
            } else {
                ApiClient.DOWNLOAD_HOST
            }
            val downloadUrl = "$downloadHost${rel.version_name}/ExclusiveTV-${rel.version_name}.apk"
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                // If browser fails, fallback to direct download as last resort
                startDownload(rel)
            }
        }
    }

    override fun onCancel() {
    }

    fun destroy() {
        try {
            if (checkingDialog?.isShowing == true) {
                checkingDialog?.dismiss()
            }
        } catch (e: Exception) { }

        if (downloadReceiver != null) {
            try {
                context.unregisterReceiver(downloadReceiver)
            } catch (e: IllegalArgumentException) {
                // Receiver not registered or already unregistered
            }
        }
    }
}
