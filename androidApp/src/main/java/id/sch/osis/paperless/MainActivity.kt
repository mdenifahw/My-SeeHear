package id.sch.osis.paperless

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import id.sch.osis.paperless.app.AppController
import id.sch.osis.paperless.platform.AndroidDocumentBridge
import id.sch.osis.paperless.platform.AndroidFocusGuard
import id.sch.osis.paperless.platform.AndroidNotificationFeed
import id.sch.osis.paperless.platform.PlatformServices
import id.sch.osis.paperless.ui.OsisPaperlessApp

class MainActivity : ComponentActivity() {
    private lateinit var focusGuard: AndroidFocusGuard
    private lateinit var notificationFeed: AndroidNotificationFeed
    private lateinit var documentBridge: AndroidDocumentBridge
    private lateinit var controller: AppController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        focusGuard = AndroidFocusGuard(this)
        notificationFeed = AndroidNotificationFeed(this)
        documentBridge = AndroidDocumentBridge(this)

        controller = AppController(
            scope = lifecycleScope,
            platform = PlatformServices(
                focusGuard = focusGuard,
                notificationFeed = notificationFeed,
                documentBridge = documentBridge,
            ),
        )

        // Blokir tombol Back selama Mode Fokus aktif dan catat ke Audit Log.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!focusGuard.onBackAttempt()) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            },
        )

        setContent {
            OsisPaperlessApp(controller)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        focusGuard.onUserLeaveHint()
    }

    override fun onStop() {
        super.onStop()
        focusGuard.onActivityStopped()
    }

    override fun onResume() {
        super.onResume()
        notificationFeed.refreshPermission()
        focusGuard.onActivityResumed()
    }
}
