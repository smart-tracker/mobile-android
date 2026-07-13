package com.example.smarttracker.data.hrm.sensor

import android.content.Context

/**
 * Фабрика сенсоров пульса (паттерн LocationTrackerFactory).
 *
 * Сейчас единственный транспорт — BLE. Точка расширения для ANT+:
 * при добавлении Garmin ANT+ SDK здесь появится детектор доступности
 * ANT Radio Service (по образцу RuntimeDetector с guard'ом
 * NoClassDefFoundError) и ветка выбора транспорта.
 */
object HeartRateSensorFactory {

    fun create(context: Context): HeartRateSensor = BleHeartRateSensor(context)
}
