package com.example.smarttracker.domain.repository

import com.example.smarttracker.domain.model.ActiveTrainingResult
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.domain.model.METActivity
import com.example.smarttracker.domain.model.SaveTrainingResult
import com.example.smarttracker.domain.model.WorkoutType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import com.example.smarttracker.domain.model.TrainingHistoryItem

/**
 * Контракт репозитория тренировок.
 *
 * Объединяет справочные данные (типы активностей) и операции
 * жизненного цикла тренировки: старт → GPS-синхронизация → завершение.
 */
interface WorkoutRepository {

    /**
     * Реактивный поток видов активности из локального кэша (Room).
     *
     * Эмитит кэшированный список немедленно при подписке, затем повторно после каждого
     * фонового обновления из сети. Сетевой запрос выполняется конкурентно — первый emit
     * не блокируется ожиданием ответа сервера.
     */
    fun workoutTypesFlow(): Flow<List<WorkoutType>>

    /**
     * Начать тренировку на сервере.
     * Возвращает серверный UUID, который используется для GPS-загрузки и завершения.
     *
     * @param typeActivId идентификатор типа активности
     * @param timeStart фактическое время начала (ISO 8601 UTC). Передаётся для офлайн-тренировок,
     *   чтобы бэкенд записал реальный time_start вместо времени получения запроса.
     *   null — бэкенд устанавливает time_start = now() (онлайн-тренировки).
     */
    suspend fun startTraining(typeActivId: Int, timeStart: String? = null): Result<ActiveTrainingResult>

    /**
     * Завершить тренировку на сервере.
     * Фиксирует время окончания и итоговую статистику.
     *
     * @param trainingId серверный UUID тренировки
     * @param timeEnd время окончания (ISO 8601 UTC)
     * @param totalDistanceMeters общая дистанция в метрах (nullable)
     * @param totalKilocalories общие калории (nullable)
     */
    suspend fun saveTraining(
        trainingId: String,
        timeEnd: String,
        totalDistanceMeters: Double?,
        totalKilocalories: Double?,
    ): Result<SaveTrainingResult>

    /**
     * Загрузить батч GPS-точек на сервер.
     * Максимум 100 точек за запрос (ограничение API).
     *
     * @param trainingId серверный UUID тренировки
     * @param batchId UUID батча для идемпотентности
     * @param points список GPS-точек для отправки
     * @return количество сохранённых на сервере точек
     */
    suspend fun uploadGpsPoints(
        trainingId: String,
        batchId: String,
        points: List<LocationPoint>,
    ): Result<Int>

    /**
     * Получить текущую активную тренировку пользователя с сервера.
     * Возвращает серверный UUID тренировки.
     *
     * Используется для автоматического завершения orphaned-тренировки при получении
     * [com.example.smarttracker.domain.model.ActiveTrainingConflictException] от [startTraining].
     */
    suspend fun getActiveTraining(): Result<String>

    /**
     * Получить MET-конфигурацию для расчёта калорий по виду активности.
     *
     * Результат используется [com.example.smarttracker.domain.usecase.CalorieCalculator]
     * для определения MET-коэффициента на каждой GPS-точке.
     *
     * @param typeActivId идентификатор типа активности
     */
    suspend fun getMETActivity(typeActivId: Int): Result<METActivity>

    /**
     * Получить историю тренировок пользователя (список последних тренировок).
     * Возвращает список элементов истории; пустой список если записей нет.
     */
    suspend fun getTrainingHistory(): Result<List<TrainingHistoryItem>>

    /**
     * Получить GPS-трек конкретной тренировки из истории.
     * Использует GET /training/{training_id}/get_training.
     * Возвращает пустой список если трек недоступен или произошла ошибка сети.
     */
    suspend fun getTrainingDetail(trainingId: String): Result<List<LocationPoint>>

    /**
     * Эмитит [Unit] каждый раз, когда тренировка успешно сохранена на сервере.
     * Используется [TrainingHistoryViewModel] для автоматического обновления истории.
     * Работает как для онлайн-завершения ([saveTraining]), так и для офлайн-случая
     * ([SaveTrainingWorker] → [saveTraining]).
     */
    val trainingCompletedFlow: SharedFlow<Unit>

    /**
     * Сохранить параметры завершения тренировки в локальную очередь.
     *
     * Вызывается когда [saveTraining] не смог достучаться до сервера (нет сети).
     * [com.example.smarttracker.data.work.SaveTrainingWorker] прочитает очередь
     * при появлении сети и доставит запрос — даже если приложение закрыто.
     *
     * Идемпотентно: повторный вызов для одного [trainingId] игнорируется (IGNORE).
     *
     * @param typeActivId non-null только для офлайн-старта: [SyncGpsPointsWorker] сначала
     *   зарегистрирует тренировку, затем загрузит GPS-точки.
     * @param timeStart реальное время начала тренировки (ISO 8601 UTC). Передаётся вместе
     *   с [typeActivId] для офлайн-старта — бэкенд запишет правильный time_start.
     */
    suspend fun savePendingFinish(
        trainingId: String,
        timeEnd: String,
        totalDistanceMeters: Double?,
        totalKilocalories: Double?,
        typeActivId: Int? = null,
        timeStart: String? = null,
    )
}
