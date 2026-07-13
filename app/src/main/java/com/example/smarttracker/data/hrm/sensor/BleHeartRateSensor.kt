package com.example.smarttracker.data.hrm.sensor

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.example.smarttracker.data.hrm.model.HrmScanResult
import java.util.UUID

/**
 * BLE-реализация [HeartRateSensor] — стандартный GATT Heart Rate Service.
 *
 * Единственный класс подсистемы HRM, трогающий Android Bluetooth API.
 *
 * Дисциплина против GATT error 133 (нюанс 35):
 * - `close()` ВСЕГДА после `disconnect()` и при любом обрыве — незакрытый
 *   BluetoothGatt удерживает слот соединения в стеке;
 * - `TRANSPORT_LE` явно — dual-mode датчики (BLE+Classic) без него
 *   получают 133 на части Samsung;
 * - перед подключением скан должен быть остановлен (контракт интерфейса).
 *
 * API 33 split: колбэк `onCharacteristicChanged` и запись дескриптора имеют
 * старую и новую сигнатуры — реализованы ОБЕ ветки, иначе на одной из сторон
 * нотификации молча не приходят.
 *
 * Все Bluetooth-вызовы обёрнуты в try/catch SecurityException: разрешение
 * проверяет UI, но отзыв разрешения в процессе работы не должен ронять сервис.
 */
class BleHeartRateSensor(private val context: Context) : HeartRateSensor {

    companion object {
        private const val TAG = "BleHrmSensor"

        /** Heart Rate Service (Bluetooth SIG assigned number 0x180D). */
        val HRS_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

        /** Heart Rate Measurement characteristic (0x2A37). */
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

        /** Client Characteristic Configuration Descriptor (0x2902) — включение notify. */
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * Собственный код ошибки скана: адаптер выключен/недоступен.
         * Отрицательный — не пересекается с ScanCallback.SCAN_FAILED_* (1..6).
         */
        const val SCAN_ERROR_BLUETOOTH_OFF = -1
    }

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var scanCallback: ScanCallback? = null

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var listener: HeartRateSensor.Listener? = null

    /** true = разрыв запрошен через [disconnect] — не считать его неожиданным. */
    @Volatile
    private var expectingDisconnect = false

    // ---------------------------------------------------------------- scan

