package com.noob_gallon.blepractice

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothClass.*
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.Toast
import java.io.File.separator

private val TAG:String = "jdk"

// bluetoothGatt는 callback.
class DeviceControlActivity(private val context: Context?, private var bluetoothGatt: BluetoothGatt?){

    private var device : BluetoothDevice? = null
    private var gattCallback : BluetoothGattCallback = object : BluetoothGattCallback() {
        // This abstract class is used to implement BluetoothGatt callbacks.
        // connectGatt function에 대한 callback을 받는 부분.

        @SuppressLint("MissingPermission")
        // onConnectionStateChange는 BLE Connection 상태가 변경될 때 호출되는 함수로,
        // newState가 BluetoothProfile.STATE_CONNECTED일 경우 bluetoothGatt?.discoverServices()를 호출.
        // BluetoothProfile.STATE_DISCONNECTED인 경우 disconnectGattServer()함수를 이용해
        // bluetoothGatt 연결을 해제하고 초기화한다.

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            val deviceAddress = gatt?.device?.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i(TAG, "Connected to GATT .")
                        Log.i(
                            TAG,
                            "Attempting to start service discovery: " + bluetoothGatt?.discoverServices()
                        )
                        // if the remote service discovery has been started => discoverServices() : true.
                        // 해당 서버에서 제공하는 서비스를 검색한다.
                        // 그러면 이후 해당 기기에서 서비스가 가능한 목록을 onServicesDiscovered 함수에 콜백을 시켜준다.
                        // 그럼 우리는 여기서 read/notify, write no response 특성을 활용 하기 위해
                        // 기기마다 제공하는 서비스(Battery Service, Heart Rate, Uart Service) 등등
                        // UUID 특성에 맞게 프로그래밍을 해야한다.
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        // 끌 때는 Service 목록을 찾지 않으므로, 여기서 바로 disconnect 해주면 된다.

                        Log.i(TAG, "Disconnected from GATT server.")
                        disconnectGattServer()
                    }
                }
            } else {
                Log.w(TAG, "Error $status encountered for $deviceAddress! Disconnecting...")
                gatt?.close()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            // 원격 장치에 대한 원격 서비스(Service), 특성(Characteristic) 및 설명자(Descriptor) 목록이 업데이트되었을 때 호출되는 콜백.
            super.onServicesDiscovered(gatt, status)

            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(TAG, "Connected to GATT_SUCCESS")
                    broadcastUpdate("Connected " + device?.name)
                }
                else -> {
                    Log.w(TAG, "Device service discovery failed, status: $status")
                    broadcastUpdate("Fail Connect " + device?.name)
                }
            }

            gatt?.printGattTable()

            val device = gatt?.device
            val address = device?.address

            var respCharacteristic: BluetoothGattCharacteristic? = null
            var cmdCharacteristic: BluetoothGattCharacteristic? = null

            val services = gatt?.services


            if (services != null) {
                loop@for (service in services) {
                    val characteristics = service?.characteristics
                    if (characteristics != null) {
                        for (characteristic in characteristics) {
                            if (characteristic.properties == PROPERTY_WRITE) {
                                cmdCharacteristic = characteristic
                                break@loop
                            }
                        }
                    }
                }

            val cmdBytes = ByteArray(2)
            cmdBytes[0] = 1
            cmdBytes[1] = 2

            writeCharacteristic(cmdCharacteristic!!, cmdBytes)
            }
        }

        private fun broadcastUpdate(str: String) {
            // Toast로 보여줄 메세지를 parameter로 받아, Handler의 handleMessage로 나타낸다.
            // Toast도 하나의 UI 작업이기 때문에, Thread 안에서 그냥 호출해주면 에러가 발생한다.

            val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    super.handleMessage(msg)
                    Toast.makeText(context, str, Toast.LENGTH_SHORT).show()
                }
            }
            mHandler.obtainMessage().sendToTarget()
        }

        @SuppressLint("MissingPermission")
        private fun disconnectGattServer() {
            Log.d(TAG, "Closing Gatt connection")
            // disconnect and close the gatt
            if (bluetoothGatt != null) {
                bluetoothGatt?.disconnect()
                // bluetoothGatt 연결 해제

                bluetoothGatt?.close()
                // bluetoothGatt 닫기

                bluetoothGatt = null
            }
        }

        private fun BluetoothGatt.printGattTable() {
            if (services.isEmpty()) {
                Log.i(TAG, "No service and characteristic available, call discoverServices() first?")
                return
            }
            services.forEach { service ->
                val characteristicsTable = service.characteristics.joinToString (
                    separator = "\n|--",
                    prefix = "|--"
                    ) { it.uuid.toString() }
                Log.i(TAG, "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)

            with(characteristic) {
                when(status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(TAG, "Wrote to characteristic ${this?.uuid} | value: ${this?.value}")
                    }
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                        Log.e(TAG, "Write exceeded connection ATT MTU!")
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e(TAG, "Write not permitted for ${this?.uuid}!", )
                    }
                    else -> {
                        Log.e(TAG, "Characteristic write failed for ${this?.uuid}!, error: $status", )
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectGatt(device:BluetoothDevice):BluetoothGatt? { // overloading function?
        // MainActivity에서 호출할 메서드로 위에서 설명한 내용과 같이 SDK의 버전에 따라 두 가지로 나누어 처리해준다.
        // Bluetooth Device의 connectGatt를 호출하면, 기기 연결을 수행 콜백을 호출한다.

        this.device = device

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // bluetoothGatt => Constructor
            // BluetoothDevice는 자체적으로 .connectGatt 함수를 이용할 수 있는 것으로 보임. 아마 override인듯?
            // 그래서, bluetoothGatt에 값이 들어간다. 얘는 MainActivity에서 선언된 callback 변수.

            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            // gattCallback : callback을 전달받을 object. BluetoothGattCallback
        }
        else {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        }
        return bluetoothGatt
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload:ByteArray) { // overloading...
        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> error("Characteristic ${characteristic.uuid} cannot be written to")
        }

        bluetoothGatt?.let { gatt ->
            characteristic.writeType = writeType
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        } ?: error("Error! bluetoothGatt is null!")
    }

    // fun BluetoothGattCharacteristic.isReadable():Boolean = containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    fun BluetoothGattCharacteristic.isWritable():Boolean = containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    fun BluetoothGattCharacteristic.isWritableWithoutResponse():Boolean = containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    fun BluetoothGattCharacteristic.containsProperty(property : Int):Boolean {
        return properties and property != 0
    }

}
