package com.dumuzeyn.mp3player;

public class MainActivity extends MainActivityCore {
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        activityCoordinator.onRequestPermissionsResult(requestCode);
    }
}
