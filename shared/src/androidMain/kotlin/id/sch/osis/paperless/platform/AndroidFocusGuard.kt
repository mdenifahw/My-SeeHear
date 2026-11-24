package id.sch.osis.paperless.platform

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.WindowManager
import id.sch.osis.paperless.domain.DeviceLockState
import id.sch.osis.paperless.domain.ViolationKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementasi Focus Lock System pada Android menggunakan:
 * 1. Lock Task Mode / Screen Pinning (`startLockTask()` / `stopLockTask()`).
 * 2. Keep Screen On & larangan split-screen (di AndroidManifest `resizeableActivity="false"`).
 * 3. Deteksi keluar paksa via `onUserLeaveHint`, `onStop`, tombol Back, dan pemantauan pelepasan sematan layar.
 */
class AndroidFocusGuard(private val activity: Activity) : FocusGuard {
    private val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val _lockState = MutableStateFlow(DeviceLockState.UNLOCKED)
    override val lockState: StateFlow<DeviceLockState> = _lockState.asStateFlow()

    private val _violations = MutableSharedFlow<Pair<ViolationKind, String>>(extraBufferCapacity = 32)
    override val violations: Flow<Pair<ViolationKind, String>> = _violations.asSharedFlow()

    override val isSupported: Boolean = true

    @Volatile
    private var shouldBeLocked: Boolean = false
    private var wasInLockTask: Boolean = false

    override fun engageLock() {
        shouldBeLocked = true
        activity.runOnUiThread {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            runCatching {
                if (!isSystemLockTaskActive()) {
                    activity.startLockTask()
                }
            }
            wasInLockTask = isSystemLockTaskActive()
            _lockState.value = DeviceLockState.LOCKED
        }
    }

    override fun releaseLock(temporary: Boolean) {
        shouldBeLocked = false
        wasInLockTask = false
        activity.runOnUiThread {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            runCatching {
                if (isSystemLockTaskActive()) {
                    activity.stopLockTask()
                }
            }
            _lockState.value = if (temporary) DeviceLockState.TEMP_UNLOCKED else DeviceLockState.UNLOCKED
        }
    }

    fun onBackAttempt(): Boolean {
        if (!shouldBeLocked) return false
        _violations.tryEmit(ViolationKind.BACK_PRESSED to "Tombol kembali diblokir selama Mode Fokus")
        return true
    }

    fun onUserLeaveHint() {
        if (!shouldBeLocked) return
        _violations.tryEmit(ViolationKind.HOME_OR_RECENTS to "Peserta menekan Home / Recent Apps")
    }

    fun onActivityStopped() {
        if (!shouldBeLocked) return
        _violations.tryEmit(ViolationKind.APP_BACKGROUNDED to "Aplikasi berpindah ke latar belakang")
    }

    fun onActivityResumed() {
        if (!shouldBeLocked) return
        val active = isSystemLockTaskActive()
        if (wasInLockTask && !active) {
            _violations.tryEmit(ViolationKind.PIN_REMOVED to "Sematan layar dilepas oleh peserta")
        }
        if (!active) {
            runCatching { activity.startLockTask() }
        }
        wasInLockTask = isSystemLockTaskActive()
        _lockState.value = DeviceLockState.LOCKED
    }

    private fun isSystemLockTaskActive(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            @Suppress("DEPRECATION")
            activityManager.isInLockTaskMode
        }
}
