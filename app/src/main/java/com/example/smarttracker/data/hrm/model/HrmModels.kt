package com.example.smarttracker.data.hrm.model

/**
 * Модели подсистемы внешних пульсометров (HRM — Heart Rate Monitor).
 *
 * Транспорт-агностичны: не содержат типов Android Bluetooth API,
 * чтобы будущая ANT+-реализация использовала те же модели.
 */

/**
 * Результат сканирования — найденный пульсометр.
 *
 * @param address MAC-адрес устройства (ключ дедупликации и подключения)
 * @param name    имя устройства из advertising-пакета; null если датчик его не вещает
 * @param rssi    уровень сигнала (dBm, ближе к 0 = сильнее) — для сортировки списка
 */
data class HrmScanResult(
    val address: String,
    val name: String?,
    val rssi: Int,
)

/**
 * Один сэмпл пульса от датчика.
 *
 * @param bpm         пульс, ударов в минуту
 * @param timestampMs время получения сэмпла (epoch millis) — для проверки свежести:
 *   после обрыва соединения последний сэмпл «протухает» и не должен
 *   записываться в GPS-точки
 */
data class HrmSample(
    val bpm: Int,
    val timestampMs: Long,
)

/**
 * Состояние соединения с пульсометром.
 *
 * RECONNECTING — датчик был подключён и неожиданно отвалился; менеджер
 * бесконечно ретраит подключение (тренировка должна быть максимально
 * покрыта данными пульса). BLUETOOTH_OFF — адаптер выключен, ретраи
 * приостановлены до включения.
 */
enum class HrmConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    BLUETOOTH_OFF,
}
