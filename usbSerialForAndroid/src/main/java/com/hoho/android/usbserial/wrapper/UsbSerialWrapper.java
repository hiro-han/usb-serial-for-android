package com.hoho.android.usbserial.wrapper;

import com.unity3d.player.UnityPlayer;

import android.event.Listner;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDeviceConnection;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager.Listener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class UsbSerialWrapper {
    private static UsbSerialPort port_ = null;
    private static boolean connected_ = false;
    private static boolean usb_permission_ = false;
    private static String error_msg_ = "";
    private static Context context_;
    private static Activity activity_;
    private static Intent intent_;
    private static UsbManager usb_manager_;
    private static UsbSerialDriver usb_driver_;
    private static BroadcastReceiver usb_reciver_;
    private static Listener data_istener_;
    private static String ACTION_USB_PERMISSION = "com.hoho.android.usbserial.USB_PERMISSION";
    private static int baudrate_ = 115200;
    private static int length_ = 0;
    private static String data_ = "";

    public static void Initialize(Context context, Activity activity, Intent intent)
    {
        context_ = context;
        activity_ = activity;
        intent_ = intent;

        usb_reciver_ = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                    if (intent_.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        usb_permission_ = true;
                        Connect();
                    } else {
                        usb_permission_ = false;
                    }
                }
            };
        };
    }

    public static boolean OpenDevice(int baudrate)
    {
        baudrate_ = baudrate;
        usb_manager_ = (UsbManager) context_.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usb_manager_);
        if (availableDrivers.isEmpty()) {
            error_msg_ = "Device is empty";
            return false;
        }

        // Open a connection to the first available driver.
        usb_driver_ = availableDrivers.get(0);
        if (usb_manager_.hasPermission(usb_driver_.getDevice())) {
            usb_permission_ = true;
        } else {
            // Raspi Pico
            //
            // usb_driver_.getDevice().getManufacturerName() : RaspberryPi
            // usb_driver_.getDevice().getProductName() : PiPico
            // usb_driver_.getDevice().getVendorId() : 11914
            // usb_driver_.getDevice().getProductId() : 10

            error_msg_ = "Doesn't have permission";
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(activity_, 0, new Intent(ACTION_USB_PERMISSION), 0);
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            context_.registerReceiver(usb_reciver_, filter);
            usb_manager_.requestPermission(usb_driver_.getDevice(), usbPermissionIntent);
        }
        return Connect();
    }

    public static boolean Connect() {
        if (!usb_permission_) {
            error_msg_ = "Doesn't have permission";
            return false;
        }

        UsbDeviceConnection connection = usb_manager_.openDevice(usb_driver_.getDevice());
        if (connection == null) {
            error_msg_ = "Connection error";
            return false;
        }

        port_ = usb_driver_.getPorts().get(0); // Most devices have just one port (port 0)
        try {
            port_.open(connection);
            port_.setParameters(baudrate_, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            port_.setDTR(true);
            connected_ = true;
        } catch (IOException e) {
            port_ = null;
            connected_ = false;
            error_msg_ = e.getMessage();
            return false;
        }
        return true;
    }

    public static String Read()
    {
        length_ = 0;
        data_ = "";
        if (!connected_) {
            return "";
        }
        try {
            byte[] buffer = new byte[8192];
            length_ = port_.read(buffer, 2000);
            data_ = new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException e) {
            length_ = 0;
            error_msg_ = e.getMessage();
            Close();
        }
        return data_;
    }

    public static void Close()
    {
        try {
            port_.close();
        } catch (IOException e) {
            port_ = null;
            error_msg_ = e.getMessage();
        }
        connected_ = false;
    }

    public static String ErrorMsg() {
        return error_msg_;
    }

    public static boolean Connected() {
        return connected_;
    }
}