    override fun startScan(onResult: (HrmScanResult) -> Unit, onError: (Int) -> Unit) {
        stopScan()
        val scanner = adapter?.takeIf { safeIsEnabled(it) }?.bluetoothLeScanner
        if (scanner == null) {
            onError(SCAN_ERROR_BLUETOOTH_OFF)
            return
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // device.name на API 31+ требует BLUETOOTH_CONNECT — при отказе
                // берём имя из advertising-пакета (не требует разрешения)
                val name = try {
                    result.device.name
                } catch (e: SecurityException) {
                    null
                } ?: result.scanRecord?.deviceName
                onResult(HrmScanResult(result.device.address, name, result.rssi))
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "Скан не запустился: errorCode=$errorCode")
                onError(errorCode)
            }
        }
        // Фильтр по UUID сервиса: в результатах только пульсометры,
        // чужие BLE-устройства (наушники, метки) не попадают в список
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HRS_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(listOf(filter), settings, callback)
            scanCallback = callback
        } catch (e: SecurityException) {
            Log.w(TAG, "Нет разрешения на скан", e)
            onError(SCAN_ERROR_BLUETOOTH_OFF)
        }
    }

    override fun stopScan() {
        val callback = scanCallback ?: return
        scanCallback = null
        try {
            adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (e: SecurityException) {
            Log.w(TAG, "Нет разрешения на остановку скана", e)
        }
    }

    // ------------------------------------------------------------- connect

    override fun connect(address: String, listener: HeartRateSensor.Listener) {
        // Закрыть предыдущее соединение ДО нового — незакрытый gatt = 133
        disconnect()
        this.listener = listener
        expectingDisconnect = false

        val currentAdapter = adapter
        val device: BluetoothDevice? = try {
            currentAdapter?.takeIf { safeIsEnabled(it) }?.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Некорректный адрес датчика", e)
            null
        }
        if (device == null) {
            // Адаптер выключен или адрес битый — попытка сразу неудачна
            this.listener = null
            listener.onDisconnected(unexpected = true)
            return
        }
        try {
            gatt = device.connectGatt(
                context,
                /* autoConnect = */ false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Нет разрешения на подключение", e)
            this.listener = null
            listener.onDisconnected(unexpected = true)
        }
    }

    override fun disconnect() {
        expectingDisconnect = true
        // Колбэки старого соединения больше не интересны: штатный разрыв
        // не должен порождать onDisconnected (менеджер сам ведёт состояние)
        listener = null
        gatt?.let { g ->
            try {
                g.disconnect()
            } catch (e: SecurityException) {
                Log.w(TAG, "Нет разрешения на disconnect", e)
            }
            try {
                g.close()
            } catch (e: SecurityException) {
                Log.w(TAG, "Нет разрешения на close", e)
            }
        }
        gatt = null
    }

    // ------------------------------------------------------- GATT callback

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    try {
                        g.discoverServices()
                    } catch (e: SecurityException) {
                        failConnection(g)
                    }
                }

                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Разрыв соединения: status=$status expected=$expectingDisconnect")
                    failConnection(g)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val characteristic = g.getService(HRS_SERVICE_UUID)?.getCharacteristic(HR_MEASUREMENT_UUID)
            if (status != BluetoothGatt.GATT_SUCCESS || characteristic == null) {
                Log.w(TAG, "Heart Rate сервис не найден: status=$status")
                failConnection(g)
                return
            }
            subscribeToHeartRate(g, characteristic)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid != CCCD_UUID) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Подписка активна — только теперь соединение считается рабочим
                listener?.onConnected()
            } else {
                Log.w(TAG, "Запись CCCD не удалась: status=$status")
                failConnection(g)
            }
        }

        // API < 33: значение читается из characteristic.value
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            handleHrPayload(characteristic.uuid, value)
        }

        // API 33+: значение приходит аргументом
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleHrPayload(characteristic.uuid, value)
        }
    }

    /**
     * Включить нотификации 0x2A37: локальный флаг + запись CCCD на датчике.
     * Единственная GATT-запись в v1 — очередь операций не нужна.
     */
    private fun subscribeToHeartRate(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        try {
            if (!g.setCharacteristicNotification(characteristic, true)) {
                failConnection(g)
                return
            }
            val cccd = characteristic.getDescriptor(CCCD_UUID)
            if (cccd == null) {
                Log.w(TAG, "CCCD отсутствует у 0x2A37 — датчик вне спецификации")
                failConnection(g)
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        } catch (e: SecurityException) {
            failConnection(g)
        }
    }

    private fun handleHrPayload(uuid: UUID, value: ByteArray) {
        if (uuid != HR_MEASUREMENT_UUID) return
        val bpm = HrmParser.parseHeartRateMeasurement(value) ?: return
        listener?.onHeartRate(bpm)
    }

    /**
     * Закрыть gatt и сообщить слушателю об обрыве (если он не запрошен штатно).
     */
    private fun failConnection(g: BluetoothGatt) {
        val wasExpected = expectingDisconnect
        try {
            g.close()
        } catch (e: SecurityException) {
            Log.w(TAG, "Нет разрешения на close", e)
        }
        if (gatt === g) gatt = null
        val currentListener = listener
        listener = null
        currentListener?.onDisconnected(unexpected = !wasExpected)
    }

    /** isEnabled на API 31+ формально требует BLUETOOTH_CONNECT — страховка. */
    private fun safeIsEnabled(adapter: BluetoothAdapter): Boolean = try {
        adapter.isEnabled
    } catch (e: SecurityException) {
        false
    }
}
