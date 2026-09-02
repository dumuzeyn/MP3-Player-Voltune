package com.dumuzeyn.mp3player

open class MainActivity : MainActivityCore() {
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        activityCoordinator.onRequestPermissionsResult(requestCode)
    }
}
