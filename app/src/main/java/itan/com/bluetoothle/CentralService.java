package itan.com.bluetoothle;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import proto.EthoFtpServerMessageProtos;
import proto.EthoGpsMessageProtos;
import proto.EthoHelmetInfoProtos;
import proto.EthoHotspotMessageProtos;
import proto.EthoMessageProtos;
import proto.EthoRtspMessageProtos;

import static itan.com.bluetoothle.Constants.BODY_SENSOR_LOCATION_CHARACTERISTIC_UUID;
import static itan.com.bluetoothle.Constants.HEART_RATE_SERVICE_UUID;



public class CentralService extends Service {

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED = "ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "EXTRA_DATA";


    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    private final IBinder mBinder = new LocalBinder();
    private int mConnectionState = STATE_DISCONNECTED;

    private int numberOfPackets;
    private int numberOfTimeDataInserted = -1;
    private boolean headerRecieved = false;
    private List<Byte> messageValue = new ArrayList<Byte>();


    private Integer packetSize;
    byte[][] packets;
    int packetInteration;

    /*
    Implements callback methods for GATT events that the app cares about.  For example,
    connection change and services discovered.
    */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            String intentAction;

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(MainActivity.TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(MainActivity.TAG, "Attempting to start service discovery:" + mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(MainActivity.TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(MainActivity.TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            } else {
                Log.w(MainActivity.TAG, "onCharacteristicRead GATT_FAILURE");
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            //broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            getData(characteristic.getValue());
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            if(packetInteration<packetSize){
                characteristic.setValue(packets[packetInteration]);
                gatt.writeCharacteristic(characteristic);
                packetInteration++;
            }
        }
    };


    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }


    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {

        Intent intent = new Intent(action);

        /*
            This is special handling for the Heart Rate Measurement profile.  Data parsing is
            carried out as per profile specifications:
            http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
         */
        if (BODY_SENSOR_LOCATION_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {

            int flag = characteristic.getProperties();
            int format = -1;

            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(MainActivity.TAG, "data format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(MainActivity.TAG, "data format UINT16.");
            }

            int msg = characteristic.getIntValue(format, 0);
            Log.d(MainActivity.TAG, String.format("message: %d", msg));
            intent.putExtra(EXTRA_DATA, msg);

        } else {

            /*
            for all other profiles, writes the data formatted in HEX.
            this code isn't relevant for this project.
            */
            final byte[] data = characteristic.getValue();

            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data) {
                    stringBuilder.append(String.format("%02X ", byteChar));
                }

                Log.w(MainActivity.TAG, "broadcastUpdate. general profile");
                intent.putExtra(EXTRA_DATA, -1);
                //intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }


        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        CentralService getService() {
            return CentralService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }


    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {

        // For API level 18 and above, get a reference to BluetoothAdapter through BluetoothManager.
        if (mBluetoothManager == null) {

            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

            if (mBluetoothManager == null) {
                Log.e(MainActivity.TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();

        if (mBluetoothAdapter == null) {
            Log.e(MainActivity.TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {

        if (mBluetoothAdapter == null || address == null) {
            Log.w(MainActivity.TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            Log.d(MainActivity.TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(MainActivity.TAG, "Device not found.  Unable to connect.");
            return false;
        }

        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        //mBluetoothGatt.requestMtu(512);
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;

        Log.d(MainActivity.TAG, "Trying to create a new connection.");

        return true;
    }


    // TODO bluetooth - call this method when needed
    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {

        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(MainActivity.TAG, "BluetoothAdapter not initialized");
            return;
        }

        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {

        if (mBluetoothGatt == null) {
            return;
        }

        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }



    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {

        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(MainActivity.TAG, "BluetoothAdapter not initialized");
            return;
        }

        mBluetoothGatt.readCharacteristic(characteristic);
    }


    public void writeCharacteristic(BluetoothGattCharacteristic characteristic){

        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(MainActivity.TAG, "BluetoothAdapter not initialized");
            Toast.makeText(this, "BluetoothAdapter not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "write", Toast.LENGTH_SHORT).show();
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    private void sendGpsMessage(){

    }
    public void sendDummyEthoMessage(BluetoothGattCharacteristic bluetoothGattCharacteristic , String dummyMsg) {
        EthoMessageProtos.EthoMessage.Builder messageBuilder = EthoMessageProtos.EthoMessage.newBuilder();
        EthoMessageProtos.EthoMessage.Content.Builder contentBuilder = EthoMessageProtos.EthoMessage.Content.newBuilder();


            EthoHotspotMessageProtos.EthoHotspotMessage.Builder hotspotMessageBuilder = EthoHotspotMessageProtos.EthoHotspotMessage.newBuilder();

            hotspotMessageBuilder.setHotspotMessageType(EthoHotspotMessageProtos.EthoHotspotMessage.HotspotMessageType.ENABLE_HOTSPOT);
            contentBuilder.setCategory(EthoMessageProtos.EthoMessage.Content.MessageCategory.WIFI);
            contentBuilder.setEthoHotspotMessage(hotspotMessageBuilder.build());




        messageBuilder.setMessageId(8);
        messageBuilder.setMessageRequestId(2);
        messageBuilder.setMessageResponseId(3);
        messageBuilder.setRequiresResponse(true);
        messageBuilder.setMessageType(EthoMessageProtos.EthoMessage.MessageType.COMMAND);
        messageBuilder.setContent(contentBuilder.build());



        sendData(messageBuilder.build().toByteArray(), bluetoothGattCharacteristic);
    }



    public void sendDummyEthoFTPMessage(BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        EthoMessageProtos.EthoMessage.Builder messageBuilder = EthoMessageProtos.EthoMessage.newBuilder();
        EthoMessageProtos.EthoMessage.Content.Builder contentBuilder = EthoMessageProtos.EthoMessage.Content.newBuilder();


            EthoFtpServerMessageProtos.EthoFtpServerMessage.Builder ftpServer = EthoFtpServerMessageProtos.EthoFtpServerMessage.newBuilder();

            ftpServer.setFtpMessageType(EthoFtpServerMessageProtos.EthoFtpServerMessage.FTPMessageType.CONNECT_FTP);
            contentBuilder.setCategory(EthoMessageProtos.EthoMessage.Content.MessageCategory.FTP);
            contentBuilder.setEthoFtpMessage(ftpServer.build());


       /* EthoHelmetInfoProtos.EthoHelmetInfo.Builder helmet = EthoHelmetInfoProtos.EthoHelmetInfo.newBuilder();
        helmet.setHelmetInfoMessageType(EthoHelmetInfoProtos.EthoHelmetInfo.HelmetInfoMessageType.GET_HELMET_INFO);
*/
        /*contentBuilder.setCategory(EthoMessageProtos.EthoMessage.Content.MessageCategory.HELMET_INFO);
        contentBuilder.setEthoHelmetInfoMessage(helmet.build());
       */ messageBuilder.setMessageId(8);
        messageBuilder.setMessageRequestId(2);
        messageBuilder.setMessageResponseId(3);
        messageBuilder.setRequiresResponse(true);
        messageBuilder.setMessageType(EthoMessageProtos.EthoMessage.MessageType.COMMAND);
        messageBuilder.setContent(contentBuilder.build());



        sendData(messageBuilder.build().toByteArray(), bluetoothGattCharacteristic);
    }

    public void sendDummyGPs(BluetoothGattCharacteristic bluetoothGattCharacteristic, int responseId) {
        EthoMessageProtos.EthoMessage.Builder messageBuilder = EthoMessageProtos.EthoMessage.newBuilder();
        EthoMessageProtos.EthoMessage.Content.Builder contentBuilder = EthoMessageProtos.EthoMessage.Content.newBuilder();

        EthoGpsMessageProtos.EthoGpsMessage.Builder gpsMessage = EthoGpsMessageProtos.EthoGpsMessage.newBuilder();

        EthoGpsMessageProtos.EthoGpsMessage.GpsData.Builder gpsBuilder = EthoGpsMessageProtos.EthoGpsMessage.GpsData.newBuilder();
        gpsBuilder.setLatitude("12");
        gpsBuilder.setLongitude("13");
        gpsBuilder.setSpeed("11");

        gpsMessage.setGpsData(gpsBuilder.build());
        gpsMessage.setTripId("13");

        gpsMessage.setGpsMessageType(EthoGpsMessageProtos.EthoGpsMessage.GpsMessageType.IMAGE_GPS_DATA_RESPONSE);
        contentBuilder.setCategory(EthoMessageProtos.EthoMessage.Content.MessageCategory.GPS);
        contentBuilder.setEthoGpsMessage(gpsMessage);

        messageBuilder.setContent(contentBuilder.build());
        messageBuilder.setMessageId(1);
        messageBuilder.setRequiresResponse(false);
        messageBuilder.setMessageResponseId(responseId);
        messageBuilder.setMessageType(EthoMessageProtos.EthoMessage.MessageType.RESPONSE);

        sendData(messageBuilder.build().toByteArray(), bluetoothGattCharacteristic);
    }

    public void sendDummyRtspMessage(BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        EthoMessageProtos.EthoMessage.Builder messageBuilder = EthoMessageProtos.EthoMessage.newBuilder();
        EthoMessageProtos.EthoMessage.Content.Builder contentBuilder = EthoMessageProtos.EthoMessage.Content.newBuilder();

        EthoRtspMessageProtos.EthoRtspMessage.Builder ethoRtspMessage = EthoRtspMessageProtos.EthoRtspMessage.newBuilder();
        ethoRtspMessage.setRtspMessageType(EthoRtspMessageProtos.EthoRtspMessage.RtspMessageType.START_STREAMING);
        ethoRtspMessage.setFileName("1621935090062_vid.mp4");

        contentBuilder.setCategory(EthoMessageProtos.EthoMessage.Content.MessageCategory.RTSP);
        contentBuilder.setEthoRtspMessage(ethoRtspMessage.build());

        messageBuilder.setContent(contentBuilder.build());
        messageBuilder.setMessageId(1);
        messageBuilder.setRequiresResponse(true);
        messageBuilder.setMessageType(EthoMessageProtos.EthoMessage.MessageType.COMMAND);

        sendData(messageBuilder.build().toByteArray(), bluetoothGattCharacteristic);
    }

    public void sendDummyWithOutTripGPs(BluetoothGattCharacteristic bluetoothGattCharacteristic, int responseId) {
        EthoMessageProtos.EthoMessage.Builder messageBuilder = EthoMessageProtos.EthoMessage.newBuilder();
        EthoMessageProtos.EthoMessage.Content.Builder contentBuilder = EthoMessageProtos.EthoMessage.Content.newBuilder();

        EthoGpsMessageProtos.EthoGpsMessage.Builder gpsMessage = EthoGpsMessageProtos.EthoGpsMessage.newBuilder();

        EthoGpsMessageProtos.EthoGpsMessage.GpsData.Builder gpsBuilder = EthoGpsMessageProtos.EthoGpsMessage.GpsData.newBuilder();
        gpsBuilder.setLatitude("12");
        gpsBuilder.setLongitude("13");
        gpsBuilder.setSpeed("11");

        gpsMessage.setGpsData(gpsBuilder.build());
       // gpsMessage.setTripId("13");

        gpsMessage.setGpsMessageType(EthoGpsMessageProtos.EthoGpsMessage.GpsMessageType.IMAGE_GPS_DATA_RESPONSE);
        contentBuilder.setCategory(EthoMessageProtos.EthoMessage.Content.MessageCategory.GPS);
        contentBuilder.setEthoGpsMessage(gpsMessage);

        messageBuilder.setContent(contentBuilder.build());
        messageBuilder.setMessageId(1);
        messageBuilder.setRequiresResponse(false);
        messageBuilder.setMessageResponseId(responseId);
        messageBuilder.setMessageType(EthoMessageProtos.EthoMessage.MessageType.RESPONSE);

        sendData(messageBuilder.build().toByteArray(), bluetoothGattCharacteristic);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void getData(byte[] data) {

        if (!headerRecieved) {

            String string = new String(data);
            numberOfPackets = Integer.parseInt(string);
            headerRecieved = true;
            numberOfTimeDataInserted++;

        } else if (numberOfTimeDataInserted < numberOfPackets) {
            for (int i = 0; i < data.length; i++) {
                messageValue.add(data[i]);
            }
            numberOfTimeDataInserted++;
            if (numberOfTimeDataInserted == numberOfPackets) {
                try {
                    byte[] result = messageValue.stream()
                            .collect(
                                    ByteArrayOutputStream::new,
                                    ByteArrayOutputStream::write,
                                    (a, b) -> {
                                    }).toByteArray();

                    EthoMessageProtos.EthoMessage ethoMessage = EthoMessageProtos.EthoMessage.parseFrom(result);

                    numberOfTimeDataInserted = -1;
                    numberOfPackets = 0;
                    headerRecieved = false;
                    messageValue.clear();

                    int jj = 102;
                    if (ethoMessage.getMessageType().equals(EthoMessageProtos.EthoMessage.MessageType.COMMAND)){
                        if (ethoMessage.getContent().getCategory().equals(EthoMessageProtos.EthoMessage.Content.MessageCategory.GPS)){
                            new Handler(Looper.getMainLooper()).post(() -> {

                                Toast.makeText(this, String.valueOf(ethoMessage.getMessageRequestId()), Toast.LENGTH_SHORT).show();

                            });
                        }
                    }
                } catch (Exception ex) {

                   // isDataParsed.postValue(false);
                    Log.e("Error", ex.getMessage());
                    int k = 90;
                }
            }
        }
    }

    public void sendData(byte [] data,BluetoothGattCharacteristic characteristicData) {
        int chunksize = 100; //20 byte chunk
        packetSize = (int) Math.ceil(data.length / (double) chunksize); //make this variable public so we can access it on the other function

        //this is use as header, so peripheral device know ho much packet will be received.
        characteristicData.setValue(packetSize.toString().getBytes());
        mBluetoothGatt.writeCharacteristic(characteristicData);
        mBluetoothGatt.executeReliableWrite();

        packets = new byte[packetSize][chunksize];
        packetInteration = 0;
        Integer start = 0;
        for (int i = 0; i < packets.length; i++) {
            int end = start + chunksize;
            if (end > data.length) {
                end = data.length;
            }
            packets[i] = Arrays.copyOfRange(data, start, end);
            start += chunksize;
        }

    }
    public void increaseMtu(){
        if (mBluetoothGatt != null){
            mBluetoothGatt.requestMtu(512);
        }
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {

        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(MainActivity.TAG, "BluetoothAdapter not initialized");
            return;
        }

        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        /*
        if (HEART_RATE_MEASUREMENT_UUID.toString().equals(characteristic.getUuid().toString())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION_UUID);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
        */

    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) {
            return null;
        }

        return mBluetoothGatt.getServices();
    }
}
