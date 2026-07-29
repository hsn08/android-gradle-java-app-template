package com.jaredsburrows.template;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkShizukuPermission()) {
            startBluetoothServer();
        } else {
            requestShizukuPermission();
        }
    }

    private boolean checkShizukuPermission() {
        if (Shizuku.isPreV11()) return false;
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    }

    private void requestShizukuPermission() {
        Shizuku.addRequestPermissionResultListener(new Shizuku.OnRequestPermissionResultListener() {
            @Override
            public void onRequestPermissionResult(int requestCode, int grantResult) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    startBluetoothServer();
                } else {
                    Toast.makeText(MainActivity.this, "Shizuku izni reddedildi!", Toast.LENGTH_LONG).show();
                }
                Shizuku.removeRequestPermissionResultListener(this);
            }
        });
        Shizuku.requestPermission(0);
    }

    private void startBluetoothServer() {
        new BluetoothControlThread().start();
        Toast.makeText(this, "PC Bekleniyor...", Toast.LENGTH_SHORT).show();
    }
}