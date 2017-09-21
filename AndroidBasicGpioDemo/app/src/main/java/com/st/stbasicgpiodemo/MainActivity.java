package com.st.stbasicgpiodemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

public class MainActivity extends Activity {

    private static Activity myActivity;
    private static final String TAG = "myActivity";

    private static final int MESSAGE_REFRESH = 101;
    private static final long REFRESH_TIMEOUT_MILLIS = 5000;

    private final static Boolean D = true;
    private static Boolean TOASTINFO = true;

    private static TextView devstatus, devname, devvid, devpid, resetstate, ubutstate;
    private static Button btLed;
    private static LinearLayout inputpanel, outputpanel;

    private static UsbManager mUsbManager;
    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    private static UsbSerialPort sPort = null;
    private static Boolean mAuthorized = false;
    private static Boolean mDeviceIsSelected = false;
    private static PendingIntent mPermissionIntent;
    private static Boolean mConnected = false;
    private final static ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private static SerialInputOutputManager mSerialIoManager;
    private static String mUSBinStr = "";
    private static Boolean mLedIsOn = false;
    private static Boolean mLedCommandOnGoing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.d(TAG, "onCreate ");
        setContentView(R.layout.activity_main);
        inputpanel = (LinearLayout) findViewById(R.id.inputpanel);
        outputpanel = (LinearLayout) findViewById(R.id.outpanel);
        devstatus = (TextView) findViewById(R.id.devstatus);
        devname = (TextView) findViewById(R.id.devname);
        devvid = (TextView) findViewById(R.id.devvid);
        devpid = (TextView) findViewById(R.id.devpid);
        resetstate = (TextView) findViewById(R.id.resetstate);
        ubutstate = (TextView) findViewById(R.id.ubutstate);
        btLed = (Button) findViewById(R.id.btLed);
        btLed.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.btLed:
                        mLedCommandOnGoing = true;
                        if (mLedIsOn) {
                            sendCortexM4Command("SL=0\n");
                        }
                        else {
                            sendCortexM4Command("SL=1\n");
                        }
                        break;
                    default:
                        break;
                }
            }
        });

        inputpanel.setVisibility(View.GONE);
        outputpanel.setVisibility(View.GONE);
        devname.setText("");
        devvid.setText("");
        devvid.setText("");
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        myActivity = this;
        if(D) Log.d(TAG, "onStart ");
    }

    @Override
    protected void onStop() {
        if(D) Log.d(TAG, "onStop ");
        super.onStop();
    }

    /*
     * called each time the application gets focused
     */
    @Override
    protected void onResume() {
        super.onResume();
        if(D) Log.d(TAG, "onResume ");
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);
        if (mDeviceIsSelected) {
            mDeviceIsSelected = false;
        } else {
            mHandler.sendEmptyMessage(MESSAGE_REFRESH);
        }
    }

    /*
     * called each time the application losts focus
     */
    @Override
    protected void onPause() {
        super.onPause();
        if(D) Log.d(TAG, "onPause ");
        if (mConnected) {
            stopIoManager();
            if (sPort != null) {
                try {
                    sPort.close();
                } catch (IOException e) {
                    // Ignore.
                }
                sPort = null;
            }
            mConnected = false;
        }
        unregisterReceiver(mUsbReceiver);
        mHandler.removeMessages(MESSAGE_REFRESH);
    }

    /*
     * method that updates the UI panels
     */
    private static void updateUI() {
        if (mConnected) {
            devstatus.setText("Connected");
            String dName = sPort.getDriver().getClass().getSimpleName();
            devname.setText(dName);
            int vid = sPort.getDriver().getDevice().getVendorId();
            devvid.setText(String.format("VID = %04X", vid));
            int pid = sPort.getDriver().getDevice().getProductId();
            devpid.setText(String.format("PID = %04X", pid));
            inputpanel.setVisibility(View.VISIBLE);
            outputpanel.setVisibility(View.VISIBLE);

        } else {
            inputpanel.setVisibility(View.GONE);
            outputpanel.setVisibility(View.GONE);
            devname.setText("");
            devvid.setText("");
            devpid.setText("");
        }
    }

    /*
     * method that tries to open a connection on STM32 usb device
     */
    private static Boolean openCDC() {
        UsbDeviceConnection connection = mUsbManager.openDevice(sPort.getDriver().getDevice());
        if (connection != null) {
            try {
                sPort.open(connection);
                sPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                onDeviceStateChange();
                mConnected = true;
                if (TOASTINFO) Toast.makeText(myActivity, "Success: Permission given on USB device", Toast.LENGTH_SHORT).show();
                updateUI();
                return true;
            } catch (IOException e) {
                Toast.makeText(myActivity, "IOException: "+e.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                try {
                    sPort.close();
                } catch (IOException e2) {
                    Toast.makeText(myActivity, "IOException2: "+e2.getMessage(), Toast.LENGTH_SHORT).show();
                    // Ignore.
                }
                sPort = null;
                mConnected = false;
                devstatus.setText("Connection failed");
                return false;
            }
        } else {
            Toast.makeText(myActivity, "Error: not the right USB device", Toast.LENGTH_SHORT).show();
            return false;
        }

    }

    /*
     * handler that checks if a STM32 usb device is attached
     */
    private static void refreshDeviceList() {
        devstatus.setText("Searching...");
        new AsyncTask<Void, Void, List<UsbSerialPort>>() {
            @Override
            protected List<UsbSerialPort> doInBackground(Void... params) {
                if(D) Log.d(TAG, "Refreshing device list ...");
                SystemClock.sleep(1000);

                final List<UsbSerialDriver> drivers =
                        UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);

                final List<UsbSerialPort> result = new ArrayList<UsbSerialPort>();
                for (final UsbSerialDriver driver : drivers) {
                    final List<UsbSerialPort> ports = driver.getPorts();
                    if(D) Log.d(TAG, String.format("+ %s: %s port%s",
                            driver, Integer.valueOf(ports.size()), ports.size() == 1 ? "" : "s"));
                    result.addAll(ports);
                }

                return result;
            }

            @Override
            protected void onPostExecute(List<UsbSerialPort> result) {
                sPort = null;
                for (final UsbSerialPort port : result) {
                    final UsbSerialDriver driv = port.getDriver();
                    final String drvName = driv.getClass().getSimpleName();
                    if (drvName.equals("STM32SerialDriver")) {
                        sPort = port;
                        mHandler.removeMessages(MESSAGE_REFRESH);
                        mDeviceIsSelected = true;
                        UsbDevice mUsbDevice =  sPort.getDriver().getDevice();
                        mAuthorized = mUsbManager.hasPermission(mUsbDevice);
                        if (!mAuthorized) {
                            if(D) Log.d(TAG, "onPostExecute, need to request USB permission thanks to an intent...");
                            mUsbManager.requestPermission(mUsbDevice, mPermissionIntent);
                            devstatus.setText("Request permission");
                        } else {
                            if (!openCDC()) {
                                mHandler.sendEmptyMessageDelayed(MESSAGE_REFRESH, REFRESH_TIMEOUT_MILLIS);
                            }
                        }
                        break;
                    } else {
                        devstatus.setText("No device found");
                    }
                }
            }

        }.execute((Void) null);
    }

    /*
     * handler that checks every 5s if a usb device is attached
     */
    private static Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_REFRESH:
                    refreshDeviceList();
                    mHandler.sendEmptyMessageDelayed(MESSAGE_REFRESH, REFRESH_TIMEOUT_MILLIS);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };

    /*
     * USB CDC listener that will receive asynchronous data from STM32 sub-system
     */
    private final static SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    myActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(myActivity, "onRunError", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onNewData(final byte[] data) {
                    myActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateReceivedData(data);
                        }
                    });
                }
            };


    /*
     * BroadcastReceiver that will be called when a permission is given (or not) to a USB CDC device
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        mAuthorized = true;
                        if(D) Log.d(TAG, "BroadcastReceiver ACTION_USB_PERMISSION GRANTED");
                        if (!openCDC()) {
                            mHandler.sendEmptyMessageDelayed(MESSAGE_REFRESH, REFRESH_TIMEOUT_MILLIS);
                        }
                    }
                    else {
                        mAuthorized = false;
                        if(D) Log.d(TAG, "BroadcastReceiver ACTION_USB_PERMISSION DENIED");
                    }
                }
            }
        }
    };

    /*
     * Method that allows to stop the IoManager over USB CDC
     * IoManager manages data exchange
     */
    private static void stopIoManager() {
        if (mSerialIoManager != null) {
            if(D) Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    /*
     * Method that allows to start the IoManager over USB CDC
     * IoManager manages data exchange
     */
    private static void startIoManager() {
        if (sPort != null) {
            if(D) Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    /*
     * Method that allows to start the IoManager
     */
    private static void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    /*
     * callback called by IoManager when data is received from Cortex M4
     * @param: data byte array received from Cortex M4
     */
    private static void updateReceivedData(byte[] data) {
        int i;
        String recStr = "";
        for (i = 0; i < data.length; i++) {
            recStr += String.format("%02X ", data[i]);
        }
        if(D) Log.d("TAG", "updateReceivedData Ln=" + data.length + ": " + recStr);
        for (i = 0; i < data.length; i++) {
            if ((data[i] != 0x0D) && (data[i] != 0x0A)) {
                mUSBinStr += (char) data[i];    // save data byte
            } else if (data[i] == 0x0A) {       // treat received event
                if (mUSBinStr.startsWith("Butt=0")) {
                    ubutstate.setText("uButton pressed");
                    ubutstate.setBackgroundColor(Color.GREEN);
                    if(D) Log.d(TAG, "updateReceivedData uButton pressed");
                } else if (mUSBinStr.startsWith("Butt=1")) {
                    ubutstate.setText("uButton released");
                    ubutstate.setBackgroundColor(Color.GRAY);
                    if(D) Log.d(TAG, "updateReceivedData uButton released");
                } else if (mUSBinStr.contains("Reset:")) {
                    if(D) Log.d(TAG, "updateReceivedData Reset detected");
                    Date dat = new Date();
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
                    String time = sdf.format(dat);
                    resetstate.setText("Last Reset pressed at " + time);
                    mLedCommandOnGoing = false;
                    mLedIsOn = false;
                    btLed.setText("LED is OFF");
                } else if (mUSBinStr.startsWith("OK")) {
                    if (mLedCommandOnGoing) {
                        mLedCommandOnGoing = false;
                        if (mLedIsOn) {
                            mLedIsOn = false;
                            btLed.setText("LED is OFF");
                        } else {
                            mLedIsOn = true;
                            btLed.setText("LED is ON");
                        }
                    }
                    if(D) Log.d(TAG, "updateReceivedData OK detected");
                }
                mUSBinStr = "";     // ready to treat new event
            }
        }
    }

    /*
 * Method used to send a command to Cortex M4
 * @param: int command, int param
 */
    private Boolean sendCortexM4Command(String command) {
        byte[] TXbuffer = command.getBytes();
        try {
            sPort.write(TXbuffer, 1000);
            if(D) Log.d(TAG, "sendCortexM4Command "+command);
            return true;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
    }


}
