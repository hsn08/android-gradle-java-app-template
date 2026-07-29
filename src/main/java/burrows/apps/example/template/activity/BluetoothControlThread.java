package com.jaredsburrows.template;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import rikka.shizuku.Shizuku;

public class BluetoothControlThread extends Thread {
    private static final String TAG = "BTControl";
    // C kodundaki SerialPortServiceClass_UUID karşılığı
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // C Kodundaki Sinyaller
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
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return;

        BluetoothServerSocket mmServerSocket = null;
        try {
            mmServerSocket = adapter.listenUsingRfcommWithServiceRecord("BTControlPlane", MY_UUID);
        } catch (Exception e) {
            Log.e(TAG, "Soket açılamadı", e);
            return;
        }

        while (true) {
            try {
                BluetoothSocket socket = mmServerSocket.accept();
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                byte[] cmdBuffer = new byte[1];
                while (in.read(cmdBuffer) > 0) {
                    byte command = cmdBuffer[0];
                    boolean success = false;

                    switch (command) {
                        case SGNL_ENABBTH_BYTE:
                            success = executeShizukuCommand("cmd bluetooth_manager enable");
                            out.write(success ? 1 : 0);
                            break;

                        case SGNL_DISABBTH_BYTE:
                            success = executeShizukuCommand("cmd bluetooth_manager disable");
                            out.write(success ? 1 : 0);
                            break;
                            
                        case SGNL_CLN_BYTE:
                            socket.close();
                            break;

                        // MAC adresi gerektiren komutlar (6 Byte daha okumamız lazım)
                        case SGNL_CHKDVCCON_BYTE:
                        case SGNL_CONDVC_BYTE:
                        case SGNL_DISCONDVC_BYTE:
                            byte[] macBuffer = new byte[6];
                            int bytesRead = 0;
                            // C'den gelen 6 bytelık MAC adresini buffer'a çekiyoruz
                            while (bytesRead < 6) {
                                int read = in.read(macBuffer, bytesRead, 6 - bytesRead);
                                if (read == -1) break;
                                bytesRead += read;
                            }
                            // TODO: İleride spesifik cihaza bağlanma/koparma mantığı buraya gelecek
                            // Şimdilik C kodu takılmasın diye başarısız (0) dönüyoruz
                            out.write(0); 
                            break;

                        case SGNL_CHKBTHSTA_BYTE:
                            // TODO: Bluetooth'un güncel durumunu sorgulama eklenecek
                            out.write(0);
                            break;
                    }
                    
                    if(command == SGNL_CLN_BYTE) break; // Temizle ve soketten çık
                }
            } catch (Exception e) {
                Log.e(TAG, "Bağlantı hatası veya PC soketi kapattı", e);
            }
        }
    }

    private boolean executeShizukuCommand(String command) {
        try {
            Process process = Shizuku.newProcess(command.split(" "), null, (java.io.File) null);
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            Log.e(TAG, "Shizuku komutu çalıştıramadı", e);
            return false;
        }
    }
}