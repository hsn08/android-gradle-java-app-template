package com.jaredsburrows.template;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    
    private static final int BT_PERMISSION_REQ_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. ADIM: Önce Bluetooth iznini kontrol et (Android 12 ve üzeri için zorunlu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // İzin yoksa kullanıcıdan iste ve metodu burada durdur (Cevabı bekleyeceğiz)
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BT_PERMISSION_REQ_CODE);
                return; 
            }
        }

        // Eğer cihaz Android 11 ve altındaysa veya izin zaten verilmişse doğrudan Shizuku'ya geç
        proceedWithShizuku();
    }

    // Bluetooth izin isteğine kullanıcının verdiği cevabı yakalayan metod
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == BT_PERMISSION_REQ_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Kullanıcı Bluetooth iznini verdi, artık Shizuku'yu kontrol edebiliriz
                proceedWithShizuku();
            } else {
                Toast.makeText(this, "Bluetooth izni verilmeden PC ile iletişim kurulamaz!", Toast.LENGTH_LONG).show();
            }
        }
    }

    // 2. ADIM: Shizuku izinlerini kontrol eden asıl mantık
    private void proceedWithShizuku() {
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

    // 3. ADIM: Her şey tamamsa dinlemeye başla
    private void startBluetoothServer() {
        new BluetoothControlThread().start();
        Toast.makeText(this, "PC Bekleniyor...", Toast.LENGTH_SHORT).show();
    }
}