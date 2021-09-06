package itan.com.bluetoothle;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Handler;
import android.os.IBinder;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;


import static itan.com.bluetoothle.Constants.BODY_SENSOR_LOCATION_CHARACTERISTIC_UUID;
import static itan.com.bluetoothle.Constants.HEART_RATE_SERVICE_UUID;
import static itan.com.bluetoothle.Constants.SERVER_MSG_FIRST_STATE;
import static itan.com.bluetoothle.Constants.SERVER_MSG_SECOND_STATE;




public class DeviceConnectActivity extends BluetoothActivity implements View.OnClickListener {

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private static final String LIST_NAME = "NAME";
    private static final String LIST_UUID = "UUID";


    private CentralService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mDeviceServices;
    private BluetoothGattCharacteristic mCharacteristic;

    private String mDeviceName;
    private String mDeviceAddress;

    private TextView mConnectionStatus;
    private TextView mConnectedDeviceName;
    private ImageView mServerCharacteristic;
    private Button mRequestReadCharacteristic;
    private Button mRequestWriteCharacteristic;
    private Button btnFTpMSg;
    private Button btnGps;
    private Button btnRtsp;

    private EditText edMsg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDeviceServices = new ArrayList<>();
        mCharacteristic = null;

        Intent intent = getIntent();
        if (intent != null) {
            mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
            mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        }


        mConnectionStatus = (TextView) findViewById(R.id.connection_status);
        mConnectedDeviceName = (TextView) findViewById(R.id.connected_device_name);
        mServerCharacteristic = (ImageView) findViewById(R.id.server_characteristic_value);
        mRequestReadCharacteristic = (Button) findViewById(R.id.request_read_characteristic);
        mRequestReadCharacteristic.setOnClickListener(this);

