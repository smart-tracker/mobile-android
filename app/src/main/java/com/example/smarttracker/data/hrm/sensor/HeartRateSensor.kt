package com.example.smarttracker.data.hrm.sensor

import com.example.smarttracker.data.hrm.model.HrmScanResult

/**
 * Контракт источника данных пульса, не зависящий от транспорта.
 *
 * Реализации:
 * - [BleHeartRateSensor] — Bluetooth LE, GATT Heart Rate Service 0x180D
 * - (будущее) AntPlusHeartRateSensor — ANT+ через Garmin ANT+ SDK
 *
 * Выбор реализации — в [HeartRateSensorFactory] (паттерн LocationTrackerFactory).
 *
 * Сенсор намеренно «тонкий»: одна попытка подключения, без ретраев и
 * state-machine — политика переподключения живёт в HrmManager.
 */
interface HeartRateSensor {

    /**
     * Начать сканирование пульсометров.
     *
     * Повторный вызов перезапускает скан. Вызывающий обязан остановить
     * скан перед [connect] (скан во время подключения — источник GATT 133).
     *
     * @param onResult колбэк с найденным устройством; может вызываться
     *   многократно для одного устройства (обновление RSSI) и на любом потоке
     * @param onError  колбэк с кодом ошибки: ScanCallback.SCAN_FAILED_* или
     *   [BleHeartRateSensor.SCAN_ERROR_BLUETOOTH_OFF]
     */
    fun startScan(onResult: (HrmScanResult) -> Unit, onError: (Int) -> Unit)

    /**
     * Остановить сканирование. Безопасен для повторного вызова.
     */
    fun stopScan()

    /**
     * Одна попытка подключения к датчику по адресу.
     *
     * Исход приходит в [Listener]: [Listener.onConnected] при успешной
     * подписке на данные пульса, [Listener.onDisconnected] при любом сбое.
     * Таймаут попытки контролирует вызывающий (HrmManager).
     *
     * @param address  MAC-адрес из результата сканирования
     * @param listener приёмник событий; колбэки могут приходить на binder-потоках
     */
    fun connect(address: String, listener: Listener)

    /**
     * Разорвать соединение и освободить ресурсы.
     *
     * Безопасен для повторного вызова. После вызова события в Listener
     * не приходят (штатный разрыв не порождает onDisconnected).
     */
    fun disconnect()

    /**
     * События подключённого датчика. Потокобезопасность — на вызывающем:
     * колбэки приходят на binder-потоках Bluetooth-стека.
     */
    interface Listener {
        /** Соединение установлено и подписка на пульс активна. */
        fun onConnected()

        /**
         * Соединение разорвано или попытка подключения не удалась.
         *
         * @param unexpected true = обрыв не запрошен вызывающим
         *   (датчик снят/вне зоны/GATT-ошибка) — повод для реконнекта
         */
        fun onDisconnected(unexpected: Boolean)

        /** Новый сэмпл пульса (уд/мин). */
        fun onHeartRate(bpm: Int)
    }
}
