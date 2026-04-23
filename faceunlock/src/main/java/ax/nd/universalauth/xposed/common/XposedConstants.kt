package ax.nd.universalauth.xposed.common

object XposedConstants {
    const val ACTION_UNLOCK_DEVICE = "ax.nd.universalauth.unlock-device"
    const val EXTRA_UNLOCK_MODE = "ax.nd.universalauth.unlock-device.unlock-mode"
    const val EXTRA_BYPASS_KEYGUARD = "ax.nd.universalauth.unlock-device.bypass-keyguard"
    const val ACTION_EARLY_UNLOCK = "ax.nd.universalauth.early-unlock"
    const val EXTRA_EARLY_UNLOCK_MODE = "ax.nd.universalauth.early-unlock.mode"
    const val PERMISSION_UNLOCK_DEVICE = "ax.nd.universalauth.permission.UNLOCK_DEVICE"
    const val ACTION_FACE_UNLOCK_RESULT = "ax.nd.universalauth.action.FACE_UNLOCK_RESULT"
    const val EXTRA_SUCCESS = "success"
    const val EXTRA_FACE_ID = "face_id"
    const val EXTRA_FACE_NAME = "face_name"
    const val MODE_NONE = 0
    const val MODE_WAKE_AND_UNLOCK = 1
    const val MODE_WAKE_AND_UNLOCK_PULSING = 2
    const val MODE_UNLOCK_COLLAPSING = 5
    const val MODE_UNLOCK_FADING = 7
    const val MODE_DISMISS_BOUNCER = 8
}
