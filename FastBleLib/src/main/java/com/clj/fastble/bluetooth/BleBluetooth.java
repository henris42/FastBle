package com.clj.fastble.bluetooth;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleIndicateCallback;
import com.clj.fastble.callback.BleMtuChangedCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleReadCallback;
import com.clj.fastble.callback.BleRssiCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleConnectStateParameter;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.data.BleMsg;
import com.clj.fastble.exception.ConnectException;
import com.clj.fastble.exception.OtherException;
import com.clj.fastble.exception.TimeoutException;
import com.clj.fastble.utils.BleLog;
import com.clj.fastble.BluetoothGattQueued;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;

import kotlinx.coroutines.GlobalScope;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BleBluetooth {

    private BleGattCallback bleGattCallback;
    private BleRssiCallback bleRssiCallback;
    private BleMtuChangedCallback bleMtuChangedCallback;
    private final HashMap<String, BleNotifyCallback> bleNotifyCallbackHashMap = new HashMap<>();
    private final HashMap<String, BleIndicateCallback> bleIndicateCallbackHashMap = new HashMap<>();
    private final HashMap<String, BleWriteCallback> bleWriteCallbackHashMap = new HashMap<>();
    private final HashMap<String, BleReadCallback> bleReadCallbackHashMap = new HashMap<>();

    private LastState lastState;
    private boolean isActiveDisconnect = false;
    private final BleDevice bleDevice;
    private BluetoothGatt bluetoothGatt;
    //private BluetoothGattQueued bluetoothGattQueued;
    private final MainHandler mainHandler = new MainHandler(Looper.getMainLooper());
    private int connectRetryCount = 0;

    public BleBluetooth(BleDevice bleDevice) {
        this.bleDevice = bleDevice;
    }

    public BleConnector newBleConnector() {
        return new BleConnector(this);
    }

    public synchronized void addConnectGattCallback(BleGattCallback callback) {
        bleGattCallback = callback;
    }

    public synchronized void removeConnectGattCallback() {
        bleGattCallback = null;
    }

    public synchronized void addNotifyCallback(String uuid, BleNotifyCallback bleNotifyCallback) {
        bleNotifyCallbackHashMap.put(uuid, bleNotifyCallback);
    }

    public synchronized void addIndicateCallback(String uuid, BleIndicateCallback bleIndicateCallback) {
        bleIndicateCallbackHashMap.put(uuid, bleIndicateCallback);
    }

    public synchronized void addWriteCallback(String uuid, BleWriteCallback bleWriteCallback) {
        bleWriteCallbackHashMap.put(uuid, bleWriteCallback);
    }

    public synchronized void addReadCallback(String uuid, BleReadCallback bleReadCallback) {
        bleReadCallbackHashMap.put(uuid, bleReadCallback);
    }

    public synchronized void removeNotifyCallback(String uuid) {
        if (bleNotifyCallbackHashMap.containsKey(uuid))
            bleNotifyCallbackHashMap.remove(uuid);
    }

    public synchronized void removeIndicateCallback(String uuid) {
        if (bleIndicateCallbackHashMap.containsKey(uuid))
            bleIndicateCallbackHashMap.remove(uuid);
    }

    public synchronized void removeWriteCallback(String uuid) {
        if (bleWriteCallbackHashMap.containsKey(uuid))
            bleWriteCallbackHashMap.remove(uuid);
    }

    public synchronized void removeReadCallback(String uuid) {
        if (bleReadCallbackHashMap.containsKey(uuid))
            bleReadCallbackHashMap.remove(uuid);
    }

    public synchronized void clearCharacterCallback() {
        bleNotifyCallbackHashMap.clear();
        bleIndicateCallbackHashMap.clear();
        bleWriteCallbackHashMap.clear();
        bleReadCallbackHashMap.clear();
    }

    public synchronized void addRssiCallback(BleRssiCallback callback) {
        bleRssiCallback = callback;
    }

    public synchronized void removeRssiCallback() {
        bleRssiCallback = null;
    }

    public synchronized void addMtuChangedCallback(BleMtuChangedCallback callback) {
        bleMtuChangedCallback = callback;
    }

    public synchronized void removeMtuChangedCallback() {
        bleMtuChangedCallback = null;
    }


    public String getDeviceKey() {
        return bleDevice.getKey();
    }

    public BleDevice getDevice() {
        return bleDevice;
    }

    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
    }
    public BluetoothGattQueued getBluetoothGattQueued() {
        return bluetoothGattQueued;
    }

    public synchronized BluetoothGattQueued connect(BleDevice bleDevice,
                                              boolean autoConnect,
                                              BleGattCallback callback) {
        return connect(bleDevice, autoConnect, callback, 0);
    }

    public synchronized BluetoothGattQueued connect(BleDevice bleDevice,
                                              boolean autoConnect,
                                              BleGattCallback callback,
                                              int connectRetryCount) {
        BleLog.i("connect device: " + bleDevice.getName()
                + "\nmac: " + bleDevice.getMac()
                + "\nautoConnect: " + autoConnect
                + "\ncurrentThread: " + Thread.currentThread().getId()
                + "\nconnectCount:" + (connectRetryCount + 1));
        if (connectRetryCount == 0) {
            this.connectRetryCount = 0;
        }

        addConnectGattCallback(callback);

        lastState = LastState.CONNECT_CONNECTING;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = bleDevice.getDevice().connectGatt(BleManager.getInstance().getContext(),
                    autoConnect, bluetoothGattQueued, TRANSPORT_LE);
        } else {
            bluetoothGatt = bleDevice.getDevice().connectGatt(BleManager.getInstance().getContext(),
                    autoConnect, bluetoothGattQueued);
        }
        if (bluetoothGatt != null) {
            if (bleGattCallback != null) {
                bleGattCallback.onStartConnect();
            }
            Message message = mainHandler.obtainMessage();
            message.what = BleMsg.MSG_CONNECT_OVER_TIME;
            mainHandler.sendMessageDelayed(message, BleManager.getInstance().getConnectOverTime());

        } else {
            disconnectGatt();
            refreshDeviceCache();
            closeBluetoothGatt();
            lastState = LastState.CONNECT_FAILURE;
            BleManager.getInstance().getMultipleBluetoothController().removeConnectingBle(BleBluetooth.this);
            if (bleGattCallback != null)
                bleGattCallback.onConnectFail(bleDevice, new OtherException("GATT connect exception occurred!"));

        }
        bluetoothGattQueued.setGatt(bluetoothGatt);
        return bluetoothGattQueued;
    }

    public synchronized void disconnect() {
        isActiveDisconnect = true;
        disconnectGatt();
    }

    public synchronized void destroy() {
        lastState = LastState.CONNECT_IDLE;
        disconnectGatt();
        refreshDeviceCache();
        closeBluetoothGatt();
        removeConnectGattCallback();
        removeRssiCallback();
        removeMtuChangedCallback();
        clearCharacterCallback();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private synchronized void disconnectGatt() {
        if (bluetoothGattQueued != null) {
            bluetoothGattQueued.disconnect();
        }
    }

    private synchronized void refreshDeviceCache() {
        try {
            final Method refresh = BluetoothGatt.class.getMethod("refresh");
            if (refresh != null && bluetoothGatt != null) {
                boolean success = (Boolean) refresh.invoke(bluetoothGatt);
                BleLog.i("refreshDeviceCache, is success:  " + success);
            }
        } catch (Exception e) {
            BleLog.i("exception occur while refreshing device: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private synchronized void closeBluetoothGatt() {
        if (bluetoothGattQueued != null) {
            bluetoothGattQueued.close();
        }
    }

    private final class MainHandler extends Handler {

        MainHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BleMsg.MSG_CONNECT_FAIL: {
                    disconnectGatt();
                    refreshDeviceCache();
                    closeBluetoothGatt();

                    if (connectRetryCount < BleManager.getInstance().getReConnectCount()) {
                        BleLog.e("Connect fail, try reconnect " + BleManager.getInstance().getReConnectInterval() + " millisecond later");
                        ++connectRetryCount;

                        Message message = mainHandler.obtainMessage();
                        message.what = BleMsg.MSG_RECONNECT;
                        mainHandler.sendMessageDelayed(message, BleManager.getInstance().getReConnectInterval());
                    } else {
                        lastState = LastState.CONNECT_FAILURE;
                        BleManager.getInstance().getMultipleBluetoothController().removeConnectingBle(BleBluetooth.this);

                        BleConnectStateParameter para = (BleConnectStateParameter) msg.obj;
                        int status = para.getStatus();
                        if (bleGattCallback != null)
                            bleGattCallback.onConnectFail(bleDevice, new ConnectException(bluetoothGatt, status));
                    }
                }
                break;

                case BleMsg.MSG_DISCONNECTED: {
                    lastState = LastState.CONNECT_DISCONNECT;
                    BleManager.getInstance().getMultipleBluetoothController().removeBleBluetooth(BleBluetooth.this);

                    disconnect();
                    refreshDeviceCache();
                    closeBluetoothGatt();
                    removeRssiCallback();
                    removeMtuChangedCallback();
                    clearCharacterCallback();
                    mainHandler.removeCallbacksAndMessages(null);

                    BleConnectStateParameter para = (BleConnectStateParameter) msg.obj;
                    boolean isActive = para.isActive();
                    int status = para.getStatus();
                    if (bleGattCallback != null)
                        bleGattCallback.onDisConnected(isActive, bleDevice, bluetoothGatt, status);
                }
                break;

                case BleMsg.MSG_RECONNECT: {
                    connect(bleDevice, false, bleGattCallback, connectRetryCount);
                }
                break;

                case BleMsg.MSG_CONNECT_OVER_TIME: {
                    disconnectGatt();
                    refreshDeviceCache();
                    closeBluetoothGatt();

                    lastState = LastState.CONNECT_FAILURE;
                    BleManager.getInstance().getMultipleBluetoothController().removeConnectingBle(BleBluetooth.this);

                    if (bleGattCallback != null)
                        bleGattCallback.onConnectFail(bleDevice, new TimeoutException());
                }
                break;

                case BleMsg.MSG_DISCOVER_SERVICES: {
                    if (bluetoothGatt != null) {
                        boolean discoverServiceResult = bluetoothGatt.discoverServices();
                        if (!discoverServiceResult) {
                            Message message = mainHandler.obtainMessage();
                            message.what = BleMsg.MSG_DISCOVER_FAIL;
                            mainHandler.sendMessage(message);
                        }
                    } else {
                        Message message = mainHandler.obtainMessage();
                        message.what = BleMsg.MSG_DISCOVER_FAIL;
                        mainHandler.sendMessage(message);
                    }
                }
                break;

                case BleMsg.MSG_DISCOVER_FAIL: {
                    disconnectGatt();
                    refreshDeviceCache();
                    closeBluetoothGatt();

                    lastState = LastState.CONNECT_FAILURE;
                    BleManager.getInstance().getMultipleBluetoothController().removeConnectingBle(BleBluetooth.this);

                    if (bleGattCallback != null)
                        bleGattCallback.onConnectFail(bleDevice,
                                new OtherException("GATT discover services exception occurred!"));
                }
                break;

                case BleMsg.MSG_DISCOVER_SUCCESS: {
                    lastState = LastState.CONNECT_CONNECTED;
                    isActiveDisconnect = false;
                    BleManager.getInstance().getMultipleBluetoothController().removeConnectingBle(BleBluetooth.this);
                    BleManager.getInstance().getMultipleBluetoothController().addBleBluetooth(BleBluetooth.this);

                    BleConnectStateParameter para = (BleConnectStateParameter) msg.obj;
                    int status = para.getStatus();
                    if (bleGattCallback != null)
                        bleGattCallback.onConnectSuccess(bleDevice, bluetoothGatt, status);
                }
                break;

                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    private BluetoothGattQueued bluetoothGattQueued = new BluetoothGattQueued() {

        @Override
        public void onConnectionStateChange(BluetoothGattQueued gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            BleLog.i("BluetoothGattCallback：onConnectionStateChange "
                    + '\n' + "status: " + status
                    + '\n' + "newState: " + newState
                    + '\n' + "currentThread: " + Thread.currentThread().getId());

            bluetoothGatt = gatt.getGatt();

            mainHandler.removeMessages(BleMsg.MSG_CONNECT_OVER_TIME);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Message message = mainHandler.obtainMessage();
                message.what = BleMsg.MSG_DISCOVER_SERVICES;
                mainHandler.sendMessageDelayed(message, 500);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (lastState == LastState.CONNECT_CONNECTING) {
                    Message message = mainHandler.obtainMessage();
                    message.what = BleMsg.MSG_CONNECT_FAIL;
                    message.obj = new BleConnectStateParameter(status);
                    mainHandler.sendMessage(message);

                } else if (lastState == LastState.CONNECT_CONNECTED) {
                    Message message = mainHandler.obtainMessage();
                    message.what = BleMsg.MSG_DISCONNECTED;
                    BleConnectStateParameter para = new BleConnectStateParameter(status);
                    para.setActive(isActiveDisconnect);
                    message.obj = para;
                    mainHandler.sendMessage(message);
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGattQueued gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            BleLog.i("BluetoothGattCallback：onServicesDiscovered "
                    + '\n' + "status: " + status
                    + '\n' + "currentThread: " + Thread.currentThread().getId());

            bluetoothGatt = gatt.getGatt();

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Message message = mainHandler.obtainMessage();
                message.what = BleMsg.MSG_DISCOVER_SUCCESS;
                message.obj = new BleConnectStateParameter(status);
                mainHandler.sendMessage(message);

            } else {
                Message message = mainHandler.obtainMessage();
                message.what = BleMsg.MSG_DISCOVER_FAIL;
                mainHandler.sendMessage(message);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGattQueued gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            Iterator iterator = bleNotifyCallbackHashMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                Object callback = entry.getValue();
                if (callback instanceof BleNotifyCallback) {
                    BleNotifyCallback bleNotifyCallback = (BleNotifyCallback) callback;
                    if (characteristic.getUuid().toString().equalsIgnoreCase(bleNotifyCallback.getKey())) {
                        Handler handler = bleNotifyCallback.getHandler();
                        if (handler != null) {
                            Message message = handler.obtainMessage();
                            message.what = BleMsg.MSG_CHA_NOTIFY_DATA_CHANGE;
                            message.obj = bleNotifyCallback;
                            Bundle bundle = new Bundle();
                            bundle.putByteArray(BleMsg.KEY_NOTIFY_BUNDLE_VALUE, characteristic.getValue());
                            message.setData(bundle);
                            handler.dispatchMessage(message);
                        }
                    }
                }
            }

            iterator = bleIndicateCallbackHashMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                Object callback = entry.getValue();
                if (callback instanceof BleIndicateCallback) {
                    BleIndicateCallback bleIndicateCallback = (BleIndicateCallback) callback;
                    if (characteristic.getUuid().toString().equalsIgnoreCase(bleIndicateCallback.getKey())) {
                        Handler handler = bleIndicateCallback.getHandler();
                        if (handler != null) {
                            Message message = handler.obtainMessage();
                            message.what = BleMsg.MSG_CHA_INDICATE_DATA_CHANGE;
                            message.obj = bleIndicateCallback;
                            Bundle bundle = new Bundle();
                            bundle.putByteArray(BleMsg.KEY_INDICATE_BUNDLE_VALUE, characteristic.getValue());
                            message.setData(bundle);
                            handler.dispatchMessage(message);
                        }
                    }
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGattQueued gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);

            Iterator iterator = bleNotifyCallbackHashMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                Object callback = entry.getValue();
                if (callback instanceof BleNotifyCallback) {
                    BleNotifyCallback bleNotifyCallback = (BleNotifyCallback) callback;
                    if (descriptor.getCharacteristic().getUuid().toString().equalsIgnoreCase(bleNotifyCallback.getKey())) {
                        Handler handler = bleNotifyCallback.getHandler();
                        if (handler != null) {
                            Message message = handler.obtainMessage();
                            message.what = BleMsg.MSG_CHA_NOTIFY_RESULT;
                            message.obj = bleNotifyCallback;
                            Bundle bundle = new Bundle();
                            bundle.putInt(BleMsg.KEY_NOTIFY_BUNDLE_STATUS, status);
                            message.setData(bundle);
                            handler.dispatchMessage(message);
                        }
                    }
                }
            }

            iterator = bleIndicateCallbackHashMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                Object callback = entry.getValue();
                if (callback instanceof BleIndicateCallback) {
                    BleIndicateCallback bleIndicateCallback = (BleIndicateCallback) callback;
                    if (descriptor.getCharacteristic().getUuid().toString().equalsIgnoreCase(bleIndicateCallback.getKey())) {
                        Handler handler = bleIndicateCallback.getHandler();
                        if (handler != null) {
                            Message message = handler.obtainMessage();
                            message.what = BleMsg.MSG_CHA_INDICATE_RESULT;
                            message.obj = bleIndicateCallback;
                            Bundle bundle = new Bundle();
                            bundle.putInt(BleMsg.KEY_INDICATE_BUNDLE_STATUS, status);
                            message.setData(bundle);
                            handler.dispatchMessage(message);
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGattQueued gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            Iterator iterator = bleWriteCallbackHashMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                Object callback = entry.getValue();
                if (callback instanceof BleWriteCallback) {
                    BleWriteCallback bleWriteCallback = (BleWriteCallback) callback;
                    if (characteristic.getUuid().toString().equalsIgnoreCase(bleWriteCallback.getKey())) {
                        Handler handler = bleWriteCallback.getHandler();
                        if (handler != null) {
                            Message message = handler.obtainMessage();
                            message.what = BleMsg.MSG_CHA_WRITE_RESULT;
                            message.obj = bleWriteCallback;
                            Bundle bundle = new Bundle();
                            bundle.putInt(BleMsg.KEY_WRITE_BUNDLE_STATUS, status);
                            bundle.putByteArray(BleMsg.KEY_WRITE_BUNDLE_VALUE, characteristic.getValue());
                            message.setData(bundle);
                            handler.dispatchMessage(message);
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGattQueued gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Iterator iterator = bleReadCallbackHashMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                Object callback = entry.getValue();
                if (callback instanceof BleReadCallback) {
                    BleReadCallback bleReadCallback = (BleReadCallback) callback;
                    if (characteristic.getUuid().toString().equalsIgnoreCase(bleReadCallback.getKey())) {
                        Handler handler = bleReadCallback.getHandler();
                        if (handler != null) {
                            Message message = handler.obtainMessage();
                            message.what = BleMsg.MSG_CHA_READ_RESULT;
                            message.obj = bleReadCallback;
                            Bundle bundle = new Bundle();
                            bundle.putInt(BleMsg.KEY_READ_BUNDLE_STATUS, status);
                            bundle.putByteArray(BleMsg.KEY_READ_BUNDLE_VALUE, characteristic.getValue());
                            message.setData(bundle);

                            handler.dispatchMessage(message);
                        }

                    }
                }
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);

            if (bleRssiCallback != null) {
                Handler handler = bleRssiCallback.getHandler();
                if (handler != null) {
                    Message message = handler.obtainMessage();
                    message.what = BleMsg.MSG_READ_RSSI_RESULT;
                    message.obj = bleRssiCallback;
                    Bundle bundle = new Bundle();
                    bundle.putInt(BleMsg.KEY_READ_RSSI_BUNDLE_STATUS, status);
                    bundle.putInt(BleMsg.KEY_READ_RSSI_BUNDLE_VALUE, rssi);
                    message.setData(bundle);
                    handler.sendMessage(message);
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);

            if (bleMtuChangedCallback != null) {
                Handler handler = bleMtuChangedCallback.getHandler();
                if (handler != null) {
                    Message message = handler.obtainMessage();
                    message.what = BleMsg.MSG_SET_MTU_RESULT;
                    message.obj = bleMtuChangedCallback;
                    Bundle bundle = new Bundle();
                    bundle.putInt(BleMsg.KEY_SET_MTU_BUNDLE_STATUS, status);
                    bundle.putInt(BleMsg.KEY_SET_MTU_BUNDLE_VALUE, mtu);
                    message.setData(bundle);
                    handler.sendMessage(message);
                }
            }
        }
    };

    enum LastState {
        CONNECT_IDLE,
        CONNECT_CONNECTING,
        CONNECT_CONNECTED,
        CONNECT_FAILURE,
        CONNECT_DISCONNECT
    }

}
