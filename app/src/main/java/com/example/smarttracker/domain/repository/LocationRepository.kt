package com.example.smarttracker.domain.repository

import com.example.smarttracker.domain.model.LocationPoint
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий GPS-точек тренировки.
 *
 * Является единственным источником правды для данных трекинга.
 * Намеренно не содержит импортов android.* — domain-слой остаётся чистым Kotlin.
 *
 * Реализация живёт в data-слое (LocationRepositoryImpl), который работает через Room.
 */
interface LocationRepository {
    /** Сохранить одну GPS-точку. */
    suspend fun savePoint(point: LocationPoint)

    /** Batch-вставка нескольких GPS-точек за одну транзакцию (буфер из Service). */
    suspend fun savePoints(points: List<LocationPoint>)

    /** Получить все точки тренировки (единовременно). */
    suspend fun getPointsForTraining(trainingId: String): List<LocationPoint>

    /** Получить точки тренировки, ещё не отправленные на сервер. */
    suspend fun getUnsentPoints(trainingId: String): List<LocationPoint>

    /**
     * Назначить batchId группе точек перед отправкой на сервер.
     * Группировка позволяет атомарно помечать весь батч как отправленный.
     *
     * @param pointIds список Room-идентификаторов точек
     * @param batchId UUID батча
     */
    suspend fun assignBatchId(pointIds: List<Long>, batchId: String)

    /**
     * Пометить все точки блока как отправленные.
     * Вызывается после успешной загрузки батча на сервер.
     *
     * @param batchId UUID блока, установленный через [assignBatchId]
     */
    suspend fun markBatchAsSent(batchId: String)

    /** Наблюдать за точками тренировки в реальном времени (для обновления UI). */
    fun observePointsForTraining(trainingId: String): Flow<List<LocationPoint>>

    /**
     * Последняя сохранённая GPS-точка из любой тренировки.
     * Используется для начального центрирования карты до получения GPS-сигнала.
     * Возвращает null если тренировок ещё не было.
     */
    suspend fun getLastKnownPoint(): LocationPoint?

    /**
     * Удаляет все точки тренировки из базы.
     * Используется для очистки discovery-точек: они временные и нужны только
     * пока discovery-сервис работает (для обновления gpsStatus в ViewModel).
     * После остановки сервиса хранить их бессмысленно — syncLoop отключён
     * для discovery, поэтому эти точки никогда не отправятся, но навсегда
     * останутся в Room с isSent=false и могут вызвать случайный 404.
     */
    suspend fun deletePointsForTraining(trainingId: String)
}