        btnFTpMSg = (Button) findViewById(R.id.btnFtp);
        btnFTpMSg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendFtpMsg();
            }
        });

        edMsg = findViewById(R.id.edMsg);
        mRequestWriteCharacteristic = findViewById(R.id.request_write_characteristic);
        mRequestWriteCharacteristic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendWriteCharacteristic();

            }
        });

        btnGps = findViewById(R.id.btnGps);
        btnGps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendGps();
            }
        });

        btnRtsp = findViewById(R.id.btnRtsp);
        btnRtsp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendRTsp();
            }
        });
        if (TextUtils.isEmpty(mDeviceName)) {
            mConnectedDeviceName.setText("");
        } else {
            mConnectedDeviceName.setText(mDeviceName);
        }


        Intent gattServiceIntent = new Intent(this, CentralService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        /*
        updateConnectionState(R.string.connected);
        mRequestReadCharacteristic.setEnabled(true);
        updateInputFromServer(SERVER_MSG_SECOND_STATE);
        */
    }


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }


    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_device_connect;
    }

    @Override
    protected int getTitleString() {
        return R.string.central_connection_screen;
    }


    @Override
    public void onClick(View view) {

        switch (view.getId()) {

            case R.id.request_read_characteristic:
                requestReadCharacteristic();
                break;


        }
    }


    /*
    request from the Server the value of the Characteristic.
    this request is asynchronous.
     */
    private void requestReadCharacteristic() {
        if (mBluetoothLeService != null && mCharacteristic != null) {
            mBluetoothLeService.readCharacteristic(mCharacteristic);
        } else {
            showMsgText(R.string.error_unknown);
        }
    }


    // TODO : send data etho message proto as an object
    private void sendWriteCharacteristic(){
        final String val = edMsg.getText().toString();
        //int value= Integer.valueOf(edMsg.getText().toString());

        if (!val.equals("") && !val.isEmpty()){
            if (mBluetoothLeService != null && mCharacteristic != null) {
                Toast.makeText(this, "write request", Toast.LENGTH_SHORT).show();

                    /**mCharacteristic.setValue(val);
                    mBluetoothLeService.writeCharacteristic(mCharacteristic);
                    */
                mBluetoothLeService.increaseMtu();
                // mCharacteristic.setValue(val);

                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                          mBluetoothLeService.sendDummyEthoMessage( mCharacteristic, val);
                      //  mBluetoothLeService.sendDummyWithOutTripGPs(mCharacteristic,value);
                    }
                },500);


            } else {
                if (mBluetoothLeService == null){
                    showMsgText("service is null");

                }
                else if (mCharacteristic == null){
                    showMsgText("characteristic is null");

                }
                showMsgText(R.string.error_unknown);
            }
        }
        else {
            Toast.makeText(this, "please enter msg", Toast.LENGTH_SHORT).show();

        }

    }

    private void sendGps() {
        int value= Integer.valueOf(edMsg.getText().toString());

        mBluetoothLeService.increaseMtu();
        // mCharacteristic.setValue(val);

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBluetoothLeService.sendDummyGPs(mCharacteristic,value);

            }
        },500);
    }
    private void sendRTsp() {
        mBluetoothLeService.increaseMtu();
        // mCharacteristic.setValue(val);

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBluetoothLeService.sendDummyRtspMessage(mCharacteristic);

            }
        },500);
    }
    private void sendFtpMsg() {

        if (mBluetoothLeService != null && mCharacteristic != null) {
            Toast.makeText(this, "write request", Toast.LENGTH_SHORT).show();

            /**mCharacteristic.setValue(val);
             mBluetoothLeService.writeCharacteristic(mCharacteristic);
             */
            mBluetoothLeService.increaseMtu();
            // mCharacteristic.setValue(val);

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothLeService.sendDummyEthoFTPMessage(mCharacteristic);

                }
            },500);


        } else {
            if (mBluetoothLeService == null){
                showMsgText("service is null");

            }
            else if (mCharacteristic == null){
                showMsgText("characteristic is null");

            }
            showMsgText(R.string.error_unknown);
        }
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {

            mBluetoothLeService = ((CentralService.LocalBinder) service).getService();

            if (!mBluetoothLeService.initialize()) {
                Log.e(MainActivity.TAG, "Unable to initialize Bluetooth");
                finish();
            }

            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };


    /*
     Handles various events fired by the Service.
     ACTION_GATT_CONNECTED: connected to a GATT server.
     ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
     ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
     ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read or notification operations.
    */
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (action == null) {
                return;
            }

            switch (intent.getAction()) {

                case CentralService.ACTION_GATT_CONNECTED:
                    updateConnectionState(R.string.connected);
                    mRequestReadCharacteristic.setEnabled(true);
                    mRequestWriteCharacteristic.setEnabled(true);
                    edMsg.setVisibility(View.VISIBLE);
                    break;

                case CentralService.ACTION_GATT_DISCONNECTED:
                    updateConnectionState(R.string.disconnected);
                    mRequestWriteCharacteristic.setEnabled(false);
                    mRequestReadCharacteristic.setEnabled(false);
                    edMsg.setVisibility(View.GONE);
                    break;


                case CentralService.ACTION_GATT_SERVICES_DISCOVERED:
                    // set all the supported services and characteristics on the user interface.
                    setGattServices(mBluetoothLeService.getSupportedGattServices());
                    registerCharacteristic();
                    break;

                case CentralService.ACTION_DATA_AVAILABLE:
                    int msg = intent.getIntExtra(CentralService.EXTRA_DATA, -1);
                    Log.v(MainActivity.TAG, "ACTION_DATA_AVAILABLE " + msg);
                    updateInputFromServer(msg);
                    break;

            }
        }
    };


    /*
     This sample demonstrates 'Read' and 'Notify' features.
     See http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
     list of supported characteristic features.
    */
    private void registerCharacteristic() {

        BluetoothGattCharacteristic characteristic = null;

        if (mDeviceServices != null) {

            /* iterate all the Services the connected device offer.
            a Service is a collection of Characteristic.
             */
            for (ArrayList<BluetoothGattCharacteristic> service : mDeviceServices) {

                // iterate all the Characteristic of the Service
                for (BluetoothGattCharacteristic serviceCharacteristic : service) {

                    /* check this characteristic belongs to the Service defined in
                    PeripheralAdvertiseService.buildAdvertiseData() method
                     */
                    if (serviceCharacteristic.getService().getUuid().equals(HEART_RATE_SERVICE_UUID)) {

                        if (serviceCharacteristic.getUuid().equals(BODY_SENSOR_LOCATION_CHARACTERISTIC_UUID)) {
                            characteristic = serviceCharacteristic;
                            mCharacteristic = characteristic;
                            showMsgText("characteristic set at 1");
                        }
                    }
                }
            }

           /*
            int charaProp = characteristic.getProperties();
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
            */

            if (characteristic != null) {
                //characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                mBluetoothLeService.readCharacteristic(characteristic);
                mBluetoothLeService.setCharacteristicNotification(characteristic, true);
                showMsgText("characteristic set at 2");
            }
        }
    }


    /*
    Demonstrates how to iterate through the supported GATT Services/Characteristics.
    */
    private void setGattServices(List<BluetoothGattService> gattServices) {

        if (gattServices == null) {
            return;
        }

        mDeviceServices = new ArrayList<>();

        // Loops through available GATT Services from the connected device
        for (BluetoothGattService gattService : gattServices) {
            ArrayList<BluetoothGattCharacteristic> characteristic = new ArrayList<>();
            characteristic.addAll(gattService.getCharacteristics()); // each GATT Service can have multiple characteristic
            mDeviceServices.add(characteristic);
        }

    }


    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionStatus.setText(resourceId);
            }
        });
    }


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CentralService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(CentralService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(CentralService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(CentralService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }


    private void updateInputFromServer(int msg) {

        String color;

        switch (msg) {
            case SERVER_MSG_FIRST_STATE:
                color = "#AD1457";
                break;

            case SERVER_MSG_SECOND_STATE:
                color = "#6A1B9A";
                break;

            default:
                color = "#FFFFFF";
                break;

        }

        mServerCharacteristic.setBackgroundColor(Color.parseColor(color));
        showMsgText(String.format(getString(R.string.characteristic_value_received), msg));
    }

    private byte[] convertToBytes(Object object) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(object);
            return bos.toByteArray();
        }
    }

    /*
    If you use Java >= 7, you could improve the accepted solution using try with resources:

private byte[] convertToBytes(Object object) throws IOException {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         ObjectOutput out = new ObjectOutputStream(bos)) {
        out.writeObject(object);
        return bos.toByteArray();
    }
}
And the other way around:

private Object convertFromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
    try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
         ObjectInput in = new ObjectInputStream(bis)) {
        return in.readObject();
    }
}
     */



}
