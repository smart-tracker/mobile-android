package com.example.smarttracker.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Инструментальные тесты GpsPointDao (выполняются на устройстве/эмуляторе).
 *
 * Покрывает:
 * - assignBatchId идемпотентен: повторный вызов не перезаписывает batchId (AND batchId IS NULL)
 * - getLastPoint(excludedTrainingId=null) возвращает самую последнюю точку без фильтра
 *
 * Использует Room in-memory базу — каждый тест начинает с чистого состояния.
 */
@RunWith(AndroidJUnit4::class)
class GpsPointDaoTest {

    private lateinit var database: SmartTrackerDatabase
    private lateinit var dao: GpsPointDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SmartTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.gpsPointDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── Тест 5: assignBatchId идемпотентен ────────────────────────────────────

    @Test
    fun assignBatchId_не_перезаписывает_уже_назначенный_batchId() = runTest {
        // Вставляем точку без batchId
        val inserted = dao.insertAndReturnId(makePoint(trainingId = "t1"))

        // Первый вызов — назначает "batch-A"
        dao.assignBatchId(listOf(inserted), batchId = "batch-A")

        // Второй вызов — попытка перезаписать "batch-A" на "batch-B"
        dao.assignBatchId(listOf(inserted), batchId = "batch-B")

        // batchId должен остаться "batch-A" (AND batchId IS NULL не выполняется)
        val points = dao.getPointsForTraining("t1")
        assertEquals("batch-A", points.first().batchId)
    }

    // ── Тест 12: getLastPoint(null) не применяет фильтр ──────────────────────

    @Test
    fun getLastPoint_с_null_возвращает_последнюю_точку_без_фильтра() = runTest {
        dao.insert(makePoint(trainingId = "t1", timestampUtc = 1000L))
        dao.insert(makePoint(trainingId = "t2", timestampUtc = 2000L))  // самая новая

        val last = dao.getLastPoint(excludedTrainingId = null)

        assertNotNull(last)
        assertEquals("t2", last!!.trainingId)
    }

    @Test
    fun getLastPoint_исключает_активную_тренировку() = runTest {
        dao.insert(makePoint(trainingId = "t1", timestampUtc = 1000L))
        dao.insert(makePoint(trainingId = "active", timestampUtc = 9999L))

        // Исключаем активную тренировку — должна вернуться точка из "t1"
        val last = dao.getLastPoint(excludedTrainingId = "active")

        assertNotNull(last)
        assertEquals("t1", last!!.trainingId)
    }

    @Test
    fun getLastPoint_возвращает_null_если_все_тренировки_исключены() = runTest {
        dao.insert(makePoint(trainingId = "only", timestampUtc = 1000L))

        val last = dao.getLastPoint(excludedTrainingId = "only")

        assertNull(last)
    }

    // ── Хелпер ───────────────────────────────────────────────────────────────

    private fun makePoint(
        trainingId:   String = "test",
        timestampUtc: Long   = System.currentTimeMillis(),
    ) = GpsPointEntity(
        id           = 0,
        trainingId   = trainingId,
        timestampUtc = timestampUtc,
        elapsedNanos = timestampUtc * 1_000_000L,
        latitude     = 55.7558,
        longitude    = 37.6173,
        altitude     = null,
        speed        = null,
        accuracy     = null,
        bearing      = null,
        externalId   = null,
        batchId      = null,
        isSent       = false,
    )

    /**
     * Вставляет точку и возвращает сгенерированный id.
     * Room не возвращает id из suspend insert(@Insert), поэтому
     * используем getPointsForTraining и берём id последней вставленной точки.
     */
    private suspend fun GpsPointDao.insertAndReturnId(point: GpsPointEntity): Long {
        insert(point)
        return getPointsForTraining(point.trainingId).last().id
    }
}
