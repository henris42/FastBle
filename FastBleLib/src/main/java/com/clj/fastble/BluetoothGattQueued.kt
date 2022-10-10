package com.clj.fastble

import android.bluetooth.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.GlobalScope
import java.util.*

open class BluetoothGattQueued() : BluetoothGattCallback(){
    lateinit var mGatt: BluetoothGatt
    var pending: Boolean = false

    init {
        Log.v("GWAPI", "BluetoothGattQueued()")
    }

    fun setGatt(gatt: BluetoothGatt) {
        mGatt = gatt
    }

    fun getGatt():BluetoothGatt {
        return mGatt
    }
    fun close() {
        mGatt.close()
    }

    fun disconnect() {
        mGatt.disconnect()
    }

    fun connect(): Boolean {
        Log.v("GWAPI", "BluetoothGattQueued connect()")
        while(pending) {}
        pending=true
        return mGatt.connect()
    }

    fun setPreferredPhy(txPhy: Int, rxPhy: Int, phyOptions: Int) {
        mGatt.setPreferredPhy(txPhy, rxPhy, phyOptions)
    }

    fun readPhy() {
        mGatt.readPhy()
    }

    fun getDevice(): BluetoothDevice? {
        return mGatt.getDevice()
    }

    fun discoverServices(): Boolean {
        Log.v("GWAPI", "BluetoothGattQueued discoverServices()")
        return mGatt.discoverServices()
    }

    fun getServices(): List<BluetoothGattService?>? {
        Log.v("GWAPI", "BluetoothGattQueued getServices()")
        return mGatt.getServices()
    }

    fun getService(uuid: UUID?): BluetoothGattService? {
        Log.v("GWAPI", "BluetoothGattQueued getService()")
        return mGatt.getService(uuid)
    }

    fun readCharacteristic(characteristic: BluetoothGattCharacteristic?): Boolean {
        Log.v("GWAPI", "BluetoothGattQueued readCharacteristic()")
        while(pending) {}
        pending=true
        return mGatt.readCharacteristic(characteristic)
    }

    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic?): Boolean {
        Log.v("GWAPI", "BluetoothGattQueued writeCharacteristic()")
        while(pending) {}
        pending=true
        return mGatt.writeCharacteristic(characteristic)
    }

    fun readDescriptor(descriptor: BluetoothGattDescriptor?): Boolean {
        return mGatt.readDescriptor(descriptor)
    }

    fun writeDescriptor(descriptor: BluetoothGattDescriptor?): Boolean {
        Log.v("GWAPI", "BluetoothGattQueued writeDescriptor()")
        while(pending) {}
        pending=true
        return mGatt.writeDescriptor(descriptor)
    }

    fun beginReliableWrite(): Boolean {
        return mGatt.beginReliableWrite()
    }

    fun executeReliableWrite(): Boolean {
        return mGatt.executeReliableWrite()
    }

    fun abortReliableWrite() {
        return mGatt.abortReliableWrite()
    }

    fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic?,
        enable: Boolean
    ): Boolean {
        Log.v("GWAPI", "BluetoothGattQueued setCharacteristicNotification()")
        return mGatt.setCharacteristicNotification(characteristic,enable)
    }

    fun readRemoteRssi(): Boolean {
        return mGatt.readRemoteRssi()
    }

    fun requestMtu(mtu: Int): Boolean {
        return mGatt.requestMtu(mtu)
    }

    fun requestConnectionPriority(connectionPriority: Int): Boolean {
        return mGatt.requestConnectionPriority(connectionPriority)
    }

    fun getConnectionState(device: BluetoothDevice?): Int {
        return mGatt.getConnectionState(device)
    }

    fun getConnectedDevices(): List<BluetoothDevice?>? {
        return mGatt.getConnectedDevices()
    }

    fun getDevicesMatchingConnectionStates(states: IntArray?): List<BluetoothDevice?>? {
        return mGatt.getDevicesMatchingConnectionStates(states)
    }

    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status:Int) {
        Log.v("GWAPI", "BluetoothGatt onCharacteristicRead() thread "+ Thread.currentThread())
        onCharacteristicRead(this, characteristic, status)
    }

    open fun onCharacteristicRead(gatt: BluetoothGattQueued, characteristic: BluetoothGattCharacteristic, status:Int) {
        Log.v("GWAPI", "BluetoothGattQueued onCharacteristicRead()")
        pending=false
    }
    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status:Int) {
        Log.v("GWAPI", "BluetoothGatt onCharacteristicWrite()")
        onCharacteristicWrite(this,characteristic,status)
    }

    open fun onCharacteristicWrite(gatt: BluetoothGattQueued, characteristic: BluetoothGattCharacteristic, status:Int) {
        Log.v("GWAPI", "BluetoothGattQueued onCharacteristicWrite()")
        pending=false
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.v("GWAPI", "BluetoothGatt onCharacteristicChanged()")
        onCharacteristicChanged(this,characteristic)
    }

    open fun onCharacteristicChanged(gatt: BluetoothGattQueued, characteristic: BluetoothGattCharacteristic) {
        Log.v("GWAPI", "BluetoothGattQueued onCharacteristicChanged()")
        pending=false
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        Log.v("GWAPI", "BluetoothGatt onDescriptorWrite()")
        onDescriptorWrite(this,descriptor,status)
    }
    open fun onDescriptorWrite(gatt: BluetoothGattQueued, descriptor: BluetoothGattDescriptor, status: Int) {
        Log.v("GWAPI", "BluetoothGattQueued onDescriptorWrite()")
        pending=false
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        Log.v("GWAPI", "BluetoothGatt onConnectionStateChange()")
        onConnectionStateChange(this,status,newState)
    }
    open fun onConnectionStateChange(gatt: BluetoothGattQueued, status: Int, newState: Int) {
        Log.v("GWAPI", "BluetoothGattQueued onConnectionStateChange()")
        pending=false
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        Log.v("GWAPI", "BluetoothGatt onServicesDiscovered()")
        onServicesDiscovered(this, status)
    }

    open fun onServicesDiscovered(gatt: BluetoothGattQueued, status: Int) {
        Log.v("GWAPI", "BluetoothGattQueued onServicesDiscovered()")
        pending=false

    }
}