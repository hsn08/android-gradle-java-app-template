package com.jaredsburrows.template;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.util.Log;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

// LSPosed HiddenApiBypass kütüphanesi
import org.lsposed.hiddenapibypass.HiddenApiBypass;
import rikka.shizuku.Shizuku;

public class BluetoothControlThread extends Thread {
    private static final String TAG = "BTControl";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final byte SGNL_CHKDVCCON_BYTE = 0x01;
    private static final byte SGNL_CHKBTHSTA_BYTE = 0x02;
    private static final byte SGNL_ENABBTH_BYTE   = 0x03;
    private static final byte SGNL_DISABBTH_BYTE  = 0x04;
    private static final byte SGNL_CONDVC_BYTE    = 0x05;
    private static final byte SGNL_DISCONDVC_BYTE = 0x06;
    private static final byte SGNL_CLN_BYTE       = 0x07;

    @SuppressLint("MissingPermission")
    @Override
    public void run() {
        // Android 9+ ve özellikle Android 16 ART mimarisi için Hidden API engelini kaldır
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("");
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return;

        BluetoothServerSocket mmServerSocket = null;
        try {
            mmServerSocket = adapter.listenUsingRfcommWithServiceRecord("BTControlPlane", MY_UUID);
        } catch (Exception e) {
            Log.e(TAG, "Server Soket açılamadı", e);
            return;
        }

        while (true) {
            BluetoothSocket socket = null;
            try {
                socket = mmServerSocket.accept();
                DataInputStream in = new DataInputStream(socket.getInputStream());
                OutputStream out = socket.getOutputStream();

                while (true) {
                    byte command;
                    try {
                        command = in.readByte(); 
                    } catch (Exception e) {
                        Log.w(TAG, "İstemci bağlantıyı kopardı veya hata oluştu.");
                        break; // Döngüden çık, finally bloğuna git
                    }
                    
                    // SGNL_CLN_BYTE (Temizlik sinyali) switch dışına alındı. 
                    // Direkt döngüyü kırıp finally bloğunda soketi temizler.
                    if (command == SGNL_CLN_BYTE) {
                        Log.i(TAG, "Temizlik sinyali alındı, soket kapatılıyor...");
                        break; 
                    }

                    boolean success = false;

                    switch (command) {
                        case SGNL_ENABBTH_BYTE:
                            success = executeShizukuCommand("cmd bluetooth_manager enable");
                            out.write(success ? 1 : 0);
                            out.flush();
                            break;

                        case SGNL_DISABBTH_BYTE:
                            success = executeShizukuCommand("cmd bluetooth_manager disable");
                            out.write(success ? 1 : 0);
                            out.flush();
                            break;

                        case SGNL_CHKBTHSTA_BYTE:
                            boolean isEnabled = adapter.isEnabled();
                            out.write(isEnabled ? 1 : 0);
                            out.flush();
                            break;

                        case SGNL_CHKDVCCON_BYTE:
                        case SGNL_CONDVC_BYTE:
                        case SGNL_DISCONDVC_BYTE:
                            byte[] macBuffer = new byte[6];
                            in.readFully(macBuffer);

                            @SuppressLint("DefaultLocale")
                            String macAddress = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                                    macBuffer[0] & 0xFF, macBuffer[1] & 0xFF, macBuffer[2] & 0xFF,
                                    macBuffer[3] & 0xFF, macBuffer[4] & 0xFF, macBuffer[5] & 0xFF);

                            BluetoothDevice device = adapter.getRemoteDevice(macAddress);
                            boolean actionSuccess = false;

                            try {
                                if (command == SGNL_CHKDVCCON_BYTE) {
                                    Method isConnectedMethod = device.getClass().getMethod("isConnected");
                                    actionSuccess = (boolean) isConnectedMethod.invoke(device);
                                } 
                                else if (command == SGNL_CONDVC_BYTE) {
                                    Method connectMethod = device.getClass().getMethod("connect");
                                    actionSuccess = (boolean) connectMethod.invoke(device);
                                } 
                                else if (command == SGNL_DISCONDVC_BYTE) {
                                    Method disconnectMethod = device.getClass().getMethod("disconnect");
                                    actionSuccess = (boolean) disconnectMethod.invoke(device);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "MAC islem hatasi (" + macAddress + "): ", e);
                                actionSuccess = false;
                            }

                            out.write(actionSuccess ? 1 : 0);
                            out.flush();
                            break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Bağlantı kabul hatası", e);
            } finally {
                // KUSURSUZLUK BURADA: Bağlantı nasıl koparsa kopsun soket KESİN kapanır.
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Soket kapatılırken hata", e);
                    }
                }
            }
        }
    }

    private boolean executeShizukuCommand(String command) {
        try {
            // Shell üzerinden çalıştırmak komutun ortam değişkenlerini doğru almasını sağlar
            Process process = Shizuku.newProcess(new String[]{"sh", "-c", command}, null, null);
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            Log.e(TAG, "Shizuku hatası", e);
            return false;
        }
    }
}